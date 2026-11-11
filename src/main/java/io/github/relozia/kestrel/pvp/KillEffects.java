package io.github.relozia.kestrel.pvp;

import io.github.relozia.kestrel.Kestrel;
import io.github.relozia.kestrel.module.Category;
import io.github.relozia.kestrel.module.Module;
import io.github.relozia.kestrel.setting.Setting;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import org.jspecify.annotations.Nullable;

// an effect where someone you just hit dies. only you see it
// servers disagree a lot about how a death looks from the outside: plenty hide other players' health (it reads
// 20 forever) and minigames respawn you instantly without a real death. so any of these counts, for anyone you
// hit in the last 3 seconds: the death animation event, the player death sound on or next to them, a chat line
// naming both of you, or their health hitting 0
// issues:
//   - a kill message that doesn't name you (some servers only say "X died") isn't a kill unless one of the others fires
public class KillEffects extends Module {
	private static final long WINDOW = 3000;
	static KillEffects INSTANCE;

	private static final class Hit {
		String name;
		double x, y, z;
		float height;
		long at;
	}

	private final Setting.Choice effect = add(new Setting.Choice("Effect", "Lightning", "Lightning", "Blood", "Flames", "Hearts", "Totem", "Explosion", "None"));
	private final Setting.Slider amount = add(new Setting.Slider("Particle Amount", 100, 25, 300, 25, "%"));
	private final Setting.Choice sound = add(new Setting.Choice("Sound", "Effect Default", "Effect Default", "Level Up", "Orb", "Anvil", "None"));
	private final Setting.Slider volume = add(new Setting.Slider("Volume", 60, 5, 100, 5, "%"));
	private final Setting.Toggle flash = add(new Setting.Toggle("Screen Flash", false));
	private final Setting.Color flashColor = add(new Setting.Color("Flash Color", 0xFFFFFFFF, 0xFFFFFFFF, 0xFFE5484D, 0xFFE0703A, 0xFFF5A524, 0xFF5B8DEF, 0xFFC879E8));
	private final Setting.Slider flashStrength = add(new Setting.Slider("Flash Strength", 40, 10, 100, 5, "%"));
	private final Setting.Toggle mobs = add(new Setting.Toggle("Mobs Too", false));

	private final Map<Integer, Hit> hits = new HashMap<>();
	private long flashAt;
	private int boltId = -1000;

	public KillEffects() {
		super("Kill Effects", Category.COMBAT, '\ue1b4', "An effect where someone you just hit dies. Only you see it.");
		INSTANCE = this;
		AttackEntityCallback.EVENT.register((p, level, hand, e, hr) -> {
			if (on() && level.isClientSide() && p == Minecraft.getInstance().player) hit(e);
			return InteractionResult.PASS;
		});
		HudElementRegistry.addLast(Kestrel.id("kill_flash"), (g, dt) -> {
			long age = Util.getMillis() - flashAt;
			if (!on() || flashAt == 0 || age > 400) return;
			float a = flashStrength.get().floatValue() / 100 * (1 - age / 400f);
			g.fill(0, 0, g.guiWidth(), g.guiHeight(), ARGB.color(Math.round(a * 255), flashColor.get() & 0xFFFFFF));
		});
	}

	// every swing at someone counts, not only ones that visibly land: a kill hit often doesn't get to flash red
	private void hit(Entity e) {
		if (!(e instanceof LivingEntity l) || e == Minecraft.getInstance().player) return;
		if (!(e instanceof Player) && !mobs.on()) return;
		Hit h = hits.computeIfAbsent(e.getId(), id -> new Hit());
		h.name = e instanceof Player p ? p.getGameProfile().name() : null;
		h.x = e.getX();
		h.y = e.getY();
		h.z = e.getZ();
		h.height = l.getBbHeight();
		h.at = Util.getMillis();
	}

	// ---------- the four signals ----------

	// entity event 3, sent to everyone around when something dies
	public static void entityEvent(@Nullable Entity e, byte id) {
		if (INSTANCE != null && INSTANCE.on() && e != null && id == 3) INSTANCE.fire(e.getId());
	}

	// the death sound attached to the entity itself
	public static void entitySound(Identifier sound, int entityId) {
		if (INSTANCE != null && INSTANCE.on() && sound.equals(SoundEvents.PLAYER_DEATH.location())) INSTANCE.fire(entityId);
	}

	// or played at a spot, then whoever we hit closest to it (within 3 blocks)
	public static void positionalSound(Identifier sound, double x, double y, double z) {
		KillEffects k = INSTANCE;
		if (k == null || !k.on() || !sound.equals(SoundEvents.PLAYER_DEATH.location())) return;
		for (Map.Entry<Integer, Hit> e : k.hits.entrySet()) {
			Hit h = e.getValue();
			if ((h.x - x) * (h.x - x) + (h.y - y) * (h.y - y) + (h.z - z) * (h.z - z) < 9) {
				k.fire(e.getKey());
				return;
			}
		}
	}

	// "Steve was slain by You", "You killed Steve", "Steve > You": both names in one line
	public static void chat(String line) {
		KillEffects k = INSTANCE;
		Minecraft mc = Minecraft.getInstance();
		if (k == null || !k.on() || mc.player == null || k.hits.isEmpty()) return;
		String low = line.toLowerCase(Locale.ROOT), me = mc.player.getGameProfile().name().toLowerCase(Locale.ROOT);
		if (!low.contains(me)) return;
		for (Map.Entry<Integer, Hit> e : k.hits.entrySet()) {
			String n = e.getValue().name;
			if (n != null && !n.equalsIgnoreCase(me) && low.contains(n.toLowerCase(Locale.ROOT))) {
				k.fire(e.getKey());
				return;
			}
		}
	}

	@Override
	public void tick(Minecraft mc) {
		if (mc.level == null) {
			hits.clear();
			return;
		}
		long now = Util.getMillis();
		Integer dead = null;
		for (Iterator<Map.Entry<Integer, Hit>> it = hits.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<Integer, Hit> e = it.next();
			if (now - e.getValue().at > WINDOW) {
				it.remove();
				continue;
			}
			// keep following them so the effect lands where they actually died
			if (mc.level.getEntity(e.getKey()) instanceof LivingEntity l) {
				e.getValue().x = l.getX();
				e.getValue().y = l.getY();
				e.getValue().z = l.getZ();
				if (l.isDeadOrDying()) dead = e.getKey();
			}
		}
		if (dead != null) fire(dead);
	}

	@Override
	protected void disable() {
		hits.clear();
	}

	// removing the hit is what stops two signals for the same death playing it twice
	private void fire(int id) {
		Hit h = hits.remove(id);
		ClientLevel level = Minecraft.getInstance().level;
		if (h == null || level == null || Util.getMillis() - h.at > WINDOW) return;
		play(level, h);
	}

	private void play(ClientLevel level, Hit h) {
		float k = amount.get().floatValue() / 100;
		String fx = effect.get();
		switch (fx) {
			case "Lightning" -> {
				// lives only on this client: visual only, and a negative id no server entity can ever have
				LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
				if (bolt != null) {
					bolt.setVisualOnly(true);
					bolt.setSilent(!sound.get().equals("Effect Default"));
					bolt.snapTo(h.x, h.y, h.z);
					bolt.setId(boltId--);
					if (boltId < -1_000_000) boltId = -1000;
					level.addEntity(bolt);
				}
			}
			case "Blood" -> burst(level, h, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState()), Math.round(60 * k), 0.25);
			case "Flames" -> burst(level, h, ParticleTypes.FLAME, Math.round(50 * k), 0.08);
			case "Hearts" -> burst(level, h, ParticleTypes.HEART, Math.round(20 * k), 0.2);
			case "Totem" -> burst(level, h, ParticleTypes.TOTEM_OF_UNDYING, Math.round(80 * k), 0.6);
			case "Explosion" -> burst(level, h, ParticleTypes.EXPLOSION, Math.max(1, Math.round(4 * k)), 0);
			default -> {}
		}
		SoundEvent s = switch (sound.get()) {
			case "Level Up" -> SoundEvents.PLAYER_LEVELUP;
			case "Orb" -> SoundEvents.EXPERIENCE_ORB_PICKUP;
			case "Anvil" -> SoundEvents.ANVIL_LAND;
			case "Effect Default" -> switch (fx) {
				case "Totem" -> SoundEvents.TOTEM_USE;
				case "Explosion" -> SoundEvents.GENERIC_EXPLODE.value();
				case "Blood" -> SoundEvents.PLAYER_HURT;
				case "Flames" -> SoundEvents.FIRECHARGE_USE;
				case "Hearts" -> SoundEvents.EXPERIENCE_ORB_PICKUP;
				default -> null;
			};
			default -> null;
		};
		if (s != null) level.playLocalSound(h.x, h.y, h.z, s, SoundSource.PLAYERS, volume.get().floatValue() / 100, 1, false);
		if (flash.on()) flashAt = Util.getMillis();
	}

	// around their middle, spread over their height, flying up and out
	private static void burst(ClientLevel level, Hit h, ParticleOptions type, int n, double speed) {
		var rnd = ThreadLocalRandom.current();
		double cy = h.y + h.height * 0.5;
		for (int i = 0; i < n; i++) {
			level.addParticle(type, h.x + rnd.nextDouble(-0.3, 0.3), cy + rnd.nextDouble(-0.5, 0.5) * h.height, h.z + rnd.nextDouble(-0.3, 0.3),
				rnd.nextDouble(-1, 1) * speed, rnd.nextDouble() * speed * 1.5, rnd.nextDouble(-1, 1) * speed);
		}
	}
}
