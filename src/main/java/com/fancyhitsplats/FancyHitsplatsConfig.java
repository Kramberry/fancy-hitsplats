package com.fancyhitsplats;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup(FancyHitsplatsConfig.GROUP)
public interface FancyHitsplatsConfig extends Config
{
	String GROUP = "fancyhitsplats";

	enum Start
	{
		OVER_HITSPLAT,
		ABOVE_HEAD
	}

	enum Style
	{
		MAPLESTORY,
		WORLD_OF_WARCRAFT
	}

	@ConfigItem(
		keyName = "style",
		name = "Style",
		description = "MapleStory: chunky orange numbers that pop in digit by digit and stack upward. "
			+ "World of Warcraft: clean numbers that drift up and away, crits pop big.",
		position = 0
	)
	default Style style()
	{
		return Style.MAPLESTORY;
	}

	@ConfigItem(
		keyName = "start",
		name = "Start position",
		description = "Over hitsplat: each number sits on top of the game's own splat until that splat disappears, "
			+ "then floats up. Above head: numbers start above the head and float from there.",
		position = 1
	)
	default Start start()
	{
		return Start.OVER_HITSPLAT;
	}

	@ConfigItem(
		keyName = "hideVanilla",
		name = "Hide normal hitsplat backgrounds",
		description = "Makes the game's red/blue/green splat images invisible (only on your screen). The game still "
			+ "draws its small number, so splats that aren't shown fancy are redrawn to look like the normal ones. "
			+ "Restart the client after changing this so splats that are already loaded pick it up.",
		position = 2
	)
	default boolean hideVanilla()
	{
		return false;
	}

	@ConfigItem(
		keyName = "plate",
		name = "Backing plate",
		description = "While the game's own splat is up, draw a bigger copy of it under the fancy number so the real "
			+ "one can't peek out around small numbers. Fades away once the game's splat is gone.",
		position = 3
	)
	default boolean plate()
	{
		return true;
	}

	@Range(min = 100, max = 250)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "plateSize",
		name = "Plate size",
		description = "The backing plate is the game's own splat, this much bigger.",
		position = 4
	)
	default int plateSize()
	{
		return 160;
	}

	@Range(min = 0, max = 100)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "numberOpacity",
		name = "Number opacity",
		description = "How solid the inside of the numbers is. Lower it to let the splat behind show through the "
			+ "digits - the outline stays solid (and gets bolder). 0 = outline only.",
		position = 5
	)
	default int numberOpacity()
	{
		return 100;
	}

	@ConfigItem(
		keyName = "burst",
		name = "Image burst",
		description = "When you hit something, 3 random pictures pop out above the hit like stickers and fade. "
			+ "Add pictures from the Fancy Hitsplats sidebar panel.",
		position = 6
	)
	default boolean burst()
	{
		return false;
	}

	@Range(min = 16, max = 160)
	@ConfigItem(
		keyName = "burstSize",
		name = "Burst picture size",
		description = "Size of each burst picture in pixels.",
		position = 7
	)
	default int burstSize()
	{
		return 44;
	}

	@ConfigItem(
		keyName = "myDamage",
		name = "My damage",
		description = "Your hits on monsters.",
		position = 8
	)
	default boolean myDamage()
	{
		return true;
	}

	@ConfigItem(
		keyName = "damageTaken",
		name = "Damage I take (fancy)",
		description = "Off = hits on you look like the normal game splats.",
		position = 9
	)
	default boolean damageTaken()
	{
		return false;
	}

	@ConfigItem(
		keyName = "teammatesAttacks",
		name = "Teammates' attacks (fancy)",
		description = "Other players' hits on monsters get fancy numbers too (silver). "
			+ "Damage landing on other players always stays normal.",
		position = 10
	)
	default boolean teammatesAttacks()
	{
		return true;
	}

	@ConfigItem(
		keyName = "heals",
		name = "Heals",
		description = "Green +numbers for heals.",
		position = 11
	)
	default boolean heals()
	{
		return true;
	}

	@ConfigItem(
		keyName = "dots",
		name = "Poison / venom / burn",
		description = "Damage-over-time ticks (shown smaller).",
		position = 12
	)
	default boolean dots()
	{
		return true;
	}

	@ConfigItem(
		keyName = "misses",
		name = "Misses",
		description = "Show MISS for blue 0 splats.",
		position = 13
	)
	default boolean misses()
	{
		return true;
	}

	@ConfigItem(
		keyName = "critThreshold",
		name = "Crit at",
		description = "Your max hits (the game's max-hit splat) always show as crits. Any hit at or above this "
			+ "number also does. 0 = only the game's max hits.",
		position = 14
	)
	default int critThreshold()
	{
		return 0;
	}

	@Range(min = 50, max = 250)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "scale",
		name = "Size",
		description = "Number size.",
		position = 15
	)
	default int scale()
	{
		return 100;
	}

	@Range(min = 500, max = 4000)
	@Units(Units.MILLISECONDS)
	@ConfigItem(
		keyName = "duration",
		name = "Duration",
		description = "How long each number floats after its splat is gone.",
		position = 16
	)
	default int duration()
	{
		return 1300;
	}
}
