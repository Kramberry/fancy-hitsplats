package com.fancyhitsplats;

import java.awt.image.BufferedImage;
import net.runelite.api.Actor;

/** One picture flying out of a hit ("image burst"). */
class Burst
{
	final Actor actor;
	final int slot;
	final BufferedImage image;
	final double angle;  // flight direction in radians (screen space, -PI/2 = straight up)
	final float spin;    // end rotation in degrees
	final float reach;   // flight distance multiplier
	final long startMs;

	Burst(Actor actor, int slot, BufferedImage image, double angle, float spin, float reach, long startMs)
	{
		this.actor = actor;
		this.slot = slot;
		this.image = image;
		this.angle = angle;
		this.spin = spin;
		this.reach = reach;
		this.startMs = startMs;
	}
}
