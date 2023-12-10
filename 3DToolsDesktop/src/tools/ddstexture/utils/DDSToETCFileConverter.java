package tools.ddstexture.utils;

import java.awt.Color;
import java.awt.FlowLayout;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.Buffer;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.prefs.Preferences;

import javax.swing.JFrame;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.jogamp.java3d.Alpha;
import org.jogamp.java3d.BoundingSphere;
import org.jogamp.java3d.BranchGroup;
import org.jogamp.java3d.Canvas3D;
import org.jogamp.java3d.GeometryArray;
import org.jogamp.java3d.IndexedTriangleStripArray;
import org.jogamp.java3d.NioImageBuffer;
import org.jogamp.java3d.PolygonAttributes;
import org.jogamp.java3d.RotationInterpolator;
import org.jogamp.java3d.Shape3D;
import org.jogamp.java3d.Texture2D;
import org.jogamp.java3d.Transform3D;
import org.jogamp.java3d.TransformGroup;
import org.jogamp.java3d.compressedtexture.CompressedTextureLoader;
import org.jogamp.java3d.compressedtexture.dktxtools.dds.DDSDecompressor;
import org.jogamp.java3d.utils.shader.SimpleShaderAppearance;
import org.jogamp.java3d.utils.universe.SimpleUniverse;
import org.jogamp.vecmath.Point3d;
import org.jogamp.vecmath.Point3f;
import org.jogamp.vecmath.TexCoord2f;

import com.jogamp.newt.event.KeyAdapter;
import com.jogamp.newt.event.KeyEvent;

import compressedtexture.DDSImage;
import compressedtexture.KTXImage;
import compressedtexture.dktxtools.ktx.KTXFormatException;
import etcpack.ETCPack;
import etcpack.ETCPack.FORMAT;
import etcpack.QuickETC;
import javaawt.VMEventQueue;
import javaawt.image.VMBufferedImage;
import javaawt.imageio.VMImageIO;
import texture.DDSToKTXConverter;
import tools.swing.DetailsFileChooser;

/**
 * dds image loading tester, note this use the decompressor to buffered image util system not the jogl compressed call
 * @author philip
 *
 */
public class DDSToETCFileConverter {
	private static Preferences prefs;

	public static void main(String[] args) {
		prefs = Preferences.userNodeForPackage(DDSToETCFileConverter.class);

		DetailsFileChooser dfc = new DetailsFileChooser(prefs.get("DDSToTexture", ""),
				new DetailsFileChooser.Listener() {
					@Override
					public void directorySelected(File dir) {
						prefs.put("DDSToTexture", dir.getParentFile().getAbsolutePath());
						System.out.println("Selected dir: " + dir);
						processDir(dir);
					}

					@Override
					public void fileSelected(File file) {
						prefs.put("DDSToTexture", file.getAbsolutePath());
						System.out.println("Selected file: " + file);
						processImage(file, 15000);
					}
				});

		dfc.setFileFilter(new FileNameExtensionFilter("dds", "dds"));
		((JFrame)dfc.getTopLevelAncestor()).setLocationRelativeTo(null);
	}

	
	
	
	private static void processDir(File dir) {
		System.out.println("Processing directory " + dir);
		long startTime = System.currentTimeMillis();
		File[] fs = dir.listFiles();

		ExecutorService es = Executors.newFixedThreadPool(10);
		List<Callable<Object>> todo = new ArrayList<Callable<Object>>();

		// files then dirs
		for (int i = 0; i < fs.length; i++) {
			final int ii = i;
			try {
				if (fs[i].isFile() && fs[i].getName().endsWith(".dds")) {
					todo.add(Executors.callable(new Runnable() {
						@Override
						public void run() {
							//System.out.println("File: " + fs[ii]);
							processImage(fs[ii], 0);
						}
					}));
				}
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
		
		try {
			List<Future<Object>> answers = es.invokeAll(todo);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		todo.clear();

		for (int i = 0; i < fs.length; i++) {
			try {
				if (fs[i].isDirectory()) {
					processDir(fs[i]);
				}
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}

		System.out.println(
				"Finished processing directory " + dir + " in " + (System.currentTimeMillis() - startTime) + "ms");
	}

	public static void processImage(File file, long stayTime) {
		String filename = file.getAbsolutePath();
		try {
			convertImage(filename, new FileInputStream(file));
			//showImage(filename, new FileInputStream(file), stayTime);

			//TODO: this should be fine, but shows blank?
			//showImageInShape(filename, new FileInputStream(file));
		} catch (IOException e) {
			System.out.println(
					"" + DDSToETCFileConverter.class + " had a  IO problem with " + filename + " : " + e.getMessage());
		}

	}

	public static void convertImage(String filename, InputStream inputStream) {
		long tstart = System.currentTimeMillis();
		ByteBuffer ktxBB = DDSToKTXConverter.convertDDSToKTX(inputStream, filename);

		if (ktxBB != null) {
			try {
				KTXImage ktxImage = new KTXImage(ktxBB);
			} catch (KTXFormatException e) {
				System.out.println("DDS to KTX image: " + filename);
				e.printStackTrace();
			} catch (IOException e) {
				System.out.println("DDS to KTX image: " + filename);
				e.printStackTrace();
			} catch (BufferOverflowException e) {
				System.out.println("DDS to KTX image: " + filename);
				e.printStackTrace();
			}
			
			String outfilename = filename.replace(".dds",".ktx");
			
			//move from game_media to temp, otherwise just next to itself
			if(outfilename.indexOf("game_media") != -1) {
				outfilename = "D:\\temp\\" + outfilename.substring(outfilename.indexOf("game_media"));
			}
			File file = new File(outfilename);
			file.getParentFile().mkdirs();
			RandomAccessFile raf = null;
			try {
				raf = new RandomAccessFile(outfilename, "rw");
				FileChannel fc = raf.getChannel();
				ktxBB.rewind();
				fc.write(ktxBB);
				fc.close();
				ktxBB.rewind();
			} catch (IOException e1) {
				e1.printStackTrace();
			} finally {
				if (raf != null) {
					try {
						raf.close();
					} catch (Exception e) {
					}
				}
			}

			System.out.println(""	+ (System.currentTimeMillis() - tstart) + "ms to compress " + filename + " to "
								+ outfilename);

		} else {
			System.err.println("Not a DDSToKTXConverter.convertDDSToKTX returned null for " + filename);
		} 
	}

	public static void showImage(String filename, InputStream inputStream, final long stayTime) {
		// we are about to decompresed to buffered image below, we always need the VM support
		javaawt.image.BufferedImage.installBufferedImageDelegate(VMBufferedImage.class);
		javaawt.imageio.ImageIO.installBufferedImageImpl(VMImageIO.class);
		javaawt.EventQueue.installEventQueueImpl(VMEventQueue.class);

		final JFrame f = new JFrame();
		f.getContentPane().setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0));
		f.getContentPane().setBackground(new Color(255, 0, 255));
 
		
		ByteBuffer ktxBB = DDSToKTXConverter.convertDDSToKTX(inputStream, filename);

		if (ktxBB != null) {
			try {
				KTXImage ktxImage = new KTXImage(ktxBB);
			} catch (KTXFormatException e) {
				System.out.println("DDS to KTX image: " + filename);
				e.printStackTrace();
			} catch (IOException e) {
				System.out.println("DDS to KTX image: " + filename);
				e.printStackTrace();
			} catch (BufferOverflowException e) {
				System.out.println("DDS to KTX image: " + filename);
				e.printStackTrace();
			}

		} else {
			System.err.println("Not a DDSToKTXConverter.convertDDSToKTX returned null for " + filename);
		}
	}

	//Method to show teh ETC texture method in a 3d scene
	public static void showImageInShape(String filename, InputStream inputStream) {
		//note win construction MUST occur beofre asking for graphics environment etc.
		//JFrame win = new JFrame("Fullscreen Example");
		//win.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

		Canvas3D canvas3D = new Canvas3D();
		//win.add(canvas3D);
		canvas3D.addNotify();

		//GraphicsSettings gs = ScreenResolution.organiseResolution(null, win, false, true, true);

		canvas3D.getGLWindow().addKeyListener(new KeyAdapter() {
			public void keyPressed(KeyEvent e) {
				final int keyCode = e.getKeyCode();
				if ((keyCode == KeyEvent.VK_ESCAPE) || ((keyCode == KeyEvent.VK_C) && e.isControlDown())) {
					System.exit(0);
				}
			}
		});

		SimpleUniverse su = new SimpleUniverse(canvas3D);
		su.getViewingPlatform().setNominalViewingTransform(); // back away from object a little
		su.addBranchGraph(createSceneGraph(filename, inputStream));

		//canvas3D.getView().setSceneAntialiasingEnable(gs.isAaRequired());
		//CompressedTextureLoader.setAnisotropicFilterDegree(gs.getAnisotropicFilterDegree());

		// don't bother super fast for now
		//ConsoleFPSCounter fps = new ConsoleFPSCounter();
		//su.addBranchGraph(fps.getBehaviorBranchGroup());

	}

	/**
	 * Builds a scenegraph for the application to render.
	 * @return the root level of the scenegraph
	 */
	private static BranchGroup createSceneGraph(String filename, InputStream inputStream) {
		final BranchGroup objRoot = new BranchGroup();

		Shape3D shape = new Shape3D();
		IndexedTriangleStripArray tri = new IndexedTriangleStripArray(4,
				GeometryArray.USE_COORD_INDEX_ONLY | GeometryArray.COORDINATES | GeometryArray.TEXTURE_COORDINATE_2, 6,
				new int[] {6});
		tri.setCoordinate(0, new Point3f(-0.5f, 0.0f, 0.0f));
		tri.setCoordinate(1, new Point3f(0.5f, 0.0f, 0.0f));
		tri.setCoordinate(2, new Point3f(0.5f, 0.5f, 0.0f));
		tri.setCoordinate(3, new Point3f(-0.5f, 0.5f, 0.0f));

		tri.setTextureCoordinate(0, 0, new TexCoord2f(0.0f, 0.0f));
		tri.setTextureCoordinate(0, 1, new TexCoord2f(1.0f, 0.0f));
		tri.setTextureCoordinate(0, 2, new TexCoord2f(1.0f, 1.0f));
		tri.setTextureCoordinate(0, 3, new TexCoord2f(0.0f, 1.0f));

		tri.setCoordinateIndex(0, 0);
		tri.setCoordinateIndex(0, 1);
		tri.setCoordinateIndex(0, 2);
		tri.setCoordinateIndex(0, 0);
		tri.setCoordinateIndex(0, 3);
		tri.setCoordinateIndex(0, 2);

		// Because we're about to spin this triangle, be sure to draw
		// backfaces.  If we don't, the back side of the triangle is invisible.
		SimpleShaderAppearance ap = new SimpleShaderAppearance();
		PolygonAttributes pa = new PolygonAttributes();
		pa.setCullFace(PolygonAttributes.CULL_NONE);
		ap.setPolygonAttributes(pa);
		shape.setAppearance(ap);

		ap.setTexture(CompressedTextureLoader.DDS.getTexture(filename, inputStream));

		// Set up a simple RotationInterpolator
		BoundingSphere bounds = new BoundingSphere(new Point3d(0.0, 0.0, 0.0), 5.0);
		TransformGroup tg = new TransformGroup();
		Transform3D yAxis = new Transform3D();
		Alpha rotationAlpha = new Alpha(-1, 4000);
		tg.setCapability(TransformGroup.ALLOW_TRANSFORM_WRITE);
		RotationInterpolator rotator = new RotationInterpolator(rotationAlpha, tg, yAxis, 0.0f, (float)Math.PI * 2.0f);
		rotator.setSchedulingBounds(bounds);

		shape.setGeometry(tri);
		tg.addChild(rotator);
		tg.addChild(shape);
		objRoot.addChild(tg);
		objRoot.compile();
		return objRoot;
	}

}
