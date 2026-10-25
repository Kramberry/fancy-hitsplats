package com.fancyhitsplats;

import net.runelite.api.Actor;

/** One floating number. */
class FloatingHit
{
	enum Kind
	{
		MY_HIT,  // you hit something
		TAKEN,   // something hit you
		OTHER,   // another player's hit on a monster
		HEAL,
		DOT,     // poison, venom, burn, bleed...
		MISS,
		NORMAL   // redrawn like the game's own splat (when its background is hidden)
	}

	final Actor actor;
	final int amount;
	final Kind kind;
	final boolean crit;
	final long startMs;
	final int stack;      // above-head mode: line index in this actor's current stack
	final float drift;    // World of Warcraft style: sideways drift, -1..1
	final int slot;       // the game's hitsplat slot 0-3 (centre, above, left, right)
	final long releaseMs; // when it starts floating: once the game's own splat is gone, or at once
	final int sprite;     // the game's background sprite for this splat type, -1 if unknown

	FloatingHit(Actor actor, int amount, Kind kind, boolean crit, long startMs, int stack, float drift,
		int slot, long releaseMs, int sprite)
	{
		this.actor = actor;
		this.amount = amount;
		this.kind = kind;
		this.crit = crit;
		this.startMs = startMs;
		this.stack = stack;
		this.drift = drift;
		this.slot = slot;
		this.releaseMs = releaseMs;
		this.sprite = sprite;
	}

	String text(FancyHitsplatsConfig.Style style)
	{
		boolean maple = style == FancyHitsplatsConfig.Style.MAPLESTORY;
		switch (kind)
		{
			case MISS:
				return maple ? "MISS" : "Miss";
			case HEAL:
				return "+" + amount;
			case TAKEN:
				return maple ? String.valueOf(amount) : "-" + amount;
			default:
				return String.valueOf(amount);
		}
	}
}
