package io.github.relozia.shrike.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.relozia.shrike.visual.WorldVisuals;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.CritParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// crit is the plain provider, sharpness (enchanted hit) the magic one, both make a CritParticle
@Mixin({CritParticle.Provider.class, CritParticle.MagicProvider.class})
public class CritParticleMixin {
	@ModifyReturnValue(method = "createParticle(Lnet/minecraft/core/particles/SimpleParticleType;Lnet/minecraft/client/multiplayer/ClientLevel;DDDDDDLnet/minecraft/util/RandomSource;)Lnet/minecraft/client/particle/Particle;", at = @At("RETURN"))
	private Particle shrike$color(Particle p, SimpleParticleType type, ClientLevel level, double x, double y, double z, double xa, double ya, double za, RandomSource r) {
		WorldVisuals.Particles.color((SingleQuadParticle) p, (Object) this instanceof CritParticle.MagicProvider, r);
		return p;
	}
}
