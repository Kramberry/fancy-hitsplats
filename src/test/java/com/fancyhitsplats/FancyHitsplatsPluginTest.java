package com.fancyhitsplats;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class FancyHitsplatsPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(FancyHitsplatsPlugin.class);
		RuneLite.main(args);
	}
}
