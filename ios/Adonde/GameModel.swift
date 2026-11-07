import AdondeCore
import Foundation
import Observation

private let searchTimeout = Duration.seconds(10)
private let searchAttempts = 15
private let defaultRounds = 5
private let defaultPlayers = 2

let avatars = ["🦊", "🐼", "🐸", "🦁", "🐧", "🐙", "🦉", "🐢", "🦄", "🐝", "🐬", "🐨"]

enum Screen { case splash, setup, explore, guess, reveal, final }

/**
 The game's state and flow, the iOS twin of the Android `GameViewModel`. The rules themselves are in `core`
 (the AdondeCore framework), shared with Android; this only keeps the screens and the saved setup.
 */
@MainActor @Observable
final class GameModel {
    @ObservationIgnored private let defaults = UserDefaults.standard
    @ObservationIgnored private let picker = PlacesKt.placePicker(csv: asset("places"))
    @ObservationIgnored let globe = GlobeDotsKt.parseGlobe(csv: asset("globe"))
    @ObservationIgnored private var game: Game?
    @ObservationIgnored private var timeout: Task<Void, Never>?
    @ObservationIgnored private var searchesLeft = 0
    /// Reads the point under the pin. Set by the map while a player is guessing.
    @ObservationIgnored var pinPosition: (() -> Place?)?

    private(set) var screen = Screen.splash
    private(set) var players: [Player]
    private(set) var playerCount: Int
    private(set) var rounds: Int
    /// The player whose name is being typed, on the title screen.
    private(set) var editingName: Int?
    private(set) var paused = false

    private(set) var round = 1
    private(set) var isLastRound = false
    /// Where to search for this round's panorama next.
    private(set) var searchNear: Place?
    /// The place of this round, once a panorama has been found.
    private(set) var spot: Place?
    private(set) var searchFailed = false
    /// The guesser's position in `players`, or nil between guesses.
    private(set) var guesserIndex: Int?
    /// Goes up each time a new player takes the map, which sends it back to the whole world.
    private(set) var turn = 0
    private(set) var roundScores: [Score] = []
    private(set) var standings: [Score] = []

    var guesser: Player? { guesserIndex.map { players[$0] } }

    init() {
        let defaults = UserDefaults.standard
        let maxPlayers = Int(GameKt.MAX_PLAYERS)
        // An empty name is the default, "Player N" in whatever language the device is in. The keys are the
        // Android app's.
        players = (0..<maxPlayers).map { index in
            let name = GameKt.savedName(saved: defaults.string(forKey: "name\(index)"), index: Int32(index))
            let avatar = defaults.object(forKey: "avatar\(index)") as? Int ?? index
            return Player(name: name, avatar: Int32(avatar))
        }
        playerCount = defaults.object(forKey: "players") as? Int ?? defaultPlayers
        rounds = defaults.object(forKey: "rounds") as? Int ?? defaultRounds
        makeAvatarsUnique()
    }

    func leaveSplash() {
        if screen == .splash { screen = .setup }
    }

    func changeRounds(_ step: Int) {
        rounds = (rounds + step).clamped(Int(GameKt.MIN_ROUNDS), Int(GameKt.MAX_ROUNDS))
    }

    func changePlayerCount(_ step: Int) {
        playerCount = (playerCount + step).clamped(Int(GameKt.MIN_PLAYERS), Int(GameKt.MAX_PLAYERS))
        makeAvatarsUnique()
    }

    /// Moves to the next avatar that nobody else in this game has.
    func changeAvatar(_ index: Int, step: Int = 1) {
        let taken = Set(players.prefix(playerCount).map { Int($0.avatar) })
        var avatar = Int(players[index].avatar)
        repeat {
            avatar = (avatar + step + avatars.count) % avatars.count
        } while taken.contains(avatar)
        players[index] = players[index].doCopy(name: players[index].name, avatar: Int32(avatar))
    }

    func editName(_ index: Int) {
        editingName = index
    }

    func onNameTyped(_ name: String) {
        guard let index = editingName else { return }
        let trimmed = name.trimmingCharacters(in: .whitespaces)
        if !trimmed.isEmpty { players[index] = players[index].doCopy(name: trimmed, avatar: players[index].avatar) }
        editingName = nil
    }

    func startGame() {
        saveSetup()
        game = Game(players: Array(players.prefix(playerCount)), rounds: Int32(rounds))
        startExploring()
    }

    /// The panorama answered a search: with where it landed, or nil if nothing was there.
    func onPanoramaFound(_ position: Place?) {
        guard spot == nil, screen == .explore else { return }
        timeout?.cancel()
        if let position {
            spot = position
            game?.startRound(place: position)
        } else {
            search()
        }
    }

    func retrySearch() {
        if screen == .explore && searchFailed { startSearch() }
    }

    /// Everyone has seen enough: on to the first player's guess.
    func startGuessing() {
        guard screen == .explore, spot != nil else { return }
        showGuesser()
        screen = .guess
    }

    /// Takes the point under the pin as the current player's guess.
    func placeGuess() {
        guard let game, screen == .guess, let position = pinPosition?() else { return }
        game.guess(guess: position)
        if game.guesser == nil {
            roundScores = game.roundScores
            standings = game.standings
            screen = .reveal
        } else {
            showGuesser()
        }
    }

    /// From the reveal, on to the next round or the final results.
    func next() {
        guard let game, screen == .reveal else { return }
        if game.isLastRound {
            screen = .final
        } else {
            game.nextRound()
            startExploring()
        }
    }

    func pause() { paused = true }

    func resume() { paused = false }

    /// Leaves the game, or the final results, for the title screen.
    func backToStart() {
        paused = false
        screen = .setup
    }

    private func startExploring() {
        guard let game else { return }
        round = Int(game.round)
        isLastRound = game.isLastRound
        spot = nil
        startSearch()
        screen = .explore
    }

    private func startSearch() {
        searchFailed = false
        searchesLeft = searchAttempts
        search()
    }

    private func search() {
        guard searchesLeft > 0 else {
            searchFailed = true
            return
        }
        searchesLeft -= 1
        searchNear = picker.next()
        // The panorama may stay silent if it can't reach Google, so the search gives up after a while.
        timeout?.cancel()
        timeout = Task { [weak self] in
            try? await Task.sleep(for: searchTimeout)
            guard let self, !Task.isCancelled else { return }
            if self.screen == .explore && self.spot == nil { self.searchFailed = true }
        }
    }

    private func showGuesser() {
        guesserIndex = game?.guesser.map { Int($0.int32Value) }
        turn += 1
    }

    /// A player who comes back when the count goes up may have kept an avatar someone else has taken since.
    private func makeAvatarsUnique() {
        let fixed = GameKt.withUniqueAvatars(players: players, count: Int32(playerCount), avatars: Int32(avatars.count))
        if fixed != players { players = fixed }
    }

    private func saveSetup() {
        defaults.set(rounds, forKey: "rounds")
        defaults.set(playerCount, forKey: "players")
        for (index, player) in players.enumerated() {
            defaults.set(player.name, forKey: "name\(index)")
            defaults.set(Int(player.avatar), forKey: "avatar\(index)")
        }
    }
}

/// The whole text of one of the shared CSV files, which the app bundles from shared/src/main/assets.
private func asset(_ name: String) -> String {
    guard let url = Bundle.main.url(forResource: name, withExtension: "csv"),
          let text = try? String(contentsOf: url, encoding: .utf8) else { return "" }
    return text
}

private extension Int {
    func clamped(_ low: Int, _ high: Int) -> Int { Swift.min(Swift.max(self, low), high) }
}
