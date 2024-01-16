package tools.ddstexture.utils;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.prefs.Preferences;

import javax.swing.JFrame;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.jogamp.java3d.compressedtexture.CompressedTextureLoader;

import compressedtexture.KTXImage;
import etcpack.ETCPack;
import tools.swing.DetailsFileChooser;

/**
 * dds image loading tester, note this use the decompressor to buffered image not the jogl compressed call
 * 
 * For showing compressed texture on the pipeline use Texture2DDisplay in 3DTools
 * @author philip
 *
 */
public class KTXTextureLoaderTester {
	private static Preferences prefs;

	public static void main(String[] args) {
		prefs = Preferences.userNodeForPackage(KTXTextureLoaderTester.class);

		DetailsFileChooser dfc = new DetailsFileChooser(prefs.get("KTXToTexture", ""),
				new DetailsFileChooser.Listener() {
					@Override
					public void directorySelected(File dir) {
						prefs.put("KTXToTexture", dir.getAbsolutePath());
						System.out.println("Selected dir: " + dir);
						processDir(dir);
					}

					@Override
					public void fileSelected(File file) {
						prefs.put("KTXToTexture", file.getAbsolutePath());
						System.out.println("Selected file: " + file);
						showImage(file, -1);
					}
				});

		dfc.setFileFilter(new FileNameExtensionFilter("ktx", "ktx"));
	}

	private static void processDir(File dir) {
		System.out.println("Processing directory " + dir);
		File[] fs = dir.listFiles();
		for (int i = 0; i < fs.length; i++) {
			try {
				if (fs[i].isFile() && fs[i].getName().endsWith(".dktxds")) {
					System.out.println("\tFile: " + fs[i]);
					showImage(fs[i], -1);

					//pause between each show to give it a chance to show
					try {
						Thread.sleep(200);
					} catch (InterruptedException e) {
					}
				} else if (fs[i].isDirectory()) {
					processDir(fs[i]);
				}

			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
	}

	public static void showImage(File file, long stayTime) {
		String filename = file.getAbsolutePath();
		try {
			showImage(filename, new FileInputStream(file), stayTime);
		} catch (IOException e) {
			System.out.println(
					"" + KTXTextureLoaderTester.class + " had a  IO problem with " + filename + " : " + e.getMessage());
		}

	}

	static JFrame f;

	public static void showImage(String filename, InputStream inputStream, final long stayTime) {
		if (f == null) {

			f = new JFrame();
			f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
			f.getContentPane().setLayout(new BorderLayout());
			f.getContentPane().setBackground(new Color(255, 0, 255));
			f.setLocationRelativeTo(null);
			f.setAutoRequestFocus(false);
			f.setVisible(true);
		}

		try {
			ByteBuffer bb = CompressedTextureLoader.toByteBuffer(inputStream);
			int[] w = new int[1];
			int[] h = new int[1];

			ETCPack ep = new ETCPack();
			byte[] rawBytes = ep.uncompressImageFromByteBuffer(bb, w, h, true);
			if (rawBytes != null) {
				ByteBuffer buffer = ByteBuffer.wrap(rawBytes);
				int width = w[0];
				int height = h[0];

				BufferedImage delegate = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
				int[] pixels = new int[width * height];
				// reverse to flip Y
				//for (int y = height - 1; y >= 0; y--) {
				//	for (int x = 0; x < width; x++) {
				for (int y = 0; y < height; y++) {
					for (int x = 0; x < width; x++) {
						pixels[(y * width) + x] = ((buffer.get() & 0xff) << 24	| (buffer.get() & 0xff) << 16
													| (buffer.get() & 0xff) << 8 | (buffer.get() & 0xff) << 0);
					}
				}
				delegate.setRGB(0, 0, width, height, pixels, 0, width);

				f.setVisible(false);
				//ImageIcon icon = new ImageIcon(delegate);

				f.getContentPane().removeAll();
				//f.getContentPane().add(new JLabel(icon));
				f.setTitle(filename);

				f.setSize(width + f.getInsets().left + f.getInsets().right,
						height + f.getInsets().top + f.getInsets().bottom);
				f.setVisible(true);

				f.getContentPane().add(new ScalablePane(delegate, true, false));
				f.pack();
				f.setVisible(true);
			}

		} catch (IOException e) {
			System.out.println(
					"" + KTXTextureLoaderTester.class + " had a  IO problem with " + filename + " : " + e.getMessage());
			return;
		}
		if (stayTime > 0) {
			Thread t = new Thread() {
				@Override
				public void run() {
					try {
						Thread.sleep(stayTime);
					} catch (InterruptedException e) {
					}
					f.dispose();
				}
			};
			t.start();
		}
	}
}
