// Shows a login URL in the macOS sign-in window (ASWebAuthenticationSession), the one native apps
// use: a window of its own without tabs or address bar, with the browser's saved passwords and
// sessions. c8poc compiles this on first use.
//
// Usage: login-window <url> <callback scheme>
// Exits 0 when a page navigates to <callback scheme>://... (c8poc's result page does, to close the
// window), 2 when the user cancels, 1 on any other error.
import AppKit
import AuthenticationServices

final class Anchor: NSObject, ASWebAuthenticationPresentationContextProviding {
  let window = NSWindow(contentRect: .zero, styleMask: [], backing: .buffered, defer: true)
  func presentationAnchor(for session: ASWebAuthenticationSession) -> ASPresentationAnchor { window }
}

let arguments = CommandLine.arguments
guard arguments.count == 3, let url = URL(string: arguments[1]) else {
  FileHandle.standardError.write("usage: login-window <url> <callback scheme>\n".data(using: .utf8)!)
  exit(1)
}

let app = NSApplication.shared
app.setActivationPolicy(.accessory)
let anchor = Anchor()
let session = ASWebAuthenticationSession(url: url, callbackURLScheme: arguments[2]) { _, error in
  if let error = error as? ASWebAuthenticationSessionError, error.code == .canceledLogin { exit(2) }
  exit(error == nil ? 0 : 1)
}
session.presentationContextProvider = anchor
session.prefersEphemeralWebBrowserSession = false
DispatchQueue.main.async {
  app.activate(ignoringOtherApps: true)
  if !session.start() { exit(1) }
}
app.run()
