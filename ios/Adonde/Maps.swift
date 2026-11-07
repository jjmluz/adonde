import AdondeCore
import GoogleMaps
import SwiftUI

/// How far from the chosen point a panorama can be, in metres.
private let searchRadiusMetres: UInt = 2000
private let googlePanoIdLength = 22
private let maxRevealZoom: Float = 11
private let revealMargin: CGFloat = 60

let worldCentre = CLLocationCoordinate2D(latitude: 25, longitude: 10)

extension Place {
    var coordinate: CLLocationCoordinate2D { CLLocationCoordinate2D(latitude: lat, longitude: lng) }
}

extension CLLocationCoordinate2D {
    var place: Place { Place(lat: latitude, lng: longitude) }
}

/// Full-screen Street View that searches until it lands on a road. The player looks around and zooms by
/// touch, but can never move.
struct PanoramaView: UIViewRepresentable {
    let model: GameModel

    func makeCoordinator() -> Coordinator { Coordinator(model: model) }

    func makeUIView(context: Context) -> GMSPanoramaView {
        let view = GMSPanoramaView(frame: .zero)
        view.navigationGestures = false
        view.navigationLinksHidden = true
        view.streetNamesHidden = true
        view.orientationGestures = true
        view.zoomGestures = true
        view.delegate = context.coordinator
        return view
    }

    func updateUIView(_ view: GMSPanoramaView, context: Context) {
        let coordinator = context.coordinator
        guard let near = model.searchNear, near !== coordinator.searched else { return }
        coordinator.searched = near
        view.moveNearCoordinate(near.coordinate, radius: searchRadiusMetres, source: .outside)
    }

    final class Coordinator: NSObject, GMSPanoramaViewDelegate {
        let model: GameModel
        var searched: Place?

        init(model: GameModel) { self.model = model }

        func panoramaView(_ view: GMSPanoramaView, didMoveTo panorama: GMSPanorama, nearCoordinate coordinate: CLLocationCoordinate2D) {
            // People's own uploads have longer ids, and their credit can name the place outright.
            let official = panorama.panoramaID.count == googlePanoIdLength && !panorama.links.isEmpty ? panorama : nil
            if official != nil {
                view.camera = GMSPanoramaCamera(heading: .random(in: 0..<360), pitch: 0, zoom: 1)
            }
            MainActor.assumeIsolated { model.onPanoramaFound(official?.coordinate.place) }
        }

        func panoramaView(_ view: GMSPanoramaView, error: Error, onMoveNearCoordinate coordinate: CLLocationCoordinate2D) {
            MainActor.assumeIsolated { model.onPanoramaFound(nil) }
        }
    }
}

/// A Google map with its own buttons off, which can be moved and zoomed by touch but never turned.
private func makeMap(gestures: Bool) -> GMSMapView {
    let options = GMSMapViewOptions()
    options.camera = GMSCameraPosition(target: worldCentre, zoom: 1)
    let map = GMSMapView(options: options)
    map.settings.compassButton = false
    map.settings.rotateGestures = false
    map.settings.tiltGestures = false
    map.settings.scrollGestures = gestures
    map.settings.zoomGestures = gestures
    map.settings.indoorPicker = false
    return map
}

/// The map a player guesses on. Moving it is what places the pin, which stays in the middle (it's drawn over the
/// map by `GuessScreen`). It goes back to the whole world for each player.
struct GuessMapView: UIViewRepresentable {
    let model: GameModel

    func makeCoordinator() -> Coordinator { Coordinator(model: model) }

    func makeUIView(context: Context) -> GMSMapView {
        let map = makeMap(gestures: true)
        model.pinPosition = { [weak map] in map?.camera.target.place }
        return map
    }

    func updateUIView(_ map: GMSMapView, context: Context) {
        let coordinator = context.coordinator
        guard coordinator.turn != model.turn else { return }
        coordinator.turn = model.turn
        map.moveCamera(GMSCameraUpdate.setCamera(GMSCameraPosition(target: worldCentre, zoom: map.minZoom)))
    }

    static func dismantleUIView(_ map: GMSMapView, coordinator: Coordinator) {
        MainActor.assumeIsolated { coordinator.model.pinPosition = nil }
    }

    final class Coordinator {
        let model: GameModel
        var turn = -1

        init(model: GameModel) { self.model = model }
    }
}

/// The answer: the real place, everyone's pin, and a line between them. `padding` is what the scoreboard covers,
/// so the pins fit in the rest. It fits them again when the map changes size.
struct RevealMapView: UIViewRepresentable {
    let place: Place
    let scores: [Score]
    let players: [Player]
    let padding: UIEdgeInsets

    func makeUIView(context: Context) -> RevealMapContainer {
        RevealMapContainer(map: makeMap(gestures: false))
    }

    func updateUIView(_ container: RevealMapContainer, context: Context) {
        if container.place !== place {
            container.show(place: place, scores: scores, players: players)
        }
        if container.map.padding != padding {
            container.map.padding = padding
            container.fit()
        }
    }
}

/// Holds the reveal map, to fit the pins again whenever its size changes, such as when the phone is turned.
final class RevealMapContainer: UIView {
    let map: GMSMapView
    private(set) var place: Place?
    private var points: [CLLocationCoordinate2D] = []
    private var fittedSize = CGSize.zero

    init(map: GMSMapView) {
        self.map = map
        super.init(frame: .zero)
        addSubview(map)
    }

    required init?(coder: NSCoder) { fatalError("Not used from a storyboard") }

    override func layoutSubviews() {
        super.layoutSubviews()
        map.frame = bounds
        if bounds.size != fittedSize { fit() }
    }

    func show(place: Place, scores: [Score], players: [Player]) {
        self.place = place
        map.clear()
        let target = place.coordinate
        points = [target]
        for score in scores {
            guard let guess = score.guess?.coordinate else { continue }
            points.append(guess)
            let path = GMSMutablePath()
            path.add(guess)
            path.add(target)
            let line = GMSPolyline(path: path)
            line.geodesic = true
            line.strokeWidth = 2.5
            line.strokeColor = playerUIColor(Int(score.player))
            line.map = map
            let marker = GMSMarker(position: guess)
            marker.icon = pinImage(color: playerUIColor(Int(score.player)), label: avatars[Int(players[Int(score.player)].avatar)])
            marker.map = map
        }
        let goal = GMSMarker(position: target)
        goal.icon = pinImage(color: UIColor(Color.good), label: "⭐")
        goal.zIndex = 1
        goal.map = map
        fit()
    }

    func fit() {
        guard bounds.width > 0, bounds.height > 0, let target = points.first else { return }
        fittedSize = bounds.size
        var region = GMSCoordinateBounds()
        for point in points { region = region.includingCoordinate(point) }
        map.moveCamera(GMSCameraUpdate.fit(region, withPadding: revealMargin))
        if map.camera.zoom > maxRevealZoom { map.moveCamera(GMSCameraUpdate.zoom(to: maxRevealZoom)) }
        // Far-apart pins may not all fit. The answer always has to show; the others are still on the board, with
        // their lines leading off.
        let visible = GMSCoordinateBounds(region: map.projection.visibleRegion())
        if !points.allSatisfy(visible.contains) { map.moveCamera(GMSCameraUpdate.setTarget(target)) }
    }
}
