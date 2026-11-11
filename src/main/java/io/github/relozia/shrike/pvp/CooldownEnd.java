package io.github.relozia.shrike.pvp;

// what CooldownInstanceAccessor puts on vanilla's private cooldown record. lives outside the mixin
// package because classes in there can't be loaded as normal classes
public interface CooldownEnd {
	int shrike$end();
}
