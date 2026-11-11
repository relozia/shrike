package io.github.relozia.kestrel.visual;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.relozia.kestrel.module.Category;
import io.github.relozia.kestrel.module.Module;
import io.github.relozia.kestrel.setting.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.resources.model.cuboid.ItemTransform;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwingAnimationType;
import net.minecraft.world.phys.HitResult;
import org.joml.Vector3fc;

// 1.7 held items + swing styles. the 1.7 part is 1.7.10's first person math done step by step on the pose,
// with the item model's own first person transform swapped for the fixed one 1.7 used on every flat item
// issues:
//   - left hand is the right hand chain mirrored, 1.7 never drew anything in the left hand so there's nothing to match
//   - Old Sneak puts the eye 0.08 under standing, in a 1.5 block gap that's above the crouch hitbox
//   - other players swinging still use vanilla's styles, only your own hands get the swing style
public class Animations extends Module {
	static Animations INSTANCE;

	private final Setting.Toggle positions = add(new Setting.Toggle("Old Item Positions", true));
	private final Setting.Toggle blocking = add(new Setting.Toggle("Old Sword Blocking", true));
	private final Setting.Toggle swingUsing = add(new Setting.Toggle("Swing While Using", true));
	private final Setting.Toggle redArmor = add(new Setting.Toggle("Red Armor When Hit", true));
	private final Setting.Toggle sneak = add(new Setting.Toggle("Old Sneak", true));
	private final Setting.Toggle healFlash = add(new Setting.Toggle("No Heal Flash", true));
	private final Setting.Choice style = add(new Setting.Choice("Swing Style", "Default", "Default", "Smooth", "Push", "Slide", "Tap", "Spin"));
	private final Setting.Slider strength = add(new Setting.Slider("Swing Strength", 100, 25, 200, 5, "%"));
	private final Setting.Slider speed = add(new Setting.Slider("Swing Speed", 100, 50, 200, 5, "%"));
	private final Setting.Toggle swordsOnly = add(new Setting.Toggle("Style Only On Swords", true));

	// set while our 1.7 chain is drawing an item, the layer hook reads it to swap the display transform
	private static Legacy legacy;

	private record Legacy(int d, boolean block, boolean bow, float pull, float timeHeld) {}

	public Animations() {
		super("Animations", Category.VISUAL, '\ue2b4', "1.7 item positions, sword blocking and red armor, plus swing styles.");
		INSTANCE = this;
	}

	private static boolean has(Setting.Toggle t) {
		return INSTANCE != null && INSTANCE.on() && t.on();
	}

	public static boolean redArmor() { return has(INSTANCE.redArmor); }
	public static boolean noHealFlash() { return has(INSTANCE.healFlash); }
	public static boolean oldSneak() { return has(INSTANCE.sneak); }

	// shield in the offhand being used, sword in the main hand
	public static boolean swordBlock(boolean using, InteractionHand used, ItemStack main, ItemStack off) {
		return has(INSTANCE.blocking) && using && used == InteractionHand.OFF_HAND && main.is(ItemTags.SWORDS) && off.has(DataComponents.BLOCKS_ATTACKS);
	}

	private static boolean swordBlock(Player p) {
		return swordBlock(p.isUsingItem(), p.getUsedItemHand(), p.getMainHandItem(), p.getOffhandItem());
	}

	@Override
	public void tick(Minecraft mc) {
		// holding attack on a block while eating or blocking keeps the arm going, 1.7 let you "mine" mid use
		if (swingUsing.on() && mc.player != null && mc.player.isUsingItem() && mc.options.keyAttack.isDown()
			&& mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK) {
			mc.player.swing(InteractionHand.MAIN_HAND, false);
		}
	}

	// vanilla eats every attack click while you use an item. with Swing While Using each one swings, locally
	// (the 2 arg swing never sends a packet, LocalPlayer only overrides the 1 arg one)
	public static void usingClick(Player p) {
		if (has(INSTANCE.swingUsing)) p.swing(InteractionHand.MAIN_HAND, false);
	}

	public static int swingDuration(Player p, int vanilla) {
		if (INSTANCE == null || !INSTANCE.on() || p != Minecraft.getInstance().player) return vanilla;
		return Math.max(1, Math.round(vanilla * 100 / INSTANCE.speed.get().floatValue()));
	}

	// ---------- first person ----------

	// true = drawn here (or hidden), skip vanilla's submitArmWithItem
	public static boolean firstPerson(ItemInHandRenderer r, AbstractClientPlayer p, float pt, InteractionHand hand, float attack,
		ItemStack stack, float inv, PoseStack ps, SubmitNodeCollector out, int light) {
		if (INSTANCE == null || !INSTANCE.on() || p.isScoping() || stack.isEmpty()) return false;
		boolean main = hand == InteractionHand.MAIN_HAND, swordBlock = swordBlock(p);
		// the shield is still what blocks, it just isn't drawn while the sword shows the block
		if (swordBlock && !main) return true;
		if (!INSTANCE.positions.on() || stack.has(DataComponents.MAP_ID) || stack.is(Items.CROSSBOW) || p.isAutoSpinAttack()) return false;

		boolean using = p.isUsingItem() && p.getUseItemRemainingTicks() > 0 && p.getUsedItemHand() == hand;
		ItemUseAnimation use = using ? stack.getUseAnimation() : ItemUseAnimation.NONE;
		boolean eat = use == ItemUseAnimation.EAT || use == ItemUseAnimation.DRINK;
		boolean block = swordBlock && main || use == ItemUseAnimation.BLOCK && stack.is(ItemTags.SWORDS);
		boolean bow = use == ItemUseAnimation.BOW;
		// trident, spear, brush, spyglass, shield etc. have their own modern poses, nothing 1.7 to go back to
		if (using && !eat && !block && !bow) return false;
		if (!using && !block && stack.getSwingAnimation().type() != SwingAnimationType.WHACK) return false;

		HumanoidArm arm = main ? p.getMainArm() : p.getMainArm().getOpposite();
		int d = arm == HumanoidArm.RIGHT ? 1 : -1;
		ps.pushPose();
		float timeHeld = 0;
		if (eat) {
			float left = p.getUseItemRemainingTicks() - pt + 1;
			float done = 1 - left / stack.getUseDuration(p);
			float k = 1 - done;
			k = k * k * k;
			k = k * k * k;
			k = k * k * k;
			float e = 1 - k;
			ps.translate(0, Mth.abs(Mth.cos(left / 4 * Mth.PI) * 0.1f) * (done > 0.2f ? 1 : 0), 0);
			ps.translate(e * 0.6f * d, -e * 0.5f, 0);
			ps.mulPose(Axis.YP.rotationDegrees(e * 90 * d));
			ps.mulPose(Axis.XP.rotationDegrees(e * 10));
			ps.mulPose(Axis.ZP.rotationDegrees(e * 30 * d));
		}
		if (bow) timeHeld = stack.getUseDuration(p) - (p.getUseItemRemainingTicks() - pt + 1);

		ps.translate(d * 0.56f, -0.52f - inv * 0.6f, -0.72f);
		// 1.7 moves the arm with the swing only when not using something, the rotation part always runs (block hitting)
		swing(ps, stack, attack, d, using || block);
		ps.mulPose(Axis.YP.rotationDegrees(d * 45));

		float pull = Math.min(1, (timeHeld / 20 * (timeHeld / 20) + timeHeld / 20 * 2) / 3);
		legacy = new Legacy(d, block, bow, pull, timeHeld);
		r.renderItem(p, stack, d == 1 ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND, ps, out, light);
		legacy = null;
		ps.popPose();
		return true;
	}

	// flat item models all share vanilla's first person rotation [0, -90, 25], rods have [0, 90, 25] (that's how the
	// json turns them around). anything else (blocks, shields, a pack's custom pose) keeps its own transform
	public static boolean layer(ItemTransform t, PoseStack.Pose pose) {
		Legacy l = legacy;
		if (l == null) return false;
		Vector3fc r = t.rotation();
		boolean flat = Math.abs(r.x()) < 1 && Math.abs(Math.abs(r.y()) - 90) < 1 && Math.abs(r.z() - 25) < 1;
		int d = l.d;
		if (!flat) {
			// 1.7 drew blocks at 0.4 scale turned 45, vanilla's block model transform already is exactly that
			pose.rotate(Axis.YP.rotationDegrees(d * -45));
			return false;
		}
		pose.scale(0.4f, 0.4f, 0.4f);
		if (l.block) {
			pose.translate(d * -0.5f, 0.2f, 0);
			pose.rotate(Axis.YP.rotationDegrees(d * 30));
			pose.rotate(Axis.XP.rotationDegrees(-80));
			pose.rotate(Axis.YP.rotationDegrees(d * 60));
		} else if (l.bow) {
			pose.rotate(Axis.ZP.rotationDegrees(d * -18));
			pose.rotate(Axis.YP.rotationDegrees(d * -12));
			pose.rotate(Axis.XP.rotationDegrees(-8));
			pose.translate(d * -0.9f, 0.2f, 0);
			if (l.pull > 0.1f) pose.translate(0, Mth.sin((l.timeHeld - 0.1f) * 1.3f) * 0.01f * (l.pull - 0.1f), 0);
			pose.translate(0, 0, l.pull * 0.1f);
			// stretch along the arrow, done in the item's own frame like 1.7
			pose.rotate(Axis.ZP.rotationDegrees(d * -335));
			pose.rotate(Axis.YP.rotationDegrees(d * -50));
			pose.translate(0, 0.5f, 0);
			pose.scale(1, 1, 1 + l.pull * 0.2f);
			pose.translate(0, -0.5f, 0);
			pose.rotate(Axis.YP.rotationDegrees(d * 50));
			pose.rotate(Axis.ZP.rotationDegrees(d * 335));
		}
		if (r.y() > 0) pose.rotate(Axis.YP.rotationDegrees(180));

		// 1.7's flat item: down 0.3, 1.5x, turned 50 and 335, sprite corner moved to the hand
		pose.translate(0, -0.3f, 0);
		pose.scale(1.5f, 1.5f, 1.5f);
		pose.rotate(Axis.YP.rotationDegrees(d * 50));
		pose.rotate(Axis.ZP.rotationDegrees(d * 335));
		pose.translate(d * -0.9375f, -0.0625f, 0);
		// 1.7's sprite spans 0..1 with its readable side facing -z and its thickness behind z 0. the modern one is
		// centered with the readable side on +z, so half a turn and a shift line them up
		pose.translate(d * 0.5f, 0.5f, -0.03125f);
		pose.rotate(Axis.YP.rotationDegrees(180));
		pose.translate(-0.5f, -0.5f, -0.5f);
		return true;
	}

	// ---------- swing ----------

	public static boolean styled() {
		return INSTANCE != null && INSTANCE.on();
	}

	// one function for both paths: vanilla's swingArm and the 1.7 chain. at attack 0 every style is a no-op.
	// "rotOnly" = 1.7 while using an item, the arm doesn't bob but still rotates
	public static void swing(PoseStack ps, ItemStack stack, float a, int d, boolean rotOnly) {
		Animations an = INSTANCE;
		String st = an == null || !an.on() || an.swordsOnly.on() && !stack.is(ItemTags.SWORDS) ? "Default" : an.style.get();
		float k = an == null || !an.on() ? 1 : an.strength.get().floatValue() / 100;
		float sq = Mth.sin(Mth.sqrt(a) * Mth.PI), sa = Mth.sin(a * Mth.PI);
		switch (st) {
			case "Smooth" -> {
				// one even arc down and back, no bounce
				if (!rotOnly) ps.translate(0, -0.08f * sa * k, -0.1f * sa * k);
				ps.mulPose(Axis.XP.rotationDegrees(-55 * sa * k));
				ps.mulPose(Axis.ZP.rotationDegrees(d * -12 * sa * k));
			}
			case "Push" -> {
				// straight jab forward
				if (!rotOnly) ps.translate(d * -0.08f * sq * k, 0.04f * sq * k, -0.35f * sq * k);
				ps.mulPose(Axis.XP.rotationDegrees(-25 * sq * k));
			}
			case "Slide" -> {
				// pulls down and back along the blade. it used to slide toward the crosshair, but the sword's flat side
				// faces sideways, so in the middle of the screen you only saw its edge
				// (tilting it back too swings the tip past the camera)
				if (!rotOnly) ps.translate(d * 0.04f * sq * k, -0.12f * sq * k, 0.06f * sa * k);
				ps.mulPose(Axis.ZP.rotationDegrees(d * 10 * sq * k));
			}
			case "Tap" -> {
				// short dip, reads well at high cps
				if (!rotOnly) ps.translate(0, -0.12f * sq * k, 0);
				ps.mulPose(Axis.XP.rotationDegrees(-30 * sq * k));
			}
			case "Spin" -> {
				// full turn around the arm
				if (!rotOnly) ps.translate(0, -0.06f * sa * k, 0);
				ps.mulPose(Axis.YP.rotationDegrees(d * -360 * a));
			}
			default -> {
				// vanilla's swingArm, which is also 1.7's (translation, then the 45 / -45 wrapped rotation)
				if (!rotOnly) ps.translate(d * -0.4f * sq * k, 0.2f * Mth.sin(Mth.sqrt(a) * Mth.TWO_PI) * k, -0.2f * sa * k);
				float y = Mth.sin(a * a * Mth.PI);
				ps.mulPose(Axis.YP.rotationDegrees(d * (45 + y * -20 * k)));
				ps.mulPose(Axis.ZP.rotationDegrees(d * sq * -20 * k));
				ps.mulPose(Axis.XP.rotationDegrees(sq * -80 * k));
				ps.mulPose(Axis.YP.rotationDegrees(d * -45));
			}
		}
	}

	// ---------- sneak ----------

	// 1.7: crouching drops the eye 0.08 under standing, in one go
	public static float sneakEye(Player p) {
		return p.getPose() == Pose.CROUCHING ? p.getEyeHeight(Pose.STANDING) - 0.08f : p.getEyeHeight();
	}

	// swimming, crawling, gliding keep vanilla's easing, 1.7 had none of those
	public static boolean sneakApplies(Player p) {
		return oldSneak() && p == Minecraft.getInstance().player && (p.getPose() == Pose.CROUCHING || p.getPose() == Pose.STANDING);
	}
}
