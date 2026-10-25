package com.fancyhitsplats;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.Filepath;

/**
 * Sidebar panel for the image burst: turn it on, add pictures with the file picker (they are
 * copied into the plugin's own folder), and see what's there.
 */
class FancyHitsplatsPanel extends PluginPanel
{
	private static final int THUMB = 64;

	private final FancyHitsplatsPlugin plugin;
	private final JCheckBox enabled = new JCheckBox("Image burst on my hits");
	private final JLabel countLabel = new JLabel();
	private final JPanel thumbs = new JPanel(new GridLayout(0, 3, 4, 4));

	FancyHitsplatsPanel(FancyHitsplatsPlugin plugin)
	{
		this.plugin = plugin;
		setLayout(new BorderLayout());
		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

		JPanel top = new JPanel();
		top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
		top.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JLabel title = new JLabel("Fancy Hitsplats - image burst");
		title.setForeground(Color.WHITE);
		top.add(title);
		top.add(Box.createVerticalStrut(6));

		enabled.setBackground(ColorScheme.DARK_GRAY_COLOR);
		enabled.addActionListener(e -> plugin.setBurstEnabled(enabled.isSelected()));
		top.add(enabled);
		top.add(Box.createVerticalStrut(6));

		JLabel help = new JLabel("<html>Each of your hits shows 3 random pictures from this list. Add as many as you like.</html>");
		help.setForeground(Color.LIGHT_GRAY);
		top.add(help);
		top.add(Box.createVerticalStrut(6));

		JButton add = new JButton("Add pictures...");
		add.addActionListener(e -> addPictures());
		JButton clear = new JButton("Remove all pictures");
		clear.addActionListener(e -> confirmRemoveAll());
		JPanel buttons = new JPanel(new GridLayout(0, 1, 0, 4));
		buttons.setBackground(ColorScheme.DARK_GRAY_COLOR);
		buttons.add(add);
		buttons.add(clear);
		top.add(buttons);
		top.add(Box.createVerticalStrut(6));

		countLabel.setForeground(Color.LIGHT_GRAY);
		top.add(countLabel);
		top.add(Box.createVerticalStrut(6));

		thumbs.setBackground(ColorScheme.DARK_GRAY_COLOR);
		add(top, BorderLayout.NORTH);
		add(thumbs, BorderLayout.CENTER);
	}

	/** Re-read the setting and the pictures. Call on the Swing thread; files are read in the background. */
	void refresh()
	{
		enabled.setSelected(plugin.isBurstEnabled());
		plugin.executor().execute(() ->
		{
			List<Filepath> files = plugin.listPictures();
			List<JLabel> labels = new ArrayList<>();
			for (Filepath f : files)
			{
				JLabel l = new JLabel();
				l.setPreferredSize(new Dimension(THUMB, THUMB));
				l.setToolTipText(f.getFileName());
				BufferedImage img = FancyHitsplatsPlugin.readImage(f);
				if (img != null)
				{
					double s = THUMB / (double) Math.max(img.getWidth(), img.getHeight());
					l.setIcon(new ImageIcon(img.getScaledInstance(Math.max(1, (int) (img.getWidth() * s)),
						Math.max(1, (int) (img.getHeight() * s)), Image.SCALE_SMOOTH)));
				}
				else
				{
					l.setText("?");
				}
				labels.add(l);
			}
			SwingUtilities.invokeLater(() ->
			{
				countLabel.setText(files.isEmpty() ? "No pictures yet - click Add pictures." : files.size() + " picture(s)");
				thumbs.removeAll();
				labels.forEach(thumbs::add);
				thumbs.revalidate();
				thumbs.repaint();
			});
		});
	}

	private void addPictures()
	{
		List<Filepath> chosen = new Filepath.Chooser()
			.setIsOpen()
			.setAcceptsFiles()
			.setMultiSelectionEnabled(true)
			.setDialogTitle("Pick pictures for the image burst")
			.addExtensionFilter("Pictures (PNG, JPG, GIF)", "png", "jpg", "jpeg", "gif")
			.showDialog(this);
		if (chosen == null || chosen.isEmpty())
		{
			return;
		}
		plugin.setBurstEnabled(true); // adding pictures means you want to see them
		plugin.addPictures(chosen);
	}

	private void confirmRemoveAll()
	{
		int answer = JOptionPane.showConfirmDialog(this, "Remove all burst pictures?", "Fancy Hitsplats",
			JOptionPane.YES_NO_OPTION);
		if (answer == JOptionPane.YES_OPTION)
		{
			plugin.removeAllPictures();
		}
	}
}
