package io.github.relozia.shrike.chat;

import io.github.relozia.shrike.Shrike;
import java.io.IOException;
import net.minecraft.util.Util;

// a windows toast while the game is in the background (mentions). powershell's own app id, toasts from an id windows
// doesn't know never show up. the text goes in through the environment, never into the script, chat can say anything
// issues:
//   - each toast starts a short lived powershell, fine for something throttled to once every 2 seconds
final class Toast {
	private Toast() {}

	private static final String SCRIPT = String.join("; ",
		"$null = [Windows.UI.Notifications.ToastNotificationManager, Windows.UI.Notifications, ContentType = WindowsRuntime]",
		"$x = [Windows.UI.Notifications.ToastNotificationManager]::GetTemplateContent([Windows.UI.Notifications.ToastTemplateType]::ToastText02)",
		"$t = $x.GetElementsByTagName('text')",
		"$null = $t.Item(0).AppendChild($x.CreateTextNode($env:SHRIKE_TITLE))",
		"$null = $t.Item(1).AppendChild($x.CreateTextNode($env:SHRIKE_TEXT))",
		"$id = '{1AC14E77-02E7-4E5D-B744-2EB1AE5198B7}\\WindowsPowerShell\\v1.0\\powershell.exe'",
		"[Windows.UI.Notifications.ToastNotificationManager]::CreateToastNotifier($id).Show([Windows.UI.Notifications.ToastNotification]::new($x))");

	static void show(String title, String text) {
		if (Util.getPlatform() != Util.OS.WINDOWS) return;
		ProcessBuilder pb = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command", SCRIPT);
		pb.environment().put("SHRIKE_TITLE", title);
		pb.environment().put("SHRIKE_TEXT", text.length() > 200 ? text.substring(0, 200) + "..." : text);
		try {
			pb.start();
		} catch (IOException e) {
			Shrike.LOG.warn("toast failed: {}", e.getMessage());
		}
	}
}
