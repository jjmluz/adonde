import AdondeCore
import SwiftUI

private let startLongitude: Double = -12
private let degreesPerSecond: Double = 22
private let dotSize: Double = 0.011
private let rim: Double = 0.985
private let tilt = 22 * Double.pi / 180
private let ocean = Color(hex: 0x15253F)
private let land = Color(hex: 0x6F8FC0)

/// The Earth turning west to east, with the far side hidden and the near side brightest, as in shared/Globe.kt.
struct GlobeView: View {
    let dots: [GlobeDot]
    @State private var start = Date()

    var body: some View {
        TimelineView(.animation) { timeline in
            let seconds = timeline.date.timeIntervalSince(start)
            Canvas { context, size in
                let radius = min(size.width, size.height) / 2 * rim
                let centre = CGPoint(x: size.width / 2, y: size.height / 2)
                let facing = (startLongitude - seconds * degreesPerSecond) * .pi / 180
                let sinTilt = sin(tilt)
                let cosTilt = cos(tilt)
                for dot in dots {
                    let across = Double(dot.longitude) - facing
                    let sinLatitude = Double(dot.sinLatitude)
                    let cosLatitude = Double(dot.cosLatitude)
                    let front = cosLatitude * cos(across)
                    let depth = sinLatitude * sinTilt + front * cosTilt
                    guard depth > 0 else { continue }
                    let x = cosLatitude * sin(across)
                    let y = sinLatitude * cosTilt - front * sinTilt
                    let dotRadius = radius * dotSize * (0.5 + 0.5 * depth)
                    let rect = CGRect(
                        x: centre.x + x * radius - dotRadius,
                        y: centre.y - y * radius - dotRadius,
                        width: 2 * dotRadius,
                        height: 2 * dotRadius
                    )
                    context.fill(Path(ellipseIn: rect), with: .color(land.opacity(0.35 + 0.65 * depth)))
                }
            }
        }
        .background(ocean)
        .clipShape(Circle())
    }
}
