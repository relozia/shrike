package io.github.relozia.kestrel.hud;

import io.github.relozia.kestrel.Kestrel;
import io.github.relozia.kestrel.gui.Ui;
import io.github.relozia.kestrel.setting.Setting;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

// active potion effects with time left, plus warnings for the ones you picked
// issues:
//   - "warn for" only lists the effects people actually drink, not every effect in the game
//   - the warning is center text and a sound, no corner popup, so it's easy to miss mid fight
public class EffectsWidget extends HudWidget {
	private static final Map<String, Holder<MobEffect>> WARNABLE = new LinkedHashMap<>();

	static {
		WARNABLE.put("Strength", MobEffects.STRENGTH);
		WARNABLE.put("Speed", MobEffects.SPEED);
		WARNABLE.put("Fire Resistance", MobEffects.FIRE_RESISTANCE);
		WARNABLE.put("Regeneration", MobEffects.REGENERATION);
		WARNABLE.put("Resistance", MobEffects.RESISTANCE);
		WARNABLE.put("Absorption", MobEffects.ABSORPTION);
		WARNABLE.put("Health Boost", MobEffects.HEALTH_BOOST);
		WARNABLE.put("Haste", MobEffects.HASTE);
		WARNABLE.put("Jump Boost", MobEffects.JUMP_BOOST);
		WARNABLE.put("Slow Falling", MobEffects.SLOW_FALLING);
		WARNABLE.put("Invisibility", MobEffects.INVISIBILITY);
		WARNABLE.put("Night Vision", MobEffects.NIGHT_VISION);
		WARNABLE.put("Water Breathing", MobEffects.WATER_BREATHING);
		WARNABLE.put("Conduit Power", MobEffects.CONDUIT_POWER);
		WARNABLE.put("Dolphin's Grace", MobEffects.DOLPHINS_GRACE);
	}

	private final Setting.Toggle empty = add(new Setting.Toggle("Show When Empty", false));
	private final Setting.Toggle byTime = add(new Setting.Toggle("Color Duration by Time", true));
	private final Setting.Toggle icons = add(new Setting.Toggle("Icons", false));
	private final Setting.Choice sort = add(new Setting.Choice("Sort By", "Time Left", "Time Left", "Name"));
	private final Setting.Toggle blink = add(new Setting.Toggle("Blink When Ending", false));
	private final Setting.Toggle hideVanilla = add(new Setting.Toggle("Hide Vanilla Icons", true));
	private final Setting.Toggle warnOn = add(new Setting.Toggle("Warn When Ending", false));
	private final Setting.Multi warnFor = add(new Setting.Multi("Warn For", List.copyOf(WARNABLE.keySet())));
	private final Setting.Slider before = add(new Setting.Slider("Warn Seconds Before", 5, 0, 60, 1, "s"));
	private final Setting.Toggle centerText = add(new Setting.Toggle("Center Text", true));
	private final Setting.Toggle warnSound = add(new Setting.Toggle("Warning Sound", true));

	private final Map<String, Boolean> was = new HashMap<>(), early = new HashMap<>();
	private String banner = "";
	private boolean bannerBad;
	private long bannerAt;

	public EffectsWidget() {
		super("Effects", '\ue0d5', "Your active effects with time left, and warnings when they run out.", 0.99f, 0.03f);
		startOn();
		// registered once here since the module object lives for the whole game
		HudElementRegistry.replaceElement(VanillaHudElements.MOB_EFFECTS, vanilla -> (g, dt) -> {
			if (!(on() && hideVanilla.on())) vanilla.extractRenderState(g, dt);
		});
		HudElementRegistry.addLast(Kestrel.id("effect_warning"), (g, dt) -> banner(g));
	}

	// ---------- panel ----------

	private static final float PAD = 12, TEXT = 13, LINE = 15.6f, GAP = 4, AMP_GAP = 5, DUR_GAP = 16, MIN_W = 150;

	private static final class Row {
		MobEffectInstance e;
		boolean active = true;
		float alpha, off = 6;
	}

	// keyed by effect and level, so drinking a stronger potion slides a new row in
	private final Map<String, Row> rows = new LinkedHashMap<>();
	private float curW = -1, curH;
	private long lastFrame = Util.getMillis(), previewFrom;

	private record Line(MobEffectInstance e, float alpha, float off, int slot) {}

	private static String key(MobEffectInstance e) {
		return e.getEffect().value().getDescriptionId() + "#" + e.getAmplifier();
	}

	private List<MobEffectInstance> active() {
		Player p = Minecraft.getInstance().player;
		List<MobEffectInstance> out = new ArrayList<>();
		if (p != null) out.addAll(p.getActiveEffects());
		if (sort.get().equals("Name")) out.sort(Comparator.comparing(e -> e.getEffect().value().getDisplayName().getString()));
		else out.sort(Comparator.comparingInt(e -> e.isInfiniteDuration() ? Integer.MAX_VALUE : e.getDuration()));
		return out;
	}

	private static final Object[][] SAMPLES = {{MobEffects.SPEED, 1, 83}, {MobEffects.STRENGTH, 1, 42}, {MobEffects.REGENERATION, 0, 18},
		{MobEffects.RESISTANCE, 1, 95}, {MobEffects.FIRE_RESISTANCE, 0, 128}, {MobEffects.HASTE, 1, 64}};

	@SuppressWarnings("unchecked")
	private static MobEffectInstance sample(int i) {
		Object[] s = SAMPLES[i % SAMPLES.length];
		return new MobEffectInstance((Holder<MobEffect>) s[0], (int) s[2] * 20, (int) s[1]);
	}

	private boolean previewing() {
		return editing && rows.isEmpty();
	}

	// two sample rows at a time in the editor, the current pair fades out over the first half of the swap
	private List<Line> lines() {
		List<Line> out = new ArrayList<>();
		if (previewing()) {
			long t = Util.getMillis() - previewFrom, step = t / 2250, local = t % 2250;
			float blend = local <= 1800 ? 0 : Math.min(1, (local - 1800) / 450f);
			float cur = blend <= 0.5f ? 1 - smooth(blend * 2) : 0, next = blend > 0.5f ? smooth((blend - 0.5f) * 2) : 0;
			for (int i = 0; i < 2; i++) {
				if (cur > 0.001f) out.add(new Line(sample((int) step + i), cur, 0, i));
				if (next > 0.001f) out.add(new Line(sample((int) step + i + 1), next, 0, i));
			}
			return out;
		}
		int i = 0;
		for (Row r : rows.values()) out.add(new Line(r.e, r.alpha, r.off, i++));
		return out;
	}

	private static float smooth(float x) {
		return x * x * (3 - 2 * x);
	}

	private float rowW(MobEffectInstance e) {
		return width(name(e), LIGHT, TEXT) + AMP_GAP + width(amp(e), HUD, TEXT) + DUR_GAP + width(time(e), LIGHT, TEXT) + PAD * 2 + (icons.on() ? 20 : 0);
	}

	@Override
	public void tick(Minecraft mc) {
		java.util.Set<String> seen = new java.util.HashSet<>();
		for (MobEffectInstance e : active()) {
			Row r = rows.computeIfAbsent(key(e), k -> new Row());
			r.e = e;
			r.active = true;
			seen.add(key(e));
		}
		for (Map.Entry<String, Row> en : rows.entrySet()) if (!seen.contains(en.getKey())) en.getValue().active = false;
		rows.values().removeIf(r -> !r.active && r.alpha < 0.01f);
		warnings(mc);
	}

	private int rowCount() {
		return previewing() ? 2 : rows.isEmpty() && empty.on() ? 1 : rows.size();
	}

	private void animate() {
		long now = Util.getMillis();
		float k = Math.min(1, (now - lastFrame) / 1000f * 12);
		lastFrame = now;
		float tw = MIN_W;
		if (previewing()) for (int i = 0; i < SAMPLES.length; i++) tw = Math.max(tw, rowW(sample(i)));
		else if (rows.isEmpty() && empty.on()) tw = Math.max(MIN_W, width("No effects", LIGHT, TEXT) + PAD * 2 + (icons.on() ? 20 : 0));
		else for (Row r : rows.values()) tw = Math.max(tw, rowW(r.e));
		int n = rowCount();
		float th = n == 0 ? 0 : PAD * 2 + LINE * n + GAP * (n - 1);
		if (curW < 0) {
			curW = tw;
			curH = th;
		}
		curW += (tw - curW) * k;
		curH += (th - curH) * k;
		for (Row r : rows.values()) {
			r.alpha += ((r.active ? 1 : 0) - r.alpha) * k;
			r.off += ((r.active ? 0 : -6) - r.off) * k;
		}
	}

	@Override
	public boolean visible() {
		return previewing() || !rows.isEmpty() || empty.on() || curH > 0.5f;
	}

	@Override
	protected int contentW() {
		return Math.round(Math.max(curW, MIN_W));
	}

	@Override
	protected int contentH() {
		return Math.max(1, Math.round(curH));
	}

	@Override
	public void render(GuiGraphicsExtractor g, int sw, int sh) {
		if (editing && previewFrom == 0) previewFrom = Util.getMillis();
		if (!editing) previewFrom = 0;
		animate();
		super.render(g, sw, sh);
	}

	private static String name(MobEffectInstance e) {
		return e.getEffect().value().getDisplayName().getString();
	}

	private static String amp(MobEffectInstance e) {
		return roman(e.getAmplifier() + 1);
	}

	private static String roman(int n) {
		String[] r = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
		return n >= 1 && n <= 10 ? r[n - 1] : Integer.toString(n);
	}

	// m:ss once there's a minute, plain seconds under that
	private static String time(MobEffectInstance e) {
		if (e.isInfiniteDuration()) return "\u221e";
		int s = e.getDuration() / 20;
		return s >= 60 ? String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60) : Integer.toString(s);
	}

	@Override
	protected void content(GuiGraphicsExtractor g) {
		float w = contentW();
		g.enableScissor(0, 0, contentW(), contentH());
		float tx = PAD + (icons.on() ? 20 : 0);
		if (rows.isEmpty() && empty.on() && !previewing()) draw(g, "No effects", LIGHT, tx, PAD + LINE * 0.8f, LABEL, TEXT);
		for (Line l : lines()) {
			if (l.alpha <= 0.001f) continue;
			MobEffectInstance e = l.e;
			float base = PAD + LINE * 0.8f + l.slot * (LINE + GAP) + l.off;
			// blinks for the last 10 seconds like vanilla's icons do, if you want it
			float a = l.alpha;
			if (blink.on() && !e.isInfiniteDuration() && e.getDuration() < 200) a *= 0.55f + 0.45f * (float) Math.abs(Math.sin(Util.getMillis() / 200.0));
			if (icons.on()) g.blitSprite(RenderPipelines.GUI_TEXTURED, Hud.getMobEffectSprite(e.getEffect()), Math.round(PAD), Math.round(base - 11.5f), 15, 15, ARGB.white(a));
			String n = name(e);
			draw(g, n, LIGHT, tx, base, ARGB.multiplyAlpha(0xFFFFFFFF, a), TEXT);
			draw(g, amp(e), HUD, tx + width(n, LIGHT, TEXT) + AMP_GAP, base, ARGB.multiplyAlpha(Ui.accent(), a), TEXT);
			String t = time(e);
			int dc = !byTime.on() ? LABEL : !e.isInfiniteDuration() && e.getDuration() < 600 ? 0xFFFF4444 : 0xFF44FF44;
			draw(g, t, LIGHT, w - PAD - width(t, LIGHT, TEXT), base, ARGB.multiplyAlpha(dc, a), TEXT);
		}
		g.disableScissor();
	}

	// ---------- warnings ----------

	private void warnings(Minecraft mc) {
		Player p = mc.player;
		if (p == null || !warnOn.on()) {
			was.clear();
			early.clear();
			return;
		}
		for (String n : warnFor.get()) {
			MobEffectInstance e = p.getEffect(WARNABLE.get(n));
			boolean active = e != null;
			if (active && !e.isInfiniteDuration()) {
				int left = (int) Math.ceil(e.getDuration() / 20.0);
				if (before.get() > 0 && left <= before.get() && !early.getOrDefault(n, false)) {
					early.put(n, true);
					warn(mc, n + " ending in " + left + "s", false);
				} else if (left > before.get() + 1) {
					// drank another one, the early warning can fire again later
					early.put(n, false);
				}
			}
			if (!active && was.getOrDefault(n, false)) {
				early.put(n, false);
				warn(mc, n + " ran out", true);
			}
			was.put(n, active);
		}
	}

	private void warn(Minecraft mc, String msg, boolean bad) {
		if (centerText.on()) {
			banner = msg.toUpperCase(Locale.ROOT);
			bannerBad = bad;
			bannerAt = Util.getMillis();
		}
		if (warnSound.on()) {
			var s = bad ? SoundEvents.NOTE_BLOCK_BASS : SoundEvents.NOTE_BLOCK_PLING;
			mc.getSoundManager().play(SimpleSoundInstance.forUI(s.value(), bad ? 0.8f : 1.4f, 0.9f));
		}
	}

	// big text a third of the way down, fades out over the last 400ms of its 2 seconds
	private void banner(GuiGraphicsExtractor g) {
		long age = Util.getMillis() - bannerAt;
		if (banner.isEmpty() || age > 2000 || !on()) return;
		float a = age < 1600 ? 1 : 1 - (age - 1600) / 400f;
		Component c = Ui.bold(banner);
		g.pose().pushMatrix();
		g.pose().translate(g.guiWidth() / 2f, g.guiHeight() * 0.3f);
		g.pose().scale(2, 2);
		int w = Ui.width(c);
		g.text(font(), c, -w / 2, 0, ARGB.multiplyAlpha(bannerBad ? 0xFFE5484D : 0xFFF5C542, a), true);
		g.pose().popMatrix();
	}
}
