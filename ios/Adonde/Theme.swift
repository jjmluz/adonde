import AdondeCore
import SwiftUI
import UIKit

// The "Noite" palette, as in shared/Theme.kt.
extension Color {
    static let background = Color(hex: 0x0B1220)
    static let foreground = Color(hex: 0xF4F6FB)
    static let muted = Color(hex: 0xA5AFC3)
    static let dim = Color(hex: 0x5B6781)
    static let accent = Color(hex: 0xFFC83D)
    static let card = Color(hex: 0x1C2A44)
    static let scrim = Color(hex: 0x0B1220, opacity: 0xE6 / 255)
    static let good = Color(hex: 0x4ADE80)

    init(hex: UInt32, opacity: Double = 1) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: opacity
        )
    }
}

let playerColors: [UInt32] = [0xE5484D, 0x3E8BFF, 0xF2A516, 0x9D5CE8, 0x12A594, 0xE93D82]

func playerColor(_ index: Int) -> Color { Color(hex: playerColors[index]) }

func playerUIColor(_ index: Int) -> UIColor { UIColor(playerColor(index)) }

// MARK: Text

/// A string from Localizable.xcstrings, which tools/build_ios_strings.py makes from the Android strings.
func tr(_ key: String, _ arguments: CVarArg...) -> String {
    let format = Bundle.main.localizedString(forKey: key, value: nil, table: nil)
    return arguments.isEmpty ? format : String(format: format, arguments: arguments)
}

/// Kilometres with the marks of the game's language, not the device's region, as on Android.
func km(_ value: Double) -> String {
    GameKt.formatKm(km: value, decimalMark: tr("decimal_mark"), groupMark: tr("group_mark"))
}

/// The player's name, or "Player N" in the device's language when they haven't chosen one.
func nameOf(_ player: Player, _ index: Int) -> String {
    player.name.isEmpty ? tr("default_player", index + 1) : player.name
}

// MARK: Logo

/// The share of the pin's height that hangs below the line of letters.
private let pinTail: CGFloat = 0.28

/// Outfit at weight 800. The font file is variable, and its default instance is Thin.
func outfit(_ size: CGFloat) -> Font {
    guard let thin = UIFont(name: "Outfit-Thin", size: size) else { return .system(size: size, weight: .black) }
    let weightAxis = 0x7767_6874 // "wght"
    let variation = UIFontDescriptor.AttributeName(rawValue: kCTFontVariationAttribute as String)
    let descriptor = thin.fontDescriptor.addingAttributes([variation: [weightAxis: 800]])
    return Font(UIFont(descriptor: descriptor, size: size))
}

/// The name with a pin for its O, whose tip hangs below the line of letters.
struct Logo: View {
    let size: CGFloat

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: size * 0.03) {
            Text(verbatim: "AD")
            PinShape()
                .fill(Color.accent, style: FillStyle(eoFill: true))
                .frame(width: size * 0.74, height: size)
                .alignmentGuide(.firstTextBaseline) { $0.height * (1 - pinTail) }
            Text(verbatim: "NDE?")
        }
        .font(outfit(size))
        .foregroundStyle(Color.foreground)
        .fixedSize()
    }
}

/// The logo's pin, drawn on an 80 × 108 grid with a hole in its head.
struct PinShape: Shape {
    func path(in rect: CGRect) -> Path {
        let unit = rect.width / 80
        func point(_ x: CGFloat, _ y: CGFloat) -> CGPoint { CGPoint(x: rect.minX + x * unit, y: rect.minY + y * unit) }
        var path = Path()
        path.move(to: point(40, 0))
        path.addCurve(to: point(80, 40), control1: point(62, 0), control2: point(80, 18))
        path.addCurve(to: point(40, 108), control1: point(80, 66), control2: point(52, 88))
        path.addCurve(to: point(0, 40), control1: point(28, 88), control2: point(0, 66))
        path.addCurve(to: point(40, 0), control1: point(0, 18), control2: point(18, 0))
        path.closeSubpath()
        path.addEllipse(in: CGRect(origin: point(23, 23), size: CGSize(width: 34 * unit, height: 34 * unit)))
        return path
    }
}

// MARK: Players and scores

struct AvatarView: View {
    let avatar: Int
    let colorIndex: Int
    let size: CGFloat

    var body: some View {
        Text(verbatim: avatars[avatar])
            .font(.system(size: size * 0.55))
            .frame(width: size, height: size)
            .background(playerColor(colorIndex), in: Circle())
    }
}

/// One player on a scoreboard: their avatar and name, a `detail` under the name, and `main` at the end.
struct ScoreRow: View {
    let score: Score
    let player: Player
    let main: String
    let detail: String

    var body: some View {
        HStack(spacing: 10) {
            AvatarView(avatar: Int(player.avatar), colorIndex: Int(score.player), size: 36)
            VStack(alignment: .leading, spacing: 0) {
                NameText(nameOf(player, Int(score.player)))
                LabelText(detail, 13, .muted)
            }
            Spacer(minLength: 0)
            LabelText(main, 18, .foreground, .bold)
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 5)
        .background(Color.card, in: RoundedRectangle(cornerRadius: 10))
    }
}

struct NameText: View {
    let name: String
    var size: CGFloat = 17

    init(_ name: String, size: CGFloat = 17) {
        self.name = name
        self.size = size
    }

    var body: some View {
        Text(verbatim: name)
            .font(.system(size: size, weight: .bold))
            .foregroundStyle(Color.foreground)
            .lineLimit(1)
            .truncationMode(.tail)
    }
}

/// Centred text, the counterpart of `Label` in shared/Theme.kt.
struct LabelText: View {
    let text: String
    let size: CGFloat
    let color: Color
    let weight: Font.Weight

    init(_ text: String, _ size: CGFloat, _ color: Color, _ weight: Font.Weight = .regular) {
        self.text = text
        self.size = size
        self.color = color
        self.weight = weight
    }

    var body: some View {
        Text(verbatim: text)
            .font(.system(size: size, weight: weight))
            .foregroundStyle(color)
            .multilineTextAlignment(.center)
    }
}

// MARK: Pins on the maps

private let pinSize = CGSize(width: 44, height: 58)

/// A drop-shaped pin with an avatar in its head and its tip at the bottom middle of the image.
func pinImage(color: UIColor, label: String) -> UIImage {
    UIGraphicsImageRenderer(size: pinSize).image { _ in
        let border: CGFloat = 2
        let middle = pinSize.width / 2
        let radius = middle - border
        let centre = CGPoint(x: middle, y: border + radius)
        let drop = UIBezierPath(
            arcCenter: centre,
            radius: radius,
            startAngle: 150 * .pi / 180,
            endAngle: 390 * .pi / 180,
            clockwise: true
        )
        drop.addLine(to: CGPoint(x: middle, y: pinSize.height - border))
        drop.close()
        color.setFill()
        drop.fill()
        UIColor.white.setStroke()
        drop.lineWidth = border
        drop.stroke()
        UIColor.white.setFill()
        let inner = radius * 0.72
        UIBezierPath(ovalIn: CGRect(x: middle - inner, y: centre.y - inner, width: 2 * inner, height: 2 * inner)).fill()
        let text = NSAttributedString(string: label, attributes: [.font: UIFont.systemFont(ofSize: radius)])
        let size = text.size()
        text.draw(at: CGPoint(x: middle - size.width / 2, y: centre.y - size.height / 2))
    }
}
