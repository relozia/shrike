package io.github.relozia.shrike.track;

import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.util.Util;

// click timestamps for CPS, fed by MouseHandlerMixin. all on the render thread, no locking needed
public final class Clicks {
	private static final Deque<Long> LEFT = new ArrayDeque<>(), RIGHT = new ArrayDeque<>();

	private Clicks() {}

	public static void press(int button) {
		long now = Util.getMillis();
		if (button == 0) LEFT.add(now);
		else if (button == 1) RIGHT.add(now);
	}

	public static int left() {
		return count(LEFT);
	}

	public static int right() {
		return count(RIGHT);
	}

	private static int count(Deque<Long> d) {
		long cut = Util.getMillis() - 1000;
		while (!d.isEmpty() && d.peekFirst() < cut) d.pollFirst();
		return d.size();
	}
}
