package io.github.relozia.shrike.visual;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.relozia.shrike.Shrike;
import io.github.relozia.shrike.module.Category;
import io.github.relozia.shrike.module.Module;
import io.github.relozia.shrike.setting.Setting;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// how the world looks: sky, glint, dropped items, armor, overlays, blur, fireflies, hit particles
public final class WorldVisuals {
	private WorldVisuals() {}

	// ---------- sky & ambience ----------

	public static class Sky extends Module {
		static Sky INSTANCE;

		private final Setting.Toggle timeOn = add(new Setting.Toggle("Override Time", true));
		private final Setting.Slider time = add(new Setting.Slider("Time Of Day", 6000, 0, 24000, 100, " ticks")).when(timeOn::on);
		private final Setting.Toggle cycle = add(new Setting.Toggle("Cycle Time", false)).when(timeOn::on);
		private final Setting.Slider speed = add(new Setting.Slider("Cycle Speed", 200, 10, 2400, 10, "x")).when(() -> timeOn.on() && cycle.on());
		private final Setting.Choice weather = add(new Setting.Choice("Weather", "Server", "Server", "Clear", "Rain", "Thunder"));
		private final Setting.Toggle barriers = add(new Setting.Toggle("Rain Through Barriers", true)).when(this::forcing);
		private final Setting.Toggle skyOn = add(new Setting.Toggle("Custom Sky Color", false));
		private final Setting.Color sky = add(new Setting.Color("Sky Color", 0xFF7BA4FF, 0xFF7BA4FF, 0xFF007DFF, 0xFFE0703A, 0xFFC879E8, 0xFF2EC4B6, 0xFF101216)).when(skyOn::on);
		private final Setting.Color netherSky = add(new Setting.Color("Nether Sky Color", 0xFF660000, 0xFF660000, 0xFF2A1A12, 0xFFC879E8, 0xFF101216)).when(skyOn::on);
		private final Setting.Color endSky = add(new Setting.Color("End Sky Color", 0xFF411E5A, 0xFF411E5A, 0xFF101216, 0xFF2EC4B6, 0xFF7BA4FF)).when(skyOn::on);
		private final Setting.Toggle horizonOn = add(new Setting.Toggle("Custom Horizon Color", false));
		private final Setting.Color horizon = add(new Setting.Color("Horizon Color", 0xFFC0D8FF, 0xFFC0D8FF, 0xFFE0703A, 0xFFC879E8, 0xFF2EC4B6)).when(horizonOn::on);
		private final Setting.Toggle sunsetOn = add(new Setting.Toggle("Custom Sunset Color", false));
		private final Setting.Color sunset = add(new Setting.Color("Sunset Color", 0xCCFF6A3D, 0xCCFF6A3D, 0xCCC879E8, 0xCCE5484D, 0xCC2EC4B6).withAlpha()).when(sunsetOn::on);
		private final Setting.Slider stars = add(new Setting.Slider("Star Brightness", 100, 0, 300, 10, "%"));
		private final Setting.Toggle dayStars = add(new Setting.Toggle("Stars In Daytime", false));
		private final Setting.Slider sun = add(new Setting.Slider("Sun Size", 100, 25, 300, 5, "%"));
		private final Setting.Slider moon = add(new Setting.Slider("Moon Size", 100, 25, 300, 5, "%"));
		private final Setting.Toggle fogOn = add(new Setting.Toggle("Custom Fog Color", false));
		private final Setting.Color fog = add(new Setting.Color("Fog Color", 0xFFC7C7C7, 0xFFC7C7C7, 0xFFC0D8FF, 0xFFE0703A, 0xFFC879E8, 0xFF2EC4B6, 0xFF101216)).when(fogOn::on);
		private final Setting.Toggle waterFogOn = add(new Setting.Toggle("Custom Water Fog Color", false));
		private final Setting.Color waterFog = add(new Setting.Color("Water Fog Color", 0xFF050533, 0xFF050533, 0xFF0B3D2E, 0xFF2EC4B6, 0xFF101216)).when(waterFogOn::on);
		private final Setting.Toggle cloudOn = add(new Setting.Toggle("Custom Cloud Color", false));
		private final Setting.Color cloud = add(new Setting.Color("Cloud Color", 0xFFFFFFFF, 0xFFFFFFFF, 0xFFE0703A, 0xFFC879E8, 0xFF2EC4B6, 0xFF404553)).when(cloudOn::on);
		private final Setting.Toggle grassOn = add(new Setting.Toggle("Custom Grass Color", false));
		private final Setting.Color grass = add(new Setting.Color("Grass Color", 0xFF660000, 0xFF660000, 0xFF57D163, 0xFF91BD59, 0xFFC879E8, 0xFFF5A524)).when(grassOn::on);
		private final Setting.Toggle foliageOn = add(new Setting.Toggle("Custom Foliage Color", false));
		private final Setting.Color foliage = add(new Setting.Color("Foliage Color", 0xFF660000, 0xFF660000, 0xFF57D163, 0xFF77AB2F, 0xFFC879E8, 0xFFF5A524)).when(foliageOn::on);
		private final Setting.Toggle waterOn = add(new Setting.Toggle("Custom Water Color", false));
		private final Setting.Color water = add(new Setting.Color("Water Color", 0xFF660000, 0xFF660000, 0xFF3F76E4, 0xFF2EC4B6, 0xFF57D163, 0xFFC879E8)).when(waterOn::on);
		// where the cycle is right now, starts from Time Of Day whenever cycling turns on
		private double cycled = -1;
		// block tints get baked into compiled chunks. a change waits a few ticks to settle so dragging a color
		// around doesn't rebuild every chunk twenty times a second
		private int tints, built, settle;

		public Sky() {
			super("sky_ambience", "Sky & Ambience", Category.VISUAL, '\ue216', "Your own time of day and weather, and sky, fog, grass and water colors. Only you see it.");
			INSTANCE = this;
		}

		private boolean forcing() {
			return weather.get().equals("Rain") || weather.get().equals("Thunder");
		}

		@Override
		public void tick(Minecraft mc) {
			if (timeOn.on() && cycle.on()) {
				if (cycled < 0) cycled = time.get();
				cycled = (cycled + speed.get()) % 24_000;
			} else {
				cycled = -1;
			}
			int h = tintHash();
			if (h != tints) {
				tints = h;
				settle = 6;
			}
			if (settle > 0 && --settle == 0 && tints != built) rebuild(mc, tints);
		}

		@Override
		protected void disable() {
			cycled = -1;
			tints = 0;
			if (built != 0) rebuild(Minecraft.getInstance(), 0);
		}

		private int tintHash() {
			int h = 1;
			if (grassOn.on()) h = h * 31 + grass.get();
			if (foliageOn.on()) h = h * 31 + foliage.get() + 7;
			if (waterOn.on()) h = h * 31 + water.get() + 13;
			return h == 1 ? 0 : h;
		}

		private void rebuild(Minecraft mc, int h) {
			built = h;
			if (mc.level != null) mc.levelExtractor.allChanged();
		}

		// the whole sky (sun angle, colors, stars, sky light) is sampled from the world clock, so pinning the clock
		// for drawing moves everything together. the day count stays so moon phases don't jump
		public static long clock(long ticks) {
			Sky s = INSTANCE;
			if (s == null || !s.on() || !s.timeOn.on()) return ticks;
			long t = Math.round(s.cycled >= 0 ? s.cycled : s.time.get()) % 24_000;
			return Math.floorDiv(ticks, 24_000) * 24_000 + t;
		}

		public static float rain(float vanilla) {
			Sky s = INSTANCE;
			if (s == null || !s.on()) return vanilla;
			return switch (s.weather.get()) {
				case "Clear" -> 0;
				case "Rain", "Thunder" -> 1;
				default -> vanilla;
			};
		}

		public static float thunder(float vanilla) {
			Sky s = INSTANCE;
			if (s == null || !s.on()) return vanilla;
			return switch (s.weather.get()) {
				case "Clear", "Rain" -> 0;
				case "Thunder" -> 1;
				default -> vanilla;
			};
		}

		// forced rain in a desert or the nether still has to fall somewhere
		public static boolean forcedRain() {
			return INSTANCE != null && INSTANCE.on() && INSTANCE.forcing();
		}

		// barrier ceilings over lobbies and arenas would otherwise keep forced rain off the whole map
		public static boolean throughBarriers() {
			return forcedRain() && INSTANCE.barriers.on();
		}

		public static float sunSize() {
			return INSTANCE != null && INSTANCE.on() ? INSTANCE.sun.get().floatValue() / 100 : 1;
		}

		public static float moonSize() {
			return INSTANCE != null && INSTANCE.on() ? INSTANCE.moon.get().floatValue() / 100 : 1;
		}

		// replaces the overworld horizon outright, sunset blending and all. -1 leaves it alone
		public static int horizon() {
			Sky s = INSTANCE;
			return s != null && s.on() && s.horizonOn.on() ? s.horizon.get() | 0xFF000000 : -1;
		}

		// 0 grass, 1 foliage, 2 water. -1 is vanilla
		public static int tint(int which) {
			Sky s = INSTANCE;
			if (s == null || !s.on()) return -1;
			return switch (which) {
				case 0 -> s.grassOn.on() ? s.grass.get() | 0xFF000000 : -1;
				case 1 -> s.foliageOn.on() ? s.foliage.get() | 0xFF000000 : -1;
				default -> s.waterOn.on() ? s.water.get() | 0xFF000000 : -1;
			};
		}

		@SuppressWarnings("unchecked")
		public static <V> V attribute(EnvironmentAttribute<V> a, V vanilla) {
			Sky s = INSTANCE;
			if (s == null || !s.on()) return vanilla;
			if (a == EnvironmentAttributes.SKY_COLOR && s.skyOn.on()) {
				var level = Minecraft.getInstance().level;
				var dim = level == null ? Level.OVERWORLD : level.dimension();
				int c = dim == Level.NETHER ? s.netherSky.get() : dim == Level.END ? s.endSky.get() : s.sky.get();
				return (V) (Integer) (c & 0xFFFFFF);
			}
			if (a == EnvironmentAttributes.FOG_COLOR && s.fogOn.on()) return (V) (Integer) (s.fog.get() & 0xFFFFFF);
			if (a == EnvironmentAttributes.WATER_FOG_COLOR && s.waterFogOn.on()) return (V) (Integer) (s.waterFog.get() & 0xFFFFFF);
			if (a == EnvironmentAttributes.CLOUD_COLOR && s.cloudOn.on()) return (V) (Integer) s.cloud.get().intValue();
			if (a == EnvironmentAttributes.SUNRISE_SUNSET_COLOR && s.sunsetOn.on()) {
				// vanilla's alpha is the fade in and out around sunset, the setting's own alpha only caps it
				int v = (Integer) vanilla, c = s.sunset.get();
				if (ARGB.alpha(v) == 0) return vanilla;
				return (V) (Integer) ARGB.color(ARGB.alpha(v) * ARGB.alpha(c) / 255, c);
			}
			if (a == EnvironmentAttributes.STAR_BRIGHTNESS) {
				float b = (Float) vanilla;
				if (s.dayStars.on()) b = Math.max(b, 0.5f);
				return (V) (Float) (b * s.stars.get().floatValue() / 100);
			}
			return vanilla;
		}
	}

	// ---------- glint ----------

	public static class Glint extends Module {
		private static final Logger LOG = LoggerFactory.getLogger("shrike");
		private static final Identifier ITEM = Identifier.withDefaultNamespace("textures/misc/enchanted_glint_item.png");
		private static final Identifier ARMOR = Identifier.withDefaultNamespace("textures/misc/enchanted_glint_armor.png");
		static Glint INSTANCE;

		private final Setting.Color item = add(new Setting.Color("Item Glint Color", 0xFF8040CC, 0xFF8040CC, 0xFFC879E8, 0xFFE0703A, 0xFFE5484D, 0xFF5B8DEF, 0xFF2EC4B6, 0xFF57D163, 0xFFFFFFFF));
		private final Setting.Color armor = add(new Setting.Color("Armor Glint Color", 0xFF8040CC, 0xFF8040CC, 0xFFC879E8, 0xFFE0703A, 0xFFE5484D, 0xFF5B8DEF, 0xFF2EC4B6, 0xFF57D163, 0xFFFFFFFF));
		private final Setting.Toggle pots = add(new Setting.Toggle("Shiny Potions", true));
		private final Setting.Toggle thrown = add(new Setting.Toggle("Shiny Splash & Lingering", true));
		private final Setting.Toggle arrows = add(new Setting.Toggle("Shiny Tipped Arrows", false));
		private final Setting.Toggle apple = add(new Setting.Toggle("No Enchanted Apple Glint", false));
		private int paintedItem, paintedArmor;

		public Glint() {
			super("enchant_glint", "Enchant Glint", Category.VISUAL, '\ue357', "Recolors the enchantment glint, and makes potions shiny again.");
			INSTANCE = this;
		}

		// only potions that actually do something, a water bottle stays plain like it always was
		public static boolean foil(ItemStack s, boolean vanilla) {
			Glint g = INSTANCE;
			if (g == null || !g.on()) return vanilla;
			if (g.apple.on() && s.is(Items.ENCHANTED_GOLDEN_APPLE)) return false;
			if (vanilla) return true;
			boolean want = s.is(Items.POTION) ? g.pots.on()
				: s.is(Items.SPLASH_POTION) || s.is(Items.LINGERING_POTION) ? g.thrown.on()
				: s.is(Items.TIPPED_ARROW) && g.arrows.on();
			if (!want) return false;
			PotionContents pc = s.get(DataComponents.POTION_CONTENTS);
			return pc != null && pc.getAllEffects().iterator().hasNext();
		}

		@Override
		public void tick(Minecraft mc) {
			if (item.get() != paintedItem) paintedItem = paint(mc, ITEM, item.get());
			if (armor.get() != paintedArmor) paintedArmor = paint(mc, ARMOR, armor.get());
		}

		// back to the pack's own textures, next use loads them fresh
		@Override
		protected void disable() {
			Minecraft mc = Minecraft.getInstance();
			mc.getTextureManager().release(ITEM);
			mc.getTextureManager().release(ARMOR);
			paintedItem = paintedArmor = 0;
		}

		// the glint texture is a purple pattern, its brightness is kept and the color swapped. done on the
		// pack's own texture so a resource pack glint keeps its pattern
		private static int paint(Minecraft mc, Identifier id, int c) {
			try (InputStream in = mc.getResourceManager().open(id)) {
				NativeImage img = NativeImage.read(in);
				for (int y = 0; y < img.getHeight(); y++) for (int x = 0; x < img.getWidth(); x++) {
					int p = img.getPixel(x, y);
					float l = Math.max(ARGB.red(p), Math.max(ARGB.green(p), ARGB.blue(p))) / 255f;
					img.setPixel(x, y, ARGB.color(ARGB.alpha(p) * ARGB.alpha(c) / 255, Math.round(ARGB.red(c) * l), Math.round(ARGB.green(c) * l), Math.round(ARGB.blue(c) * l)));
				}
				mc.getTextureManager().register(id, new DynamicTexture(() -> "shrike glint", img));
			} catch (IOException e) {
				LOG.warn("couldn't recolor {}: {}", id, e.getMessage());
			}
			return c;
		}
	}

	// ---------- dropped items ----------

	public static class DroppedItems extends Module {
		public static final RenderStateDataKey<Float> SCALE = RenderStateDataKey.create(() -> "shrike item scale");
		public static final RenderStateDataKey<Boolean> GROUND = RenderStateDataKey.create(() -> "shrike item on ground");
		static DroppedItems INSTANCE;

		private final Setting.Choice mode = add(new Setting.Choice("Style", "Flat", "Vanilla", "Flat", "Facing You"));
		private final Setting.Toggle noSpin = add(new Setting.Toggle("No Ground Spin", true)).when(this::flat);
		private final Setting.Toggle noBounce = add(new Setting.Toggle("No Ground Bounce", true)).when(this::flat);
		private final Setting.Toggle layFlat = add(new Setting.Toggle("Lay Flat", true)).when(this::flat);
		private final Setting.Slider size = add(new Setting.Slider("Size", 100, 50, 200, 10, "%"));
		private final Setting.Items big = add(new Setting.Items("Bigger Items", "minecraft:golden_apple", "minecraft:enchanted_golden_apple",
			"minecraft:totem_of_undying", "minecraft:ender_pearl", "minecraft:diamond", "minecraft:netherite_ingot"));
		private final Setting.Slider bigSize = add(new Setting.Slider("Bigger Size", 160, 100, 300, 10, "%"));

		public DroppedItems() {
			super("dropped_items", "Dropped Items", Category.VISUAL, '\ue129', "Items on the ground lie flat or face you, and important ones show bigger.");
			INSTANCE = this;
		}

		private boolean flat() {
			return mode.get().equals("Flat");
		}

		public static boolean active() {
			return INSTANCE != null && INSTANCE.on();
		}

		public static String style() {
			return INSTANCE.mode.get();
		}

		public static boolean noSpin() {
			return INSTANCE.noSpin.on();
		}

		public static boolean noBounce() {
			return INSTANCE.noBounce.on();
		}

		public static boolean layFlat() {
			return INSTANCE.layFlat.on();
		}

		public static float scale(ItemStack s) {
			if (!active()) return 1;
			float k = INSTANCE.size.get().floatValue() / 100;
			return INSTANCE.big.has(s.getItem()) ? k * INSTANCE.bigSize.get().floatValue() / 100 : k;
		}
	}

	// ---------- hide armor ----------

	public static class HideArmor extends Module {
		public static final RenderStateDataKey<Boolean> OWN = RenderStateDataKey.create(() -> "shrike own player");
		static HideArmor INSTANCE;

		private final Setting.Multi pieces = add(new Setting.Multi("Hide", List.of("Helmet", "Chestplate", "Leggings", "Boots"), "Helmet", "Chestplate", "Leggings", "Boots"));

		public HideArmor() {
			super("hide_armor", "Hide Armor", Category.VISUAL, '\ue15a', "Hides your own armor on your player. Only you see this.");
			INSTANCE = this;
		}

		public static boolean hidden(EquipmentSlot slot) {
			if (INSTANCE == null || !INSTANCE.on()) return false;
			return INSTANCE.pieces.has(switch (slot) {
				case HEAD -> "Helmet";
				case CHEST -> "Chestplate";
				case LEGS -> "Leggings";
				default -> "Boots";
			});
		}
	}

	// ---------- no render ----------

	public static class NoRender extends Module {
		static NoRender INSTANCE;

		private final Setting.Multi what = add(new Setting.Multi("Hide", List.of("Totem Animation", "Pumpkin Overlay", "Powder Snow Overlay",
			"Water Overlay", "Vignette", "Portal Overlay", "Nausea Overlay", "Crosshair", "Scoreboard", "Explosion Particles", "Glow", "Name Tags"),
			"Pumpkin Overlay", "Explosion Particles"));
		private final Setting.Multi fog = add(new Setting.Multi("Remove Fog", List.of("World", "Water", "Lava", "Powder Snow")));

		public NoRender() {
			super("no_render", "No Render", Category.VISUAL, '\ue0bb', "Turns off overlays, fog and effects that get in the way.");
			INSTANCE = this;
		}

		public static boolean hides(String s) {
			return INSTANCE != null && INSTANCE.on() && INSTANCE.what.has(s);
		}

		public static boolean noFog(String s) {
			return INSTANCE != null && INSTANCE.on() && INSTANCE.fog.has(s);
		}

		public static boolean hidesParticle(ParticleOptions p) {
			return hides("Explosion Particles") && (p.getType() == ParticleTypes.EXPLOSION || p.getType() == ParticleTypes.EXPLOSION_EMITTER);
		}
	}

	// ---------- motion blur ----------

	public static class MotionBlur extends Module {
		static MotionBlur INSTANCE;

		private final Setting.Choice strength = add(new Setting.Choice("Strength", "Medium", "Low", "Medium", "High"));

		public MotionBlur() {
			super("motion_blur", "Motion Blur", Category.VISUAL, '\ue1b0', "Cosmetic motion blur on the world. The HUD and menus stay sharp.");
			INSTANCE = this;
		}

		// null when off. each strength is its own post effect, uniforms in post effect files are fixed
		public static Identifier effect() {
			MotionBlur m = INSTANCE;
			if (m == null || !m.on()) return null;
			return Shrike.id("motion_blur_" + m.strength.get().toLowerCase(java.util.Locale.ROOT));
		}
	}

	// ---------- particles ----------

	public static class Particles extends Module {
		static Particles INSTANCE;

		private final Setting.Slider crits = add(new Setting.Slider("Crit Amount", 2, 0, 5, 1, "x"));
		private final Setting.Slider sharp = add(new Setting.Slider("Sharpness Amount", 2, 0, 5, 1, "x"));
		private final Setting.Toggle alwaysCrit = add(new Setting.Toggle("Always Crit Particles", false));
		private final Setting.Toggle alwaysSharp = add(new Setting.Toggle("Always Sharpness Particles", false));
		private final Setting.Toggle critOn = add(new Setting.Toggle("Custom Crit Color", false));
		private final Setting.Color critColor = add(new Setting.Color("Crit Color", 0xFFFF5555, 0xFFFF5555, 0xFFF5A524, 0xFFC879E8, 0xFF57D163, 0xFFFFFFFF)).when(critOn::on);
		private final Setting.Toggle sharpOn = add(new Setting.Toggle("Custom Sharpness Color", false));
		private final Setting.Color sharpColor = add(new Setting.Color("Sharpness Color", 0xFF55FFFF, 0xFF55FFFF, 0xFF5B8DEF, 0xFFC879E8, 0xFF57D163, 0xFFFFFFFF)).when(sharpOn::on);
		// entity id to when it last got particles from us, so a server crit right after our own "always" burst
		// doesn't double them up
		private final Map<Integer, Long> last = new HashMap<>();

		public Particles() {
			super("particle_changer", "Particle Changer", Category.VISUAL, '\ue47e', "More or fewer crit and sharpness particles, crits on every hit, your own colors. Only you see them.");
			INSTANCE = this;
			AttackEntityCallback.EVENT.register((p, level, hand, e, hit) -> {
				if (on() && level.isClientSide() && e instanceof LivingEntity) {
					if (alwaysCrit.on()) burst(e, ParticleTypes.CRIT, Math.max(1, crits.get().intValue()));
					if (alwaysSharp.on()) burst(e, ParticleTypes.ENCHANTED_HIT, Math.max(1, sharp.get().intValue()));
				}
				return InteractionResult.PASS;
			});
		}

		private void burst(Entity e, ParticleOptions type, int n) {
			long now = Util.getMillis();
			Long was = last.get(e.getId() * 2 + (type == ParticleTypes.CRIT ? 0 : 1));
			if (was != null && now - was < 50) return;
			last.put(e.getId() * 2 + (type == ParticleTypes.CRIT ? 0 : 1), now);
			if (last.size() > 256) last.clear();
			for (int i = 0; i < n; i++) Minecraft.getInstance().particleEngine.createTrackingEmitter(e, type);
		}

		@Override
		protected void disable() {
			last.clear();
		}

		// the server says a crit (4) or a magic crit (5) happened, this replaces vanilla's single emitter. 0 means none
		public static boolean hitEffect(Entity e, ParticleOptions type) {
			Particles p = INSTANCE;
			if (p == null || !p.on()) return false;
			p.burst(e, type, (type == ParticleTypes.CRIT ? p.crits : p.sharp).get().intValue());
			return true;
		}

		// shaded a little per particle like vanilla, one flat color looks like a sticker
		public static void color(SingleQuadParticle q, boolean magic, RandomSource r) {
			Particles p = INSTANCE;
			if (p == null || !p.on() || !(magic ? p.sharpOn : p.critOn).on()) return;
			int c = (magic ? p.sharpColor : p.critColor).get();
			float k = 0.85f + r.nextFloat() * 0.15f;
			q.setColor(ARGB.redFloat(c) * k, ARGB.greenFloat(c) * k, ARGB.blueFloat(c) * k);
		}
	}
}
