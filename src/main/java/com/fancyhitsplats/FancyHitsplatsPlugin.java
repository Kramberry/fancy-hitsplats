package com.fancyhitsplats;

import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Hitsplat;
import net.runelite.api.HitsplatID;
import net.runelite.api.Player;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.gameval.SpriteID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Filepath;

/**
 * MapleStory or World of Warcraft style damage numbers. The client's API can only report
 * hitsplats, not restyle them, so the fancy numbers are drawn on top of the game's own splats
 * (optionally over a bigger copy of the splat, or with the splat backgrounds hidden).
 */
@Slf4j
@PluginDescriptor(
	name = "Fancy Hitsplats",
	internalName = "fancy-hitsplats",
	description = "MapleStory or World of Warcraft style damage numbers",
	tags = {"hitsplat", "damage", "numbers", "maplestory", "wow", "combat", "cosmetic"}
)
public class FancyHitsplatsPlugin extends Plugin
{
	static final long BURST_MS = 1000;
	private static final int MAX_LIVE = 60;
	private static final int MAX_BURSTS = 30;
	private static final int MAX_PICTURES = 60;
	// Above-head mode stacks a new hit over the last one if it lands within this window.
	private static final long STACK_WINDOW_MS = 900;
	private static final int MS_PER_CLIENT_CYCLE = 20;

	// Every hitsplat background sprite.
	private static final int[] HITSPLAT_SPRITES = {
		SpriteID.HITMARK_BLOCKED,
		SpriteID.Hitmark._0, SpriteID.Hitmark._1, SpriteID.Hitmark._2, SpriteID.Hitmark._3, SpriteID.Hitmark._4, SpriteID.Hitmark._5,
		SpriteID.Hitmark._6, SpriteID.Hitmark._7, SpriteID.Hitmark._8, SpriteID.Hitmark._9, SpriteID.Hitmark._10, SpriteID.Hitmark._11,
		SpriteID.Hitmark._12, SpriteID.Hitmark._13, SpriteID.Hitmark._14, SpriteID.Hitmark._15, SpriteID.Hitmark._16, SpriteID.Hitmark._17,
		SpriteID.Hitmark._18, SpriteID.Hitmark._19, SpriteID.Hitmark._20, SpriteID.Hitmark._21, SpriteID.Hitmark._22, SpriteID.Hitmark._23,
		SpriteID.Hitmark._24, SpriteID.Hitmark._25, SpriteID.Hitmark._26, SpriteID.Hitmark._27, SpriteID.Hitmark._28, SpriteID.Hitmark._29,
		SpriteID.Hitmark._30, SpriteID.Hitmark._31, SpriteID.Hitmark._32, SpriteID.Hitmark._33, SpriteID.Hitmark._34, SpriteID.Hitmark._35,
		SpriteID.Hitmark._36, SpriteID.Hitmark._37, SpriteID.Hitmark._38, SpriteID.Hitmark._39, SpriteID.Hitmark._40, SpriteID.Hitmark._41,
		SpriteID.Hitmark._42, SpriteID.Hitmark._43, SpriteID.Hitmark._44, SpriteID.Hitmark._45, SpriteID.Hitmark._46, SpriteID.Hitmark._47,
		SpriteID.Hitmark._48, SpriteID.Hitmark._49, SpriteID.Hitmark._50, SpriteID.Hitmark._51, SpriteID.Hitmark._52, SpriteID.Hitmark._53
	};

	// Hitsplat type -> its background sprite, read from the game's hitsplat definitions
	// (types that switch sprite by game setting use their default sprite).
	private static final int[][] TYPE_SPRITES = {
		{0, SpriteID.Hitmark._17}, {1, SpriteID.HITMARK_BLOCKED}, {2, SpriteID.Hitmark._37}, {3, SpriteID.Hitmark._3},
		{4, SpriteID.Hitmark._12}, {5, SpriteID.Hitmark._11}, {6, SpriteID.Hitmark._8}, {7, SpriteID.Hitmark._18},
		{8, SpriteID.Hitmark._18}, {9, SpriteID.Hitmark._19}, {10, SpriteID.Hitmark._20}, {11, SpriteID.Hitmark._22},
		{12, SpriteID.Hitmark._0}, {13, SpriteID.Hitmark._9}, {14, SpriteID.Hitmark._21}, {15, SpriteID.Hitmark._23},
		{16, SpriteID.Hitmark._1}, {17, SpriteID.Hitmark._10}, {18, SpriteID.Hitmark._6}, {19, SpriteID.Hitmark._15},
		{20, SpriteID.Hitmark._7}, {21, SpriteID.Hitmark._16}, {22, SpriteID.Hitmark._4}, {23, SpriteID.Hitmark._13},
		{24, SpriteID.Hitmark._5}, {25, SpriteID.Hitmark._14}, {26, SpriteID.Hitmark._0}, {27, SpriteID.Hitmark._9},
		{28, SpriteID.Hitmark._1}, {29, SpriteID.Hitmark._10}, {30, SpriteID.Hitmark._4}, {31, SpriteID.Hitmark._13},
		{32, SpriteID.Hitmark._5}, {33, SpriteID.Hitmark._14}, {34, SpriteID.Hitmark._6}, {35, SpriteID.Hitmark._15},
		{36, SpriteID.Hitmark._7}, {37, SpriteID.Hitmark._16}, {38, SpriteID.Hitmark._19}, {39, SpriteID.Hitmark._20},
		{40, SpriteID.Hitmark._22}, {41, SpriteID.Hitmark._21}, {42, SpriteID.Hitmark._23}, {43, SpriteID.Hitmark._24},
		{44, SpriteID.Hitmark._27}, {45, SpriteID.Hitmark._28}, {46, SpriteID.Hitmark._25}, {47, SpriteID.Hitmark._26},
		{48, SpriteID.Hitmark._24}, {49, SpriteID.Hitmark._25}, {50, SpriteID.Hitmark._26}, {51, SpriteID.Hitmark._27},
		{52, SpriteID.Hitmark._28}, {53, SpriteID.Hitmark._29}, {54, SpriteID.Hitmark._30}, {55, SpriteID.Hitmark._31},
		{56, SpriteID.Hitmark._29}, {57, SpriteID.Hitmark._30}, {58, SpriteID.Hitmark._31}, {59, SpriteID.Hitmark._32},
		{60, SpriteID.Hitmark._33}, {61, SpriteID.Hitmark._34}, {62, SpriteID.Hitmark._32}, {63, SpriteID.Hitmark._33},
		{64, SpriteID.Hitmark._34}, {65, SpriteID.Hitmark._2}, {66, SpriteID.Hitmark._36}, {67, SpriteID.Hitmark._35},
		{68, SpriteID.Hitmark._2}, {69, SpriteID.Hitmark._36}, {70, SpriteID.Hitmark._37}, {71, SpriteID.Hitmark._38},
		{72, SpriteID.Hitmark._39}, {73, SpriteID.Hitmark._40}, {74, SpriteID.Hitmark._41}, {75, SpriteID.Hitmark._42},
		{76, SpriteID.Hitmark._43}, {77, SpriteID.Hitmark._27}, {78, SpriteID.Hitmark._44}, {79, SpriteID.Hitmark._45},
		{80, SpriteID.Hitmark._46}, {81, SpriteID.Hitmark._47}, {82, SpriteID.Hitmark._1}
	};

	@Inject
	private Client client;
	@Inject
	private FancyHitsplatsConfig config;
	@Inject
	private OverlayManager overlayManager;
	@Inject
	private FancyHitsplatsOverlay overlay;
	@Inject
	private ClientThread clientThread;
	@Inject
	private ClientToolbar clientToolbar;
	@Inject
	private ConfigManager configManager;
	@Inject
	private SpriteManager spriteManager;
	@Inject
	private ScheduledExecutorService executor;

	// The game's own splat images, grabbed before they are swapped out; used for the backing
	// plate and for redrawing splats that aren't shown fancy.
	final Map<Integer, BufferedImage> originals = new HashMap<>();
	final List<FloatingHit> hits = new ArrayList<>();
	final List<Burst> bursts = new ArrayList<>();

	private final Map<Integer, Integer> spriteForType = new HashMap<>();
	private final Map<Actor, long[]> stacks = new HashMap<>(); // actor -> {last hit ms, stack size}
	private final Map<Actor, int[]> slots = new HashMap<>();   // actor -> client cycle each splat slot frees up
	// Decoded off the client thread; hits only use what has finished loading.
	private volatile List<BufferedImage> burstImages = Collections.emptyList();

	private FancyHitsplatsPanel panel;
	private NavigationButton navButton;

	@Provides
	FancyHitsplatsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(FancyHitsplatsConfig.class);
	}

	@Override
	protected void startUp()
	{
		for (int[] ts : TYPE_SPRITES)
		{
			spriteForType.put(ts[0], ts[1]);
		}
		overlayManager.add(overlay);
		panel = new FancyHitsplatsPanel(this);
		navButton = NavigationButton.builder()
			.tooltip("Fancy Hitsplats")
			.icon(createIcon())
			.priority(7)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
		clientThread.invoke(this::applyHide);
		reloadBurstImages();
		refreshPanel();
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		clientToolbar.removeNavigation(navButton);
		clientThread.invoke(this::removeHide);
		navButton = null;
		panel = null;
		hits.clear();
		bursts.clear();
		stacks.clear();
		slots.clear();
		burstImages = Collections.emptyList();
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged e)
	{
		if (!FancyHitsplatsConfig.GROUP.equals(e.getGroup()))
		{
			return;
		}
		if ("hideVanilla".equals(e.getKey()))
		{
			clientThread.invoke(this::applyHide);
		}
		else if ("burst".equals(e.getKey()))
		{
			refreshPanel();
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged e)
	{
		if (e.getGameState() != GameState.LOGGED_IN)
		{
			hits.clear();
			bursts.clear();
			stacks.clear();
			slots.clear();
		}
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied e)
	{
		Actor actor = e.getActor();
		Hitsplat splat = e.getHitsplat();
		if (actor == null || splat == null)
		{
			return;
		}
		int type = splat.getHitsplatType();
		FloatingHit.Kind kind = classify(actor, splat);
		if (kind == null)
		{
			if (!config.hideVanilla() || !spriteForType.containsKey(type))
			{
				return;
			}
			kind = FloatingHit.Kind.NORMAL; // its background is hidden, so draw it like the game would
		}
		boolean maxHit = type >= HitsplatID.DAMAGE_MAX_ME && type <= HitsplatID.DAMAGE_MAX_ME_WHITE
			|| type == HitsplatID.DAMAGE_MAX_ME_POISE;
		boolean crit = kind == FloatingHit.Kind.MY_HIT
			&& (maxHit || config.critThreshold() > 0 && splat.getAmount() >= config.critThreshold());

		long now = System.currentTimeMillis();
		long[] st = stacks.computeIfAbsent(actor, a -> new long[2]);
		st[1] = now - st[0] <= STACK_WINDOW_MS ? st[1] + 1 : 0;
		st[0] = now;
		float drift = (float) ThreadLocalRandom.current().nextDouble(-1, 1);

		// Mirror the game's splat slots: the first free slot, else the one ending soonest.
		int cycle = client.getGameCycle();
		int ends = splat.getDisappearsOnGameCycle();
		int[] sl = slots.computeIfAbsent(actor, a -> new int[4]);
		int slot = 0;
		for (int i = 0; i < sl.length; i++)
		{
			if (sl[i] <= cycle)
			{
				slot = i;
				break;
			}
			if (sl[i] < sl[slot])
			{
				slot = i;
			}
		}
		sl[slot] = ends;

		boolean over = kind == FloatingHit.Kind.NORMAL || config.start() == FancyHitsplatsConfig.Start.OVER_HITSPLAT;
		long release = over ? now + Math.max(0, ends - cycle) * (long) MS_PER_CLIENT_CYCLE : now;
		hits.add(new FloatingHit(actor, splat.getAmount(), kind, crit, now, over ? 0 : (int) st[1], drift, slot,
			release, spriteForType.getOrDefault(type, -1)));
		while (hits.size() > MAX_LIVE)
		{
			hits.remove(0);
		}
		if (kind == FloatingHit.Kind.MY_HIT && config.burst())
		{
			spawnBurst(actor, slot, now);
		}
	}

	private FloatingHit.Kind classify(Actor actor, Hitsplat splat)
	{
		boolean onMe = actor == client.getLocalPlayer();
		// Damage landing on players stays normal: other players always, you unless chosen otherwise.
		if (actor instanceof Player && (!onMe || !config.damageTaken()))
		{
			return null;
		}
		switch (splat.getHitsplatType())
		{
			case HitsplatID.HEAL:
				return config.heals() && (onMe || splat.isMine() || config.teammatesAttacks()) ? FloatingHit.Kind.HEAL : null;
			case HitsplatID.POISON:
			case HitsplatID.VENOM:
			case HitsplatID.DISEASE:
			case HitsplatID.BLEED:
			case HitsplatID.BURN:
			case HitsplatID.DOOM:
				if (!config.dots() || splat.getAmount() <= 0)
				{
					return null;
				}
				return onMe || splat.isMine() || config.teammatesAttacks() ? FloatingHit.Kind.DOT : null;
			case HitsplatID.BLOCK_ME:
				if (!config.misses())
				{
					return null;
				}
				return (onMe ? config.damageTaken() : config.myDamage()) ? FloatingHit.Kind.MISS : null;
			case HitsplatID.BLOCK_OTHER:
				return config.misses() && config.teammatesAttacks() ? FloatingHit.Kind.MISS : null;
		}
		if (splat.getAmount() <= 0)
		{
			return null;
		}
		if (onMe)
		{
			return FloatingHit.Kind.TAKEN;
		}
		if (splat.isMine())
		{
			return config.myDamage() ? FloatingHit.Kind.MY_HIT : null;
		}
		return config.teammatesAttacks() ? FloatingHit.Kind.OTHER : null;
	}

	/** Drop finished numbers and pictures; called by the overlay each frame. */
	void prune(long now, long duration)
	{
		hits.removeIf(h -> h.kind == FloatingHit.Kind.NORMAL ? now > h.releaseMs : now - h.releaseMs > duration);
		bursts.removeIf(b -> now - b.startMs > BURST_MS);
		stacks.values().removeIf(st -> now - st[0] > duration);
		int cycle = client.getGameCycle();
		slots.values().removeIf(sl -> sl[0] <= cycle && sl[1] <= cycle && sl[2] <= cycle && sl[3] <= cycle);
	}

	// ---- Hiding the game's splat backgrounds ----

	/** Remember the game's splat images, then (if enabled) swap them for see-through ones. */
	private void applyHide()
	{
		for (int id : HITSPLAT_SPRITES)
		{
			if (!originals.containsKey(id) && !client.getSpriteOverrides().containsKey(id))
			{
				BufferedImage img = spriteManager.getSprite(id, 0);
				if (img != null)
				{
					originals.put(id, img);
				}
			}
		}
		if (!config.hideVanilla())
		{
			removeHide();
			return;
		}
		for (int id : HITSPLAT_SPRITES)
		{
			client.getSpriteOverrides().put(id, client.createSpritePixels(new int[]{0}, 1, 1));
		}
	}

	private void removeHide()
	{
		for (int id : HITSPLAT_SPRITES)
		{
			client.getSpriteOverrides().remove(id);
		}
	}

	// ---- Image burst ----

	/** Three random pictures fanning out upward (left, up, right). */
	private void spawnBurst(Actor actor, int slot, long now)
	{
		List<BufferedImage> pics = burstImages;
		if (pics.isEmpty())
		{
			return;
		}
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		List<BufferedImage> pool = new ArrayList<>(pics);
		Collections.shuffle(pool, rnd);
		double[] fan = {-Math.PI * 0.92, -Math.PI * 0.5, -Math.PI * 0.08};
		for (int i = 0; i < fan.length; i++)
		{
			BufferedImage img = pool.get(i % pool.size());
			double angle = fan[i] + rnd.nextDouble(-0.1, 0.1);
			float spin = (float) rnd.nextDouble(-28, 28);
			float reach = (float) rnd.nextDouble(0.85, 1.15);
			bursts.add(new Burst(actor, slot, img, angle, spin, reach, now + i * 45L));
		}
		while (bursts.size() > MAX_BURSTS)
		{
			bursts.remove(0);
		}
	}

	boolean isBurstEnabled()
	{
		return config.burst();
	}

	void setBurstEnabled(boolean on)
	{
		configManager.setConfiguration(FancyHitsplatsConfig.GROUP, "burst", on);
	}

	/** The folder the burst pictures are kept in: .runelite/plugin-data/fancy-hitsplats/pictures. */
	Filepath picturesDir()
	{
		try
		{
			Filepath dir = getPluginDirectory().joinSegment("pictures");
			dir.createDirectories();
			return dir;
		}
		catch (IOException | IllegalArgumentException ex)
		{
			log.warn("Couldn't open the pictures folder", ex);
			return null;
		}
	}

	/** The pictures currently in the folder, sorted by name. */
	List<Filepath> listPictures()
	{
		Filepath dir = picturesDir();
		if (dir == null)
		{
			return Collections.emptyList();
		}
		try (Stream<Filepath> files = dir.walk(1))
		{
			return files.filter(Filepath::isFile)
				.filter(f -> isPicture(f.getFileName()))
				.sorted()
				.collect(Collectors.toList());
		}
		catch (IOException ex)
		{
			log.warn("Couldn't list the pictures folder", ex);
			return Collections.emptyList();
		}
	}

	static boolean isPicture(String name)
	{
		String n = name.toLowerCase(Locale.ROOT);
		return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".gif");
	}

	/** Copy the chosen pictures into the folder (in the background), then reload. */
	void addPictures(List<Filepath> chosen)
	{
		executor.execute(() ->
		{
			Filepath dir = picturesDir();
			if (dir == null)
			{
				return;
			}
			for (Filepath f : chosen)
			{
				try
				{
					if (isPicture(f.getFileName()))
					{
						f.copyTo(dir.joinSegment(f.getFileName()), StandardCopyOption.REPLACE_EXISTING);
					}
				}
				catch (IOException | IllegalArgumentException ex)
				{
					log.warn("Couldn't copy {}", f.getFileName(), ex);
				}
			}
			loadBurstImages();
			refreshPanel();
		});
	}

	/** Delete every picture from the folder (in the background), then reload. */
	void removeAllPictures()
	{
		executor.execute(() ->
		{
			for (Filepath f : listPictures())
			{
				try
				{
					f.deleteIfExists();
				}
				catch (IOException ex)
				{
					log.warn("Couldn't delete {}", f.getFileName(), ex);
				}
			}
			loadBurstImages();
			refreshPanel();
		});
	}

	void reloadBurstImages()
	{
		executor.execute(this::loadBurstImages);
	}

	/** Decode the folder's pictures (shrunk to at most 160px) and swap them in. Not on the client thread. */
	private void loadBurstImages()
	{
		List<BufferedImage> out = new ArrayList<>();
		for (Filepath f : listPictures())
		{
			if (out.size() >= MAX_PICTURES)
			{
				break;
			}
			BufferedImage img = readImage(f);
			if (img != null)
			{
				out.add(shrink(img, 160));
			}
		}
		burstImages = Collections.unmodifiableList(out);
	}

	static BufferedImage readImage(Filepath f)
	{
		try (InputStream in = f.openInputStream())
		{
			return ImageIO.read(in);
		}
		catch (IOException ex)
		{
			return null;
		}
	}

	static BufferedImage shrink(BufferedImage img, int max)
	{
		double s = Math.min(1.0, max / (double) Math.max(img.getWidth(), img.getHeight()));
		int w = Math.max(1, (int) Math.round(img.getWidth() * s));
		int h = Math.max(1, (int) Math.round(img.getHeight() * s));
		BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		g.drawImage(img, 0, 0, w, h, null);
		g.dispose();
		return out;
	}

	ScheduledExecutorService executor()
	{
		return executor;
	}

	private void refreshPanel()
	{
		FancyHitsplatsPanel p = panel;
		if (p != null)
		{
			SwingUtilities.invokeLater(p::refresh);
		}
	}

	private static BufferedImage createIcon()
	{
		BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		Polygon star = new Polygon();
		for (int i = 0; i < 16; i++)
		{
			double a = Math.PI * i / 8 - Math.PI / 2;
			double r = i % 2 == 0 ? 7.5 : 3.5;
			star.addPoint((int) Math.round(8 + Math.cos(a) * r), (int) Math.round(8 + Math.sin(a) * r));
		}
		g.setColor(new Color(0xFF8000));
		g.fill(star);
		g.setColor(new Color(0x3A1400));
		g.draw(star);
		g.dispose();
		return img;
	}
}
