import Cocoa
import CoreText
import Darwin

final class BuddyApp: NSObject, NSApplicationDelegate {
    private var statusItem: NSStatusItem?
    private var controller: Process?
    private var awake: Process?
    private var log: FileHandle?
    private var timer: Timer?
    private var setupURL: URL?
    private var stopping = false
    private var restarting = false
    private var support: URL!
    private var readyFile: URL!
    private var deadline = Date()
    private let smoke = CommandLine.arguments.contains("--smoke-test")

    func applicationDidFinishLaunching(_ notification: Notification) {
        do {
            if smoke, let index = CommandLine.arguments.firstIndex(of: "--smoke-test"), CommandLine.arguments.count > index + 1 {
                support = URL(fileURLWithPath: CommandLine.arguments[index + 1], isDirectory: true)
            } else {
                support = try FileManager.default.url(for: .applicationSupportDirectory, in: .userDomainMask, appropriateFor: nil, create: true).appendingPathComponent("DuoBuddy", isDirectory: true)
            }
            try FileManager.default.createDirectory(at: support, withIntermediateDirectories: true, attributes: [.posixPermissions: 0o700])
            try FileManager.default.setAttributes([.posixPermissions: 0o700], ofItemAtPath: support.path)
            readyFile = support.appendingPathComponent("ready-\(ProcessInfo.processInfo.processIdentifier).json")
            if !smoke { makeMenu() }
            try launchController()
        } catch { fail(error.localizedDescription) }
    }

    private func brandFont() -> NSFont? {
        guard let url = Bundle.main.resourceURL?.appendingPathComponent("controller/android/assets/fonts/Quicksand.ttf"),
              FileManager.default.fileExists(atPath: url.path) else { return nil }
        CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        let descriptor = NSFontDescriptor(fontAttributes: [.family: "Quicksand", .traits: [NSFontDescriptor.TraitKey.weight: NSFont.Weight.semibold.rawValue]])
        return NSFont(descriptor: descriptor, size: 13)
    }

    private func makeMenu() {
        statusItem = NSStatusBar.system.statusItem(withLength: NSStatusItem.variableLength)
        let font = brandFont()
        statusItem?.button?.font = font
        statusItem?.button?.title = "DuoBuddy"
        statusItem?.button?.image = NSImage(systemSymbolName: "book.closed", accessibilityDescription: "DuoBuddy")
        statusItem?.button?.imagePosition = .imageLeading
        let menu = NSMenu()
        if let font = font { menu.font = font }
        for (title, action) in [("Open DuoBuddy setup", #selector(openSetup)), ("Check for updates…", #selector(openUpdates)), ("Show controller log", #selector(showLog)), ("Restart DuoBuddy", #selector(restart)), ("Quit DuoBuddy", #selector(quit))] {
            let item = NSMenuItem(title: title, action: action, keyEquivalent: "")
            item.target = self
            menu.addItem(item)
        }
        statusItem?.menu = menu
        statusItem?.button?.toolTip = "Starting DuoBuddy…"
    }

    private func launchController() throws {
        guard let resources = Bundle.main.resourceURL else { throw NSError(domain: "DuoBuddy", code: 1, userInfo: [NSLocalizedDescriptionKey: "The app bundle is incomplete. Download DuoBuddy again."]) }
        let logURL = support.appendingPathComponent("controller.log")
        log?.closeFile()
        if let size = (try? FileManager.default.attributesOfItem(atPath: logURL.path)[.size]) as? NSNumber, size.intValue > 2_000_000 {
            let previous = support.appendingPathComponent("controller.previous.log")
            try? FileManager.default.removeItem(at: previous)
            try FileManager.default.moveItem(at: logURL, to: previous)
        }
        if !FileManager.default.fileExists(atPath: logURL.path) { FileManager.default.createFile(atPath: logURL.path, contents: nil, attributes: [.posixPermissions: 0o600]) }
        try FileManager.default.setAttributes([.posixPermissions: 0o600], ofItemAtPath: logURL.path)
        log = try FileHandle(forWritingTo: logURL)
        log?.seekToEndOfFile()
        try? FileManager.default.removeItem(at: readyFile)
        let process = Process()
        process.executableURL = resources.appendingPathComponent("bin/node")
        process.arguments = [resources.appendingPathComponent("controller/server.mjs").path]
        process.currentDirectoryURL = support
        var env = ProcessInfo.processInfo.environment
        for key in Array(env.keys) where key.hasPrefix("TV_") || key == "NODE_OPTIONS" || key == "NODE_PATH" { env.removeValue(forKey: key) }
        env["TV_DISTRIBUTION"] = "1"
        env["TV_VOICE_ENGINE"] = "mac"
        env["TV_RUNTIME_DIR"] = support.path
        env["TV_ADB"] = resources.appendingPathComponent("bin/adb").path
        env["TV_READY_FILE"] = readyFile.path
        env["TV_PORT"] = "18765"
        env["TV_ADMIN_PORT"] = "18766"
        // Headless smoke runs use an isolated, intentionally non-existent ADB path.
        if smoke { env["TV_ADB"] = support.appendingPathComponent("no-adb-in-smoke-test").path }
        process.environment = env
        process.standardOutput = log
        process.standardError = log
        process.standardInput = FileHandle.nullDevice
        process.terminationHandler = { [weak self] task in
            DispatchQueue.main.async {
                guard let self = self else { return }
                self.timer?.invalidate()
                self.awake?.terminate()
                if self.restarting {
                    self.restarting = false
                    do { try self.launchController() } catch { self.fail(error.localizedDescription) }
                }
                else if self.stopping { NSApp.reply(toApplicationShouldTerminate: true) }
                else { self.setupURL = nil; self.fail("The controller stopped (code \(task.terminationStatus)). Check the controller log. If another DuoBuddy controller is running, quit it before restarting.") }
            }
        }
        controller = process
        try process.run()
        let caffeine = Process()
        caffeine.executableURL = URL(fileURLWithPath: "/usr/bin/caffeinate")
        caffeine.arguments = ["-i", "-w", String(process.processIdentifier)]
        try caffeine.run()
        awake = caffeine
        deadline = Date().addingTimeInterval(20)
        timer = Timer.scheduledTimer(withTimeInterval: 0.2, repeats: true) { [weak self] _ in self?.checkReady() }
    }

    private func checkReady() {
        guard let task = controller, task.isRunning else { return }
        if let data = try? Data(contentsOf: readyFile),
           let info = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any],
           let pid = info["pid"] as? Int, pid == Int(task.processIdentifier),
           let address = info["url"] as? String, let url = URL(string: address),
           url.scheme == "http", url.host == "127.0.0.1", url.port == 18766 {
            timer?.invalidate()
            setupURL = url
            statusItem?.button?.toolTip = "DuoBuddy is running. Keep this Mac awake and on the TV’s network."
            if smoke { print("DuoBuddy controller ready with bundled runtime and local voice."); quit() }
            else { openSetup() }
        } else if Date() > deadline {
            timer?.invalidate()
            fail("DuoBuddy did not start within 20 seconds. Open the controller log for details.")
            if smoke { quit() }
        }
    }

    private func fail(_ message: String) {
        statusItem?.button?.toolTip = "DuoBuddy stopped — open the log or restart."
        if smoke { fputs("DuoBuddy smoke test failed: \(message)\n", stderr); exit(1) }
        let alert = NSAlert()
        alert.messageText = "DuoBuddy needs attention"
        alert.informativeText = message
        alert.addButton(withTitle: "Show log")
        alert.addButton(withTitle: "Close")
        NSApp.activate(ignoringOtherApps: true)
        if alert.runModal() == .alertFirstButtonReturn { showLog() }
    }

    @objc private func openSetup() { if let url = setupURL { NSWorkspace.shared.open(url) } }
    @objc private func openUpdates() {
        if let url = setupURL, var parts = URLComponents(url: url, resolvingAgainstBaseURL: false) {
            parts.fragment = "check-update"
            if let link = parts.url { NSWorkspace.shared.open(link) }
        }
    }
    @objc private func showLog() { NSWorkspace.shared.open(support.appendingPathComponent("controller.log")) }
    @objc private func restart() {
        if let task = controller, task.isRunning {
            restarting = true
            setupURL = nil
            timer?.invalidate()
            task.terminate()
            DispatchQueue.main.asyncAfter(deadline: .now() + 5) { if self.restarting && task.isRunning { kill(task.processIdentifier, SIGKILL) } }
            return
        }
        setupURL = nil
        do { try launchController() } catch { fail(error.localizedDescription) }
    }
    @objc private func quit() { NSApp.terminate(nil) }
    func applicationShouldHandleReopen(_ sender: NSApplication, hasVisibleWindows flag: Bool) -> Bool { openSetup(); return false }
    func applicationShouldTerminate(_ sender: NSApplication) -> NSApplication.TerminateReply {
        timer?.invalidate()
        restarting = false
        if let task = controller, task.isRunning {
            stopping = true
            task.terminate()
            DispatchQueue.main.asyncAfter(deadline: .now() + 5) {
                if task.isRunning { kill(task.processIdentifier, SIGKILL) }
                NSApp.reply(toApplicationShouldTerminate: true)
            }
            return .terminateLater
        }
        return .terminateNow
    }
    func applicationWillTerminate(_ notification: Notification) {
        awake?.terminate()
        if let file = readyFile { try? FileManager.default.removeItem(at: file) }
        log?.closeFile()
    }
}

let app = NSApplication.shared
app.setActivationPolicy(.accessory)
let delegate = BuddyApp()
app.delegate = delegate
app.run()
