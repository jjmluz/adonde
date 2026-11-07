import GoogleMaps
import SwiftUI

@main
struct AdondeApp: App {
    @State private var model = GameModel()

    init() {
        // The key comes from Config/Secrets.xcconfig through Info.plist, so it stays out of git.
        let key = Bundle.main.object(forInfoDictionaryKey: "MapsAPIKey") as? String ?? ""
        GMSServices.provideAPIKey(key)
    }

    var body: some Scene {
        WindowGroup {
            GameView(model: model)
                .statusBarHidden()
                .persistentSystemOverlays(.hidden)
                .preferredColorScheme(.dark)
        }
    }
}
