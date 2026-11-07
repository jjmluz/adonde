// Draws the TV launcher banner and icon, and the iOS app icon.
// Usage: swift tools/make_art.swift app/src/main/res/drawable-nodpi [ios/Adonde/Assets.xcassets/AppIcon.appiconset]
import AppKit

let background = NSColor(red: 0.043, green: 0.071, blue: 0.125, alpha: 1)
let foreground = NSColor(red: 0.957, green: 0.965, blue: 0.984, alpha: 1)
let accent = NSColor(red: 1, green: 0.784, blue: 0.239, alpha: 1)
// The logo font is shared by both apps, so it lives in the shared module.
let fontFile = URL(fileURLWithPath: #filePath).deletingLastPathComponent()
    .appendingPathComponent("../shared/src/main/res/font/outfit.ttf")
let outfit = CGFont(CGDataProvider(url: fontFile as CFURL)!)!.copy(withVariations: ["Weight": 800] as CFDictionary)!

func pin(height: CGFloat, tip: CGPoint) {
    let radius = height * 0.36
    let centre = CGPoint(x: tip.x, y: tip.y + height - radius)
    let drop = NSBezierPath()
    drop.appendArc(withCenter: centre, radius: radius, startAngle: -30, endAngle: 210)
    drop.line(to: tip)
    drop.close()
    accent.setFill()
    drop.fill()
    background.setFill()
    NSBezierPath(ovalIn: NSRect(x: centre.x - radius * 0.42, y: centre.y - radius * 0.42,
                                width: radius * 0.84, height: radius * 0.84)).fill()
}

func save(_ name: String, width: Int, height: Int, in folder: String = CommandLine.arguments[1], draw: () -> Void) {
    let context = CGContext(data: nil, width: width, height: height, bitsPerComponent: 8, bytesPerRow: 0,
                            space: CGColorSpace(name: CGColorSpace.sRGB)!,
                            bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue)!
    NSGraphicsContext.current = NSGraphicsContext(cgContext: context, flipped: false)
    background.setFill()
    NSRect(x: 0, y: 0, width: width, height: height).fill()
    draw()
    NSGraphicsContext.current?.flushGraphics()
    let path = folder + "/" + name
    let bitmap = NSBitmapImageRep(cgImage: context.makeImage()!)
    try! bitmap.representation(using: .png, properties: [:])!.write(to: URL(fileURLWithPath: path))
}

save("banner.png", width: 640, height: 360) {
    let font = CTFontCreateWithGraphicsFont(outfit, 112, nil, nil) as NSFont
    let attributes: [NSAttributedString.Key: Any] = [.font: font, .foregroundColor: foreground]
    let start = NSAttributedString(string: "AD", attributes: attributes)
    let end = NSAttributedString(string: "NDE?", attributes: attributes)
    let pinHeight: CGFloat = 112
    let pinWidth = pinHeight * 0.74
    let gap: CGFloat = 4
    let width = start.size().width + pinWidth + end.size().width + gap * 2
    let left = (640 - width) / 2
    let baseline: CGFloat = 138
    start.draw(at: CGPoint(x: left, y: baseline + font.descender))
    let middle = left + start.size().width + gap + pinWidth / 2
    pin(height: pinHeight, tip: CGPoint(x: middle, y: baseline - pinHeight * 0.28))
    end.draw(at: CGPoint(x: left + start.size().width + pinWidth + gap * 2, y: baseline + font.descender))
}

save("icon.png", width: 360, height: 360) {
    pin(height: 268, tip: CGPoint(x: 180, y: 46))
}

// The same pin for iOS, which wants one 1024-point square and rounds its corners itself.
if CommandLine.arguments.count > 2 {
    save("icon.png", width: 1024, height: 1024, in: CommandLine.arguments[2]) {
        pin(height: 268 * 1024 / 360, tip: CGPoint(x: 512, y: 46 * 1024 / 360))
    }
}
