import AdondeCore
import SwiftUI

private let splashSeconds = 2.5
/// The logo is about this many times as wide as its font size.
private let logoWidthPerSize: CGFloat = 4.4
/// Below this height, the setup and the scoreboard go side by side rather than one above the other.
private let shortScreen: CGFloat = 560
private let maxColumnWidth: CGFloat = 560
/// Google's logo and notices sit along the bottom of the maps, and must stay visible under the buttons.
private let googleNotices: CGFloat = 44
private let nameLength = 14

/// Every screen of the game, the iOS twin of the Android phone app's `MobileApp`.
struct GameView: View {
    let model: GameModel

    var body: some View {
        ZStack {
            Color.background.ignoresSafeArea()
            switch model.screen {
            case .splash: SplashScreen(model: model)
            case .setup: SetupScreen(model: model)
            case .explore: ExploreScreen(model: model)
            case .guess: GuessScreen(model: model)
            case .reveal: RevealScreen(model: model)
            case .final: FinalScreen(model: model)
            case .records: RecordsScreen(model: model)
            }
            if model.paused { PausedOverlay(model: model) }
        }
    }
}

private struct SplashScreen: View {
    let model: GameModel

    var body: some View {
        GeometryReader { geometry in
            let globe = min(geometry.size.width, geometry.size.height) * 0.8
            let logo = min(geometry.size.width * 0.85, globe * 1.15) / logoWidthPerSize
            ZStack {
                GlobeView(dots: model.globe).frame(width: globe, height: globe)
                Logo(size: logo).shadow(color: .background, radius: 18, y: 4)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .ignoresSafeArea()
        .contentShape(Rectangle())
        .onTapGesture { model.leaveSplash() }
        .task {
            try? await Task.sleep(for: .seconds(splashSeconds))
            model.leaveSplash()
        }
    }
}

private struct SetupScreen: View {
    let model: GameModel
    @State private var typedName = ""

    var body: some View {
        GeometryReader { geometry in
            if geometry.size.width > geometry.size.height && geometry.size.height < shortScreen {
                // A phone on its side: the game's settings on the left, the players on the right.
                HStack(spacing: 24) {
                    VStack(spacing: 10) {
                        Logo(size: 40).padding(.bottom, 8)
                        settings
                        start.padding(.top, 4)
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    ScrollView {
                        VStack(spacing: 8) { playerList }
                            .frame(minHeight: geometry.size.height - 32)
                    }
                    .frame(maxWidth: .infinity)
                }
                .padding(16)
            } else {
                VStack(spacing: 12) {
                    ScrollView {
                        // From the top, so adding a player doesn't move the rows under the finger.
                        VStack(spacing: 8) {
                            Logo(size: 52).padding(.top, 48).padding(.bottom, 24)
                            settings
                            playerList.padding(.top, 4)
                        }
                    }
                    start
                }
                .frame(maxWidth: maxColumnWidth)
                .padding(16)
                .frame(maxWidth: .infinity)
            }
        }
        .alert(tr("player_name"), isPresented: editing) {
            TextField(editingPlaceholder, text: $typedName)
                .textInputAutocapitalization(.words)
                .onChange(of: typedName) { _, name in
                    if name.count > nameLength { typedName = String(name.prefix(nameLength)) }
                }
            Button("OK") { model.onNameTyped(typedName) }
        }
    }

    private var editing: Binding<Bool> {
        Binding(get: { model.editingName != nil }, set: { if !$0 && model.editingName != nil { model.onNameTyped("") } })
    }

    private var editingPlaceholder: String {
        model.editingName.map { nameOf(model.players[$0], $0) } ?? ""
    }

    @ViewBuilder private var settings: some View {
        Stepper(title: tr("rounds"), value: model.rounds, min: Int(GameKt.MIN_ROUNDS), max: Int(GameKt.MAX_ROUNDS), change: model.changeRounds)
        Stepper(title: tr("players"), value: model.playerCount, min: Int(GameKt.MIN_PLAYERS), max: Int(GameKt.MAX_PLAYERS), change: model.changePlayerCount)
    }

    @ViewBuilder private var playerList: some View {
        ForEach(0..<model.playerCount, id: \.self) { index in
            PlayerRow(index: index, player: model.players[index], model: model) {
                typedName = ""
                model.editName(index)
            }
        }
    }

    private var start: some View {
        HStack(spacing: 8) {
            ActionButton(text: tr("start"), action: model.startGame).frame(maxWidth: .infinity)
            Button(action: model.showRecords) {
                Image(systemName: "trophy")
                    .font(.system(size: 22, weight: .semibold))
                    .foregroundStyle(Color.accent)
                    .frame(width: 56, height: 56)
                    .background(Color.card, in: Circle())
            }
            .buttonStyle(PressStyle())
            .accessibilityLabel(tr("records"))
        }
    }
}

private struct Stepper: View {
    let title: String
    let value: Int
    let min: Int
    let max: Int
    let change: (Int) -> Void

    var body: some View {
        SetupRow {
            LabelText(title, 18, .foreground)
            Spacer()
            RoundButton(symbol: "−", enabled: value > min) { change(-1) }
            LabelText("\(value)", 22, .foreground, .bold).frame(width: 48)
            RoundButton(symbol: "+", enabled: value < max) { change(1) }
        }
    }
}

private struct PlayerRow: View {
    let index: Int
    let player: Player
    let model: GameModel
    let rename: () -> Void

    var body: some View {
        SetupRow {
            // The avatar has its own tap: the next animal nobody else has.
            Button { model.changeAvatar(index) } label: {
                AvatarView(avatar: Int(player.avatar), colorIndex: index, size: 40)
            }
            .buttonStyle(.plain)
            NameText(nameOf(player, index), size: 18).padding(.leading, 12)
            Spacer(minLength: 8)
            Image(systemName: "pencil").foregroundStyle(Color.muted).font(.system(size: 18, weight: .semibold))
        }
        .contentShape(Rectangle())
        .onTapGesture(perform: rename)
        .accessibilityAction(named: tr("player_name"), rename)
    }
}

private struct SetupRow<Content: View>: View {
    @ViewBuilder let content: Content

    var body: some View {
        HStack(spacing: 0) { content }
            .padding(.horizontal, 12)
            .frame(height: 56)
            .frame(maxWidth: .infinity)
            .background(Color.card, in: RoundedRectangle(cornerRadius: 12))
    }
}

private struct ExploreScreen: View {
    let model: GameModel

    var body: some View {
        ZStack {
            PanoramaView(model: model)
            if model.spot == nil {
                Message {
                    if model.searchFailed {
                        LabelText(tr("no_place"), 26, .foreground, .bold)
                        LabelText(tr("check_internet"), 18, .muted)
                        ActionButton(text: tr("retry"), action: model.retrySearch).padding(.top, 8)
                    } else {
                        GlobeView(dots: model.globe).frame(width: 220, height: 220).padding(.bottom, 16)
                        LabelText(tr("searching"), 24, .foreground, .bold)
                    }
                }
                // Covers the panorama, and keeps touches from reaching it.
                .background(Color.background.ignoresSafeArea())
                .contentShape(Rectangle())
            } else {
                PlayingOverlay(model: model, action: tr("guess"), onAction: model.startGuessing) {
                    Chip { LabelText(tr("round_of", model.round, model.rounds), 15, .foreground, .bold) }
                }
            }
        }
    }
}

private struct GuessScreen: View {
    let model: GameModel

    var body: some View {
        if let index = model.guesserIndex, let player = model.guesser {
            ZStack {
                GuessMapView(model: model)
                // The pin's tip, not its middle, marks the centre of the map.
                let pin = pinImage(color: playerUIColor(index), label: avatars[Int(player.avatar)])
                Image(uiImage: pin)
                    .offset(y: -pin.size.height / 2)
                    .allowsHitTesting(false)
                PlayingOverlay(model: model, action: tr("confirm"), onAction: model.placeGuess) {
                    VStack(spacing: 4) {
                        Chip {
                            AvatarView(avatar: Int(player.avatar), colorIndex: index, size: 30).padding(.trailing, 8)
                            LabelText(tr("turn", nameOf(player, index)), 17, .foreground, .bold)
                        }
                        LabelText(tr("round_of", model.round, model.rounds), 13, .foreground, .bold)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 3)
                            .background(Color.scrim, in: RoundedRectangle(cornerRadius: 12))
                    }
                }
            }
        }
    }
}

/// What goes over the panorama and the guess map: the pause button, `top` in the middle at the top, and at the
/// bottom the button that moves the game on. The bottom edge is left to Google's notices.
private struct PlayingOverlay<Top: View>: View {
    let model: GameModel
    let action: String
    let onAction: () -> Void
    @ViewBuilder let top: Top

    var body: some View {
        ZStack {
            VStack {
                HStack(alignment: .top) {
                    Button(action: model.pause) {
                        Image(systemName: "pause.fill")
                            .font(.system(size: 18, weight: .bold))
                            .foregroundStyle(Color.foreground)
                            .frame(width: 48, height: 48)
                            .background(Color.scrim, in: Circle())
                    }
                    .accessibilityLabel(tr("pause"))
                    Spacer()
                }
                Spacer()
            }
            VStack {
                top.padding(.horizontal, 56)
                Spacer()
                ActionButton(text: action, action: onAction)
                    .frame(minWidth: 200)
                    .fixedSize()
                    .padding(.bottom, googleNotices - 12)
            }
        }
        .padding(12)
    }
}

private struct RevealScreen: View {
    let model: GameModel
    @State private var board = CGSize.zero

    var body: some View {
        if let place = model.spot {
            GeometryReader { geometry in
                // On a phone on its side the board goes down the right; otherwise along the bottom.
                let side = geometry.size.width > geometry.size.height && geometry.size.height < shortScreen
                let columns = side || geometry.size.width < 600 ? 1 : geometry.size.width < 900 ? 2 : 3
                ZStack(alignment: side ? .trailing : .bottom) {
                    RevealMapView(
                        place: place,
                        scores: model.roundScores,
                        players: model.players,
                        padding: UIEdgeInsets(top: 0, left: 0, bottom: side ? 0 : board.height, right: side ? board.width : 0)
                    )
                    scoreboard(columns: columns)
                        .frame(width: side ? min(340, geometry.size.width * 0.45) : nil)
                        .frame(maxWidth: side ? nil : .infinity, maxHeight: side ? .infinity : nil)
                        .background(Color.background.ignoresSafeArea(edges: side ? [.trailing, .vertical] : [.bottom, .horizontal]))
                        .onGeometryChange(for: CGSize.self, of: \.size) { board = $0 }
                        // Only a cap: a frame with a maximum grows to it, so it goes outside the board's background.
                        .frame(maxHeight: side ? .infinity : geometry.size.height * 0.6, alignment: .bottom)
                }
            }
        }
    }

    private func scoreboard(columns: Int) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            LabelText(tr("round_of", model.round, model.rounds), 18, .foreground, .bold)
            // The board is as tall as its rows, so the map gets the rest; it only scrolls when they don't fit.
            ViewThatFits(in: .vertical) {
                scores(columns: columns)
                ScrollView { scores(columns: columns) }
            }
            ActionButton(text: tr(model.isLastRound ? "final_result" : "next_round"), action: model.next)
                .frame(maxWidth: .infinity)
        }
        .padding(16)
    }

    private func scores(columns: Int) -> some View {
        LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 8), count: columns), spacing: 8) {
            ForEach(model.roundScores, id: \.player) { score in
                let record = Int(score.player) == model.roundRecord
                ScoreRow(
                    score: score,
                    player: model.players[Int(score.player)],
                    main: km(score.roundKm),
                    detail: tr("total", km(score.totalKm)),
                    highlight: record
                )
                // Hangs over the card's top edge, into the gap above it.
                .overlay(alignment: .topTrailing) { if record { NewRecordTag().offset(x: -10, y: -11) } }
            }
        }
        // Room for the record tag, which hangs above the first card and would be clipped when the board scrolls.
        .padding(.top, model.roundRecord != nil ? 11 : 0)
    }
}

private struct FinalScreen: View {
    let model: GameModel

    var body: some View {
        let standings = model.standings
        ScrollView {
            VStack(spacing: 10) {
                LabelText(tr("final_result"), 20, .muted, .bold)
                if standings.count > 1, let winner = standings.first.map({ Int($0.player) }) {
                    LabelText(tr("wins", nameOf(model.players[winner], winner)), 30, .accent, .bold)
                }
                VStack(spacing: 8) {
                    ForEach(Array(standings.enumerated()), id: \.offset) { position, score in
                        ScoreRow(
                            score: score,
                            player: model.players[Int(score.player)],
                            main: km(score.totalKm),
                            detail: tr("places_\(position)")
                        )
                    }
                    if !model.brokenRecords.isEmpty {
                        NewRecordsCard(records: model.brokenRecords).padding(.top, 4)
                    }
                    ActionButton(text: tr("back_to_start"), action: model.backToStart)
                        .frame(maxWidth: .infinity)
                        .padding(.top, 8)
                }
                .frame(maxWidth: 480)
            }
            .padding(24)
            .frame(maxWidth: .infinity)
        }
        .defaultScrollAnchor(.center)
        .scrollBounceBehavior(.basedOnSize)
    }
}

private struct RecordsScreen: View {
    let model: GameModel

    var body: some View {
        GeometryReader { geometry in
            // Side by side when the three lists fit across, as on an iPad on its side; otherwise one under another.
            let across = geometry.size.width >= 900
            ScrollView {
                VStack(spacing: 12) {
                    HStack(spacing: 10) {
                        Image(systemName: "trophy").font(.system(size: 24, weight: .semibold)).foregroundStyle(Color.accent)
                        LabelText(tr("records"), 26, .foreground, .bold)
                    }
                    if across {
                        HStack(alignment: .top, spacing: 12) { lists }
                    } else {
                        VStack(spacing: 12) { lists }.frame(maxWidth: 480)
                    }
                    ActionButton(text: tr("back"), primary: false, action: model.backToStart).frame(maxWidth: 480)
                }
                .padding(24)
                .frame(maxWidth: .infinity, minHeight: geometry.size.height)
            }
            .scrollBounceBehavior(.basedOnSize)
        }
    }

    @ViewBuilder private var lists: some View {
        ClosestList(records: model.records).layoutPriority(1)
        TallyList(title: tr("most_wins"), tallies: model.records.byWins) { $0.wins }
        TallyList(title: tr("close_rounds"), tallies: model.records.byCloseRounds) { $0.closeRounds }
    }
}

private struct PausedOverlay: View {
    let model: GameModel

    var body: some View {
        VStack(spacing: 16) {
            HStack(spacing: 96 * 0.2) {
                ForEach(0..<2, id: \.self) { _ in
                    RoundedRectangle(cornerRadius: 96 * 0.3 * 0.25).fill(Color.foreground).frame(width: 96 * 0.3, height: 96)
                }
            }
            .padding(.bottom, 16)
            ActionButton(text: tr("resume"), action: model.resume).frame(width: 240)
            ActionButton(text: tr("quit"), primary: false, action: model.backToStart).frame(width: 240)
        }
        .padding(24)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        // Covers the map, and keeps touches from reaching it.
        .background(Color.scrim.ignoresSafeArea())
        .contentShape(Rectangle())
    }
}

/// A full-screen column of centred lines.
private struct Message<Content: View>: View {
    @ViewBuilder let content: Content

    var body: some View {
        VStack(spacing: 12) { content }
            .padding(.horizontal, 48)
            .padding(.vertical, 27)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

/// The big pill that moves the game on: yellow, or a card's colour when it isn't the main thing to do.
private struct ActionButton: View {
    let text: String
    var primary = true
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            LabelText(text, 18, primary ? .background : .foreground, .bold)
                .padding(.horizontal, 28)
                .padding(.vertical, 12)
                .frame(maxWidth: .infinity, minHeight: 56)
                .background(primary ? Color.accent : Color.card, in: Capsule())
                .contentShape(Capsule())
        }
        .buttonStyle(PressStyle())
    }
}

/// Shrinks a button a little while it's held.
private struct PressStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.96 : 1)
            .animation(.easeOut(duration: 0.1), value: configuration.isPressed)
    }
}

/// A round − or + on the setup screen, dimmed at its limit.
private struct RoundButton: View {
    let symbol: String
    let enabled: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            LabelText(symbol, 24, enabled ? .accent : .dim, .bold)
                .frame(width: 40, height: 40)
                .background(enabled ? Color.background : Color.clear, in: Circle())
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
    }
}

/// A small rounded label over the map.
private struct Chip<Content: View>: View {
    @ViewBuilder let content: Content

    var body: some View {
        HStack(spacing: 0) { content }
            .padding(.horizontal, 14)
            .padding(.vertical, 8)
            .background(Color.scrim, in: RoundedRectangle(cornerRadius: 20))
    }
}
