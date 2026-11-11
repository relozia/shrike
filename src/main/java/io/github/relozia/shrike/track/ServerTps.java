package io.github.relozia.shrike.track;

import net.minecraft.util.Util;

// the server sends the world's game time every 20 ticks, so ticks passed / real seconds passed
// between two of those packets is its tick rate
// issues:
//   - needs two packets before it knows anything, so nothing shows for the first second after joining
//   - integrated server (singleplayer) sends them too, reads ~20 there which is correct
public final class ServerTps {
	private static final float[] recent = new float[5];
	private static int n, idx;
	private static long lastGame = -1, lastAt;

	private ServerTps() {}

	public static void onTime(long gameTime) {
		long now = Util.getMillis();
		long dt = now - lastAt;
		// a long gap means we just joined or the game froze, that sample would read way too low
		if (lastGame >= 0 && gameTime > lastGame && dt > 50 && dt < 5000) {
			float tps = Math.min(20f, (gameTime - lastGame) / (dt / 1000f));
			recent[idx++ % recent.length] = tps;
			n = Math.min(n + 1, recent.length);
		}
		lastGame = gameTime;
		lastAt = now;
	}

	// -1 until there's a reading
	public static float tps() {
		if (n == 0) return -1;
		float sum = 0;
		for (int i = 0; i < n; i++) sum += recent[i];
		return sum / n;
	}
}
