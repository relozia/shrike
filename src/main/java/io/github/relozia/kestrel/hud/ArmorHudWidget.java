package io.github.relozia.kestrel.hud;

import com.google.gson.JsonObject;
import io.github.relozia.kestrel.Kestrel;
import io.github.relozia.kestrel.config.Config;
import io.github.relozia.kestrel.gui.Notice;
import io.github.relozia.kestrel.gui.Ui;
import io.github.relozia.kestrel.setting.Setting;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

// armor durability as 22px hotbar style slots, 20 apart with the item 3px in, the geometry uku uses so its
// imported offsets land where they should
// the held item, offhand and (with Move Pieces Separately) each piece are parts, their own boxes in the editor
// issues:
//   - Kestrel's hotbar never scales or moves, so attached mode only has the vanilla hotbar to follow
//   - uku's non hotbar anchors don't import, it stays where it was and you drag it into place
public class ArmorHudWidget extends HudWidget {
	private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
	private static final EquipmentSlot[] WATCHED = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};
	private static final String[] KEYS = {"helmet", "chestplate", "leggings", "boots", "held", "offhand"};
	private static final Identifier HOTBAR = Identifier.withDefaultNamespace("hud/hotbar");
	private static final int SLOT = 22, INSET = 3, BAND = 10;
	// item stacks can't exist before item components are bound, so the editor samples get made on first use
	private static List<ItemStack> sampleArmor;
	private static ItemStack sampleHeld, sampleOff;

	private final Setting.Choice layout = add(new Setting.Choice("Layout", "Horizontal", "Horizontal", "Vertical"));
	private final Setting.Choice attach = add(new Setting.Choice("Attach To Hotbar", "Left", "Off", "Left", "Right"));
	private final Setting.Slider offX = add(new Setting.Slider("X Offset", 0, -400, 400, 1, ""));
	private final Setting.Slider offY = add(new Setting.Slider("Y Offset", 0, -400, 400, 1, ""));
	private final Setting.Toggle room = add(new Setting.Toggle("Make Room For Offhand", true));
	private final Setting.Toggle separate = add(new Setting.Toggle("Move Pieces Separately", false));
	private final Setting.Toggle hideEmpty = add(new Setting.Toggle("Hide Empty Slots", true));
	private final Setting.Toggle reverse = add(new Setting.Toggle("Reverse Order", false));
	private final Setting.Choice background = add(new Setting.Choice("Background", "Hotbar", "Hotbar", "Tiles", "Glass", "Minimal", "None"));
	private final Setting.Toggle border = add(new Setting.Toggle("Border", true));
	private final Setting.Choice borderTint = add(new Setting.Choice("Border Tint", "Durability", "Durability", "Custom"));
	private final Setting.Color borderColor = add(new Setting.Color("Border Color", 0x80FFFFFF,
		0xFFFFFFFF, 0xFFE0703A, 0xFFF5A524, 0xFF5B8DEF, 0xFF2EC4B6).withAlpha());
	private final Setting.Color bgColor = add(new Setting.Color("Background Color", 0xB0101014,
		0xFF101014, 0xFF000000, 0xFF1D2028, 0xFF2A1A12, 0xFF101A2E).withAlpha());
	private final Setting.Choice duraSide = add(new Setting.Choice("Durability Side", "Auto", "Auto", "Right", "Left"));
	private final Setting.Toggle bar = add(new Setting.Toggle("Durability Bar", true));
	private final Setting.Toggle hideNumbers = add(new Setting.Toggle("Hide Durability Numbers", false));
	private final Setting.Toggle percent = add(new Setting.Toggle("Show As Percent", false));
	private final Setting.Toggle maxDamage = add(new Setting.Toggle("Show Max Damage", false));
	private final Setting.Toggle noColors = add(new Setting.Toggle("Disable Colors", false));
	private final Setting.Toggle textShadow = add(new Setting.Toggle("Text Shadow", true));
	private final Setting.Toggle lowWarn = add(new Setting.Toggle("Low Durability Warning", true));
	private final Setting.Choice warnStyle = add(new Setting.Choice("Warning Style", "Both", "Both", "Pulse", "Icon"));
	private final Setting.Slider warnBelow = add(new Setting.Slider("Warn Below", 10, 1, 50, 1, "%"));
	private final Setting.Slider warnValue = add(new Setting.Slider("Warn Below Value", 5, 0, 200, 1, ""));
	private final Setting.Toggle warnSound = add(new Setting.Toggle("Warning Sound", true));
	private final Setting.Choice sound = add(new Setting.Choice("Sound", "Item Break", "Item Break", "Ding", "Anvil"));
	private final Setting.Slider volume = add(new Setting.Slider("Sound Volume", 60, 5, 100, 5, "%"));
	private final Setting.Toggle handWarn = add(new Setting.Toggle("Warn For Held Items", false));
	private final Setting.Toggle showHeld = add(new Setting.Toggle("Show Held Item", false));
	private final Setting.Toggle showOff = add(new Setting.Toggle("Show Offhand Item", false));
	private final Setting.Toggle allHeld = add(new Setting.Toggle("Show All Held Items", false));
	private final Setting.Slider spacing = add(new Setting.Slider("Spacing", 20, 18, 28, 1, ""));
	private final Setting.Slider size = add(new Setting.Slider("Scale", 1, 0.5, 2, 0.1, "x"));

	private final List<Part> loose = new ArrayList<>();
	private final List<HudWidget> parts = new ArrayList<>();
	private final int[] lastDamage = {-1, -1, -1, -1, -1, -1};
	private final Item[] lastItem = new Item[6];

	public ArmorHudWidget() {
		// where it lands when it isn't attached to the hotbar, bottom right just over the hotbar line
		super("Armor HUD", '\ue158', "Armor durability as hotbar style slots. Attach it to the hotbar or drag it anywhere.", 0.985f, 0.83f);
		add(new Setting.Action("Uku's Armor HUD Settings", "Import", this::importUku));
		parts.add(this);
		for (int i = 0; i < 6; i++) {
			Part p = new Part(i);
			loose.add(p);
			parts.add(p);
		}
		startOn();
	}

	@Override
	public List<HudWidget> boxes() {
		return parts;
	}

	@Override protected boolean units() { return false; }
	@Override protected Look look() { return Look.NONE; }

	// ---------- data ----------

	private record Piece(ItemStack stack, String text, float frac, boolean dmg, boolean warn) {}

	private Piece piece(ItemStack st) {
		if (!st.isDamageableItem()) return new Piece(st, "", 1, false, false);
		int max = Math.max(1, st.getMaxDamage()), left = max - st.getDamageValue();
		float f = Mth.clamp(left / (float) max, 0, 1);
		String t = hideNumbers.on() ? "" : percent.on() ? Math.round(f * 100) + "%" : maxDamage.on() ? left + "/" + max : Integer.toString(left);
		return new Piece(st, t, f, true, low(st));
	}

	// low = at or under the percent line or the value line, whichever comes first
	private boolean low(ItemStack st) {
		if (!lowWarn.on() || st.isEmpty() || !st.isDamageableItem()) return false;
		int max = Math.max(1, st.getMaxDamage()), left = max - st.getDamageValue();
		return left * 100f / max <= warnBelow.get() || left <= warnValue.get();
	}

	private List<Piece> armor() {
		Player p = Minecraft.getInstance().player;
		List<Piece> out = new ArrayList<>(4);
		if (p == null) return out;
		boolean any = false;
		for (EquipmentSlot s : ARMOR) {
			ItemStack st = p.getItemBySlot(s);
			any |= !st.isEmpty();
			if (!st.isEmpty() || !hideEmpty.on()) out.add(piece(st));
		}
		if (!any) {
			out.clear();
			if (editing) for (ItemStack st : sampleArmor()) out.add(piece(st));
		}
		if (reverse.on()) Collections.reverse(out);
		return out;
	}

	private boolean horizontal() {
		return layout.get().equals("Horizontal");
	}

	private int step() {
		return (int) Math.round(spacing.get());
	}

	private float base() {
		return size.get().floatValue();
	}

	private boolean attached() {
		return !attach.get().equals("Off") && !separate.on();
	}

	private boolean textRight(float centerX, int sw) {
		return switch (duraSide.get()) {
			case "Right" -> true;
			case "Left" -> false;
			default -> centerX <= sw * 0.5f;
		};
	}

	// ---------- warning sound ----------

	// plays each time a piece that's already low takes more damage, not just once when it crosses the line
	@Override
	public void tick(Minecraft mc) {
		if (mc.player == null) return;
		for (int i = 0; i < WATCHED.length; i++) {
			ItemStack st = mc.player.getItemBySlot(WATCHED[i]);
			int dmg = st.isEmpty() ? -1 : st.getDamageValue();
			boolean hurt = !st.isEmpty() && st.getItem() == lastItem[i] && lastDamage[i] >= 0 && dmg > lastDamage[i];
			lastDamage[i] = dmg;
			lastItem[i] = st.isEmpty() ? null : st.getItem();
			if (hurt && (i < 4 || handWarn.on()) && warnSound.on() && low(st)) warnSound(mc);
		}
	}

	private void warnSound(Minecraft mc) {
		float v = volume.get().floatValue() / 100f;
		mc.getSoundManager().play(switch (sound.get()) {
			case "Ding" -> SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 1.6f, v);
			// anvil at full volume is a lot louder than the other two
			case "Anvil" -> SimpleSoundInstance.forUI(SoundEvents.ANVIL_LAND, 1.8f, v * 0.5f);
			default -> SimpleSoundInstance.forUI(SoundEvents.ITEM_BREAK.value(), 1.4f, v);
		});
	}

	// ---------- layout (unscaled gui pixels) ----------

	private record Layout(boolean horizontal, boolean above, boolean left, int width, int height, int stripX, int stripY) {
		int slotX(int i, int step) { return horizontal ? stripX + i * step : stripX; }
		int slotY(int i, int step) { return horizontal ? stripY : stripY + i * step; }
	}

	private Layout layoutFor(List<Piece> ps, boolean above, boolean left) {
		int len = SLOT + Math.max(0, ps.size() - 1) * step(), tw = 0;
		for (Piece p : ps) if (!p.text.isEmpty()) tw = Math.max(tw, font().width(p.text));
		if (horizontal()) {
			int band = tw > 0 ? BAND : 0;
			return new Layout(true, above, false, len, SLOT + band, 0, above ? band : 0);
		}
		int band = tw > 0 ? tw + 3 : 0;
		return new Layout(false, false, left, SLOT + band, len, left ? band : 0, 0);
	}

	// the group's box, text goes above when it sits on the hotbar so it doesn't hang off the screen bottom
	private Layout groupLayout(List<Piece> ps, boolean left) {
		return layoutFor(ps, attached() && horizontal(), left);
	}

	@Override
	public boolean visible() {
		return !separate.on() && !armor().isEmpty();
	}

	@Override
	protected int contentW() {
		return Mth.ceil(groupLayout(armor(), false).width * base());
	}

	@Override
	protected int contentH() {
		return Mth.ceil(groupLayout(armor(), false).height * base());
	}

	// 98 from the middle is the hotbar's half width plus uku's gap, imported offsets only line up with that
	private float[] anchor(int sw, int sh) {
		Minecraft mc = Minecraft.getInstance();
		boolean left = attach.get().equals("Left");
		float extra = 0;
		if (room.on()) {
			// the offhand slot sits on the off arm's side, vanilla's attack indicator on the other
			boolean offHere = (mc.player.getMainArm().getOpposite() == HumanoidArm.LEFT) == left;
			if (offHere && !mc.player.getOffhandItem().isEmpty()) extra = 29;
			else if (!offHere && mc.options.attackIndicator().get() == AttackIndicatorStatus.HOTBAR && mc.player.getAttackStrengthScale(0) < 1) extra = 23;
		}
		// whole pixels like vanilla's own hotbar (sw / 2 - 91), a float middle left it half a pixel off on odd widths
		float edge = left ? sw / 2 - 98 - extra : sw / 2 + 98 + extra;
		float w = w() * scale, h = h() * scale;
		return new float[] {(left ? edge - w : edge) + offX.get().floatValue(), sh - h + offY.get().floatValue()};
	}

	@Override
	public int px(int sw) {
		return attached() ? Math.round(anchor(sw, Minecraft.getInstance().getWindow().getGuiScaledHeight())[0]) : super.px(sw);
	}

	@Override
	public int py(int sh) {
		return attached() ? Math.round(anchor(Minecraft.getInstance().getWindow().getGuiScaledWidth(), sh)[1]) : super.py(sh);
	}

	// dragging an attached strip only changes how far it sits from the hotbar, it stays attached
	@Override
	public void moveTo(float x, float y, int sw, int sh) {
		if (!attached()) {
			super.moveTo(x, y, sw, sh);
			return;
		}
		float[] a = anchor(sw, sh);
		offX.set(offX.get() + x - a[0]);
		offY.set(offY.get() + y - a[1]);
	}

	@Override
	protected void content(GuiGraphicsExtractor g) {
		List<Piece> ps = armor();
		int sw = g.guiWidth();
		boolean left = attached() ? attach.get().equals("Left") : !textRight(px(sw) + w() * scale / 2f, sw);
		paint(g, ps, groupLayout(ps, left));
	}

	// ---------- parts ----------

	// 0-3 a loose armor piece, 4 the held item, 5 the offhand
	private class Part extends HudWidget {
		final int kind;
		// not dragged yet: sits where it would be in the strip
		boolean placed;

		Part(int kind) {
			super("Armor HUD", '\ue158', "", 0, 0);
			this.kind = kind;
		}

		@Override protected boolean units() { return false; }
		@Override protected Look look() { return Look.NONE; }

		List<Piece> pieces() {
			Player p = Minecraft.getInstance().player;
			if (p == null) return List.of();
			if (kind >= 4) {
				ItemStack st = kind == 4 ? p.getMainHandItem() : p.getOffhandItem();
				// tools and armor have durability to show, a stack of blocks doesn't unless you ask for all of them
				if (!st.isEmpty() && (allHeld.on() || st.isDamageableItem())) return List.of(piece(st));
				return editing ? List.of(piece(kind == 4 ? sampleHeld() : sampleOff())) : List.of();
			}
			boolean any = false;
			for (EquipmentSlot s : ARMOR) any |= !p.getItemBySlot(s).isEmpty();
			if (!any) return editing ? List.of(piece(sampleArmor().get(kind))) : List.of();
			ItemStack st = p.getItemBySlot(ARMOR[kind]);
			return st.isEmpty() && hideEmpty.on() ? List.of() : List.of(piece(st));
		}

		@Override
		public boolean visible() {
			boolean wanted = kind < 4 ? separate.on() : kind == 4 ? showHeld.on() : showOff.on();
			return wanted && !pieces().isEmpty();
		}

		@Override
		protected int contentW() {
			return Mth.ceil(layoutFor(pieces(), false, false).width * base());
		}

		@Override
		protected int contentH() {
			return Mth.ceil(layoutFor(pieces(), false, false).height * base());
		}

		// where this piece sits inside the strip right now, reverse order and hidden empties included
		private int index(List<Piece> strip) {
			ItemStack mine = Minecraft.getInstance().player.getItemBySlot(ARMOR[kind]);
			for (int i = 0; i < strip.size(); i++) {
				ItemStack st = strip.get(i).stack;
				if (st == mine || st == sampleArmor().get(kind)) return i;
			}
			return kind;
		}

		@Override
		public int px(int sw) {
			if (placed) return super.px(sw);
			if (kind == 4) return sw - 30;
			if (kind == 5) return sw - 56;
			ArmorHudWidget g = ArmorHudWidget.this;
			List<Piece> strip = armor();
			return g.px(sw) + Math.round(layoutFor(strip, false, false).slotX(index(strip), step()) * base() * g.scale);
		}

		@Override
		public int py(int sh) {
			if (placed) return super.py(sh);
			if (kind >= 4) return sh - 70;
			ArmorHudWidget g = ArmorHudWidget.this;
			List<Piece> strip = armor();
			return g.py(sh) + Math.round(layoutFor(strip, false, false).slotY(index(strip), step()) * base() * g.scale);
		}

		@Override
		public void moveTo(float x, float y, int sw, int sh) {
			placed = true;
			super.moveTo(x, y, sw, sh);
		}

		@Override
		protected void content(GuiGraphicsExtractor g) {
			List<Piece> ps = pieces();
			int sw = g.guiWidth();
			paint(g, ps, layoutFor(ps, false, !textRight(px(sw) + w() * scale / 2f, sw)));
		}
	}

	// ---------- drawing ----------

	private void paint(GuiGraphicsExtractor g, List<Piece> ps, Layout lay) {
		float b = base();
		g.pose().scale(b, b);
		int step = step(), len = SLOT + Math.max(0, ps.size() - 1) * step;
		String style = background.get();
		float pulse = (float) (0.5 + 0.5 * Math.sin(Util.getMillis() / 220.0));
		if (style.equals("Hotbar")) strip(g, lay, len);
		else if (style.equals("Glass")) glass(g, lay, len, ps, pulse);

		boolean pulseWarn = !warnStyle.get().equals("Icon"), iconWarn = !warnStyle.get().equals("Pulse");
		for (int i = 0; i < ps.size(); i++) {
			Piece p = ps.get(i);
			int sx = lay.slotX(i, step), sy = lay.slotY(i, step);
			if (style.equals("Tiles")) tile(g, sx, sy, p, pulse);
			else if (style.equals("Hotbar") && p.warn && pulseWarn) g.fill(sx + 2, sy + 2, sx + SLOT - 2, sy + SLOT - 2, ARGB.color(0.15f + 0.3f * pulse, 0xFF3030));
			item(g, p, sx, sy, lay, style.equals("Minimal"));
			if (p.warn && iconWarn) {
				float bob = (float) Math.sin(Util.getMillis() / 160.0) * 1.5f;
				g.text(font(), "!", sx + 15, Math.round(sy - 1 + bob), 0xFFFF4040, true);
			}
		}
	}

	// vanilla's hotbar texture, the body then its 3px right cap, turned on its side for vertical strips
	private static void strip(GuiGraphicsExtractor g, Layout lay, int len) {
		g.pose().pushMatrix();
		if (lay.horizontal) {
			g.pose().translate(lay.stripX, lay.stripY);
		} else {
			g.pose().translate(lay.stripX + SLOT, lay.stripY);
			g.pose().rotate((float) (Math.PI / 2));
		}
		g.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR, 182, 22, 0, 0, 0, 0, len - 3, SLOT);
		g.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR, 182, 22, 179, 0, len - 3, 0, 3, SLOT);
		g.pose().popMatrix();
	}

	// a tile per piece, border tinted by durability and a thin durability line along the bottom
	private void tile(GuiGraphicsExtractor g, int x, int y, Piece p, float pulse) {
		int x0 = x + 1, y0 = y + 1, w = SLOT - 2, h = SLOT - 2;
		fillRound(g, x0, y0, w, h, bgColor.get());
		if (border.on()) lineRound(g, x0, y0, w, h, borderFor(p, pulse));
		if (p.dmg) {
			int tx = x0 + 3, tw = w - 6, by = y0 + h - 3;
			g.fill(tx, by, tx + tw, by + 1, 0x60000000);
			g.fill(tx, by, tx + Math.max(1, Math.round(tw * p.frac)), by + 1, 0xFF000000 | tint(p.frac));
		}
	}

	// one panel behind the whole strip with a drop shadow and a lighter top edge, border follows the worst piece
	private void glass(GuiGraphicsExtractor g, Layout lay, int len, List<Piece> ps, float pulse) {
		int x = lay.stripX, y = lay.stripY, w = lay.horizontal ? len : SLOT, h = lay.horizontal ? SLOT : len;
		fillRound(g, x + 1, y + 2, w, h, 0x40000000);
		fillRound(g, x, y, w, h, bgColor.get());
		g.fill(x + 3, y + 1, x + w - 3, y + 2, 0x26FFFFFF);
		// Kestrel's accent edge, on the side the strip starts from
		if (lay.horizontal) g.fill(x + 1, y + 5, x + 3, y + h - 5, Ui.accent());
		else g.fill(x + 5, y + 1, x + w - 5, y + 3, Ui.accent());
		if (!border.on()) return;
		Piece worst = null;
		for (Piece p : ps) if (p.dmg && (worst == null || p.frac < worst.frac)) worst = p;
		lineRound(g, x, y, w, h, worst == null ? borderColor.get() : borderFor(worst, pulse));
	}

	private int borderFor(Piece p, float pulse) {
		int c = borderColor.get();
		if (borderTint.get().equals("Durability") && p.dmg) c = c & 0xFF000000 | tint(p.frac);
		if (p.warn && !warnStyle.get().equals("Icon")) c = ARGB.srgbLerp(0.4f + 0.6f * pulse, c, 0xFFFF3030);
		return c;
	}

	private void item(GuiGraphicsExtractor g, Piece p, int sx, int sy, Layout lay, boolean minimal) {
		if (p.stack.isEmpty()) return;
		int ix = sx + INSET, iy = sy + INSET;
		g.item(p.stack, ix, iy);
		if (bar.on()) g.itemDecorations(font(), p.stack, ix, iy);
		if (p.text.isEmpty()) return;
		int w = font().width(p.text), tx, ty;
		if (lay.horizontal) {
			tx = sx + SLOT / 2 - w / 2;
			ty = lay.above ? sy - BAND + 1 : sy + SLOT + 1;
		} else {
			tx = lay.left ? sx - 3 - w : sx + SLOT + 3;
			ty = sy + 7;
		}
		// minimal has nothing behind the numbers, so it always gets the shadow
		g.text(font(), p.text, tx, ty, noColors.on() ? 0xFFFFFFFF : 0xFF000000 | tint(p.frac), minimal || textShadow.on());
	}

	// green, yellow, red as it wears down (rgb only)
	private static int tint(float f) {
		return (f > 0.5f ? ARGB.srgbLerp((f - 0.5f) * 2, 0xFFFFE066, 0xFF5BE37D) : ARGB.srgbLerp(f * 2, 0xFFFF5555, 0xFFFFE066)) & 0xFFFFFF;
	}

	// 2px cut corners, at item scale the round sprites the menu uses smear into blobs
	private static void fillRound(GuiGraphicsExtractor g, int x, int y, int w, int h, int c) {
		g.fill(x + 2, y, x + w - 2, y + h, c);
		g.fill(x, y + 2, x + 2, y + h - 2, c);
		g.fill(x + w - 2, y + 2, x + w, y + h - 2, c);
		g.fill(x + 1, y + 1, x + 2, y + 2, c);
		g.fill(x + w - 2, y + 1, x + w - 1, y + 2, c);
		g.fill(x + 1, y + h - 2, x + 2, y + h - 1, c);
		g.fill(x + w - 2, y + h - 2, x + w - 1, y + h - 1, c);
	}

	private static void lineRound(GuiGraphicsExtractor g, int x, int y, int w, int h, int c) {
		g.fill(x + 2, y, x + w - 2, y + 1, c);
		g.fill(x + 2, y + h - 1, x + w - 2, y + h, c);
		g.fill(x, y + 2, x + 1, y + h - 2, c);
		g.fill(x + w - 1, y + 2, x + w, y + h - 2, c);
		g.fill(x + 1, y + 1, x + 2, y + 2, c);
		g.fill(x + w - 2, y + 1, x + w - 1, y + 2, c);
		g.fill(x + 1, y + h - 2, x + 2, y + h - 1, c);
		g.fill(x + w - 2, y + h - 2, x + w - 1, y + h - 1, c);
	}

	// ---------- uku import ----------

	// reads config/ukus-armor-hud.toml (local file only) onto these settings. same 22/20/98 geometry as uku, so
	// its offsets carry over as they are
	private void importUku() {
		Path f = FabricLoader.getInstance().getConfigDir().resolve("ukus-armor-hud.toml");
		if (!Files.isRegularFile(f)) {
			toast("Couldn't find ukus-armor-hud.toml in your config folder");
			return;
		}
		Map<String, String> v = new HashMap<>();
		try {
			// flat key = value lines, section headers and comments have no '=' or start with #
			for (String raw : Files.readAllLines(f)) {
				String line = raw.strip();
				int eq = line.indexOf('=');
				if (line.startsWith("#") || eq <= 0) continue;
				String val = line.substring(eq + 1).strip();
				if (val.length() >= 2 && val.startsWith("\"") && val.endsWith("\"")) val = val.substring(1, val.length() - 1);
				v.put(line.substring(0, eq).strip(), val);
			}
		} catch (IOException e) {
			Kestrel.LOG.warn("uku toml unreadable: {}", e.getMessage());
			toast("Couldn't read Uku's Armor HUD config");
			return;
		}

		layout.set(up(v, "orientation", "HORIZONTAL").startsWith("VERT") ? "Vertical" : "Horizontal");
		reverse.set(bool(v.get("reversed"), false));
		hideEmpty.set(up(v, "widgetShown", "NOT_EMPTY").equals("NOT_EMPTY"));
		room.set(!up(v, "offhandSlotBehavior", "ADHERE").equals("ALWAYS_IGNORE"));
		String style = up(v, "style", "HOTBAR");
		background.set(style.equals("NONE") ? "None" : style.startsWith("ROUNDED") ? "Tiles" : "Hotbar");
		border.set(true);

		String d = up(v, "durabilityDisplay", "BAR");
		bar.set(true);
		hideNumbers.set(d.equals("BAR"));
		percent.set(d.contains("PERCENT"));
		// uku draws hand durability on the hotbar itself, here that would be two extra boxes, so they stay off
		showHeld.set(false);
		showOff.set(false);

		lowWarn.set(bool(v.get("warningShown"), true));
		warnSound.set(bool(v.get("playBreakSound"), true));
		handWarn.set(bool(v.get("mainHandDurability"), false) || bool(v.get("offHandDurability"), false));
		warnValue.set(num(v.get("minDurabilityValue"), 5));
		// uku stores the percent as 0-1
		warnBelow.set(num(v.get("minDurabilityPercentage"), 0.1) * 100);

		boolean hotbar = up(v, "anchor", "HOTBAR").equals("HOTBAR");
		boolean right = up(v, "side", "LEFT").equals("RIGHT");
		if (hotbar) {
			attach.set(right ? "Right" : "Left");
			// uku's x pushes away from the hotbar and its y pushes up, ours are plain screen directions
			double ux = Math.round(num(v.get("offsetX"), 0)), uy = Math.round(num(v.get("offsetY"), 0));
			offX.set(right ? ux : -ux);
			offY.set(-uy);
		} else {
			attach.set("Off");
			offX.set(0.0);
			offY.set(0.0);
		}
		separate.set(false);
		spacing.set(20.0);
		size.set(1.0);
		scale = 1;
		for (Part p : loose) {
			p.placed = false;
			p.scale = 1;
		}
		Config.dirty();
		Kestrel.LOG.info("imported uku armor hud: {}", v);
		toast(hotbar ? "Imported Uku's Armor HUD settings" : "Imported Uku's settings, its non hotbar anchors aren't supported so drag it into place");
	}

	private static String up(Map<String, String> v, String k, String def) {
		return v.getOrDefault(k, def).toUpperCase(Locale.ROOT);
	}

	private static double num(String s, double def) {
		if (s == null) return def;
		try {
			return Double.parseDouble(s);
		} catch (NumberFormatException e) {
			return def;
		}
	}

	private static boolean bool(String s, boolean def) {
		return s == null ? def : s.equalsIgnoreCase("true");
	}

	private static void toast(String msg) {
		Notice.show("Armor HUD", msg, 3500);
	}

	// ---------- config ----------

	@Override
	public JsonObject save() {
		JsonObject o = super.save();
		JsonObject ps = new JsonObject();
		for (Part p : loose) {
			if (!p.placed) continue;
			JsonObject j = new JsonObject();
			j.addProperty("x", p.fx);
			j.addProperty("y", p.fy);
			j.addProperty("scale", p.scale);
			ps.add(KEYS[p.kind], j);
		}
		o.add("parts", ps);
		return o;
	}

	@Override
	public void load(JsonObject o) {
		super.load(o);
		JsonObject ps = o.getAsJsonObject("parts");
		for (Part p : loose) {
			JsonObject j = ps == null ? null : ps.getAsJsonObject(KEYS[p.kind]);
			p.placed = j != null;
			if (j == null) {
				p.scale = 1;
				continue;
			}
			p.fx = Mth.clamp(j.get("x").getAsFloat(), 0, 1);
			p.fy = Mth.clamp(j.get("y").getAsFloat(), 0, 1);
			p.scale = Mth.clamp(j.get("scale").getAsFloat(), 0.5f, 3f);
		}
	}

	// ---------- samples for the editor ----------

	private static List<ItemStack> sampleArmor() {
		if (sampleArmor == null) {
			sampleArmor = List.of(new ItemStack(Items.NETHERITE_HELMET), new ItemStack(Items.NETHERITE_CHESTPLATE),
				new ItemStack(Items.NETHERITE_LEGGINGS), new ItemStack(Items.NETHERITE_BOOTS));
		}
		return sampleArmor;
	}

	private static ItemStack sampleHeld() {
		if (sampleHeld == null) sampleHeld = new ItemStack(Items.DIAMOND_SWORD);
		return sampleHeld;
	}

	private static ItemStack sampleOff() {
		if (sampleOff == null) sampleOff = new ItemStack(Items.SHIELD);
		return sampleOff;
	}
}
