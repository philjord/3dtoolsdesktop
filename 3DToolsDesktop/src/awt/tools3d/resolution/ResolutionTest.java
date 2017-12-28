package awt.tools3d.resolution;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.WindowConstants;

import org.jogamp.java3d.Alpha;
import org.jogamp.java3d.Appearance;
import org.jogamp.java3d.BoundingSphere;
import org.jogamp.java3d.BranchGroup;
import org.jogamp.java3d.Canvas3D;
import org.jogamp.java3d.GeometryArray;
import org.jogamp.java3d.PolygonAttributes;
import org.jogamp.java3d.RotationInterpolator;
import org.jogamp.java3d.Shape3D;
import org.jogamp.java3d.Transform3D;
import org.jogamp.java3d.TransformGroup;
import org.jogamp.java3d.TriangleArray;
import org.jogamp.java3d.compressedtexture.CompressedTextureLoader;
import org.jogamp.java3d.utils.universe.SimpleUniverse;
import org.jogamp.vecmath.Color3f;
import org.jogamp.vecmath.Point3d;
import org.jogamp.vecmath.Point3f;

import com.jogamp.newt.event.KeyAdapter;
import com.jogamp.newt.event.KeyEvent;

public final class ResolutionTest
{
	private final JFrame win;

	private Canvas3D canvas3D;

	private ResolutionTest()
	{
		//note win construction MUST occur before asking for graphics environment etc.
		win = new JFrame("Fullscreen Example");
		win.setVisible(true);
		win.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

		JMenuBar menubar = new JMenuBar();
		JMenu fileMenu = new JMenu("File");
		JMenuItem setGraphicsMenuItem = new JMenuItem("Set Graphics");
		fileMenu.add(setGraphicsMenuItem);
		menubar.add(fileMenu);
		win.setJMenuBar(menubar);

		canvas3D = new Canvas3D();
		//win.add(canvas3D);
		canvas3D.addNotify();

		GraphicsSettings gs = ScreenResolution.organiseResolution(null, win, false, true, true);

		canvas3D.getGLWindow().addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e)
			{
				final int keyCode = e.getKeyCode();
				if ((keyCode == KeyEvent.VK_ESCAPE) || ((keyCode == KeyEvent.VK_C) && e.isControlDown()))
				{
					System.exit(0);
				}
			}
		});

		SimpleUniverse su = new SimpleUniverse(canvas3D);
		su.getViewingPlatform().setNominalViewingTransform(); // back away from object a little
		su.addBranchGraph(createSceneGraph());

		canvas3D.getView().setSceneAntialiasingEnable(gs.isAaRequired());
		CompressedTextureLoader.setAnisotropicFilterDegree(gs.getAnisotropicFilterDegree());

		// don't bother super fast for now
		//ConsoleFPSCounter fps = new ConsoleFPSCounter();
		//su.addBranchGraph(fps.getBehaviorBranchGroup());

		setGraphicsMenuItem.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e)
			{
				GraphicsSettings gs = ScreenResolution.organiseResolution(null, win, false, false, false);

				canvas3D.getView().setSceneAntialiasingEnable(gs.isAaRequired());
				CompressedTextureLoader.setAnisotropicFilterDegree(gs.getAnisotropicFilterDegree());

			}
		});

	}

	/**
	 * Builds a scenegraph for the application to render.
	 * @return the root level of the scenegraph
	 */
	private static BranchGroup createSceneGraph()
	{
		final BranchGroup objRoot = new BranchGroup();

		// Create a triangle with each point a different color.  Remember to
		// draw the points in counter-clockwise order.  That is the default
		// way of determining which is the front of a polygon.
		//        o (1)
		//       / \
		//      /   \
		// (2) o-----o (0)
		Shape3D shape = new Shape3D();
		TriangleArray tri = new TriangleArray(3, GeometryArray.COORDINATES | GeometryArray.COLOR_3);
		tri.setCoordinate(0, new Point3f(0.5f, 0.0f, 0.0f));
		tri.setCoordinate(1, new Point3f(0.0f, 0.5f, 0.0f));
		tri.setCoordinate(2, new Point3f(-0.5f, 0.0f, 0.0f));
		tri.setColor(0, new Color3f(1.0f, 0.0f, 0.0f));
		tri.setColor(1, new Color3f(0.0f, 1.0f, 0.0f));
		tri.setColor(2, new Color3f(0.0f, 0.0f, 1.0f));

		// Because we're about to spin this triangle, be sure to draw
		// backfaces.  If we don't, the back side of the triangle is invisible.
		Appearance ap = new Appearance();
		PolygonAttributes pa = new PolygonAttributes();
		pa.setCullFace(PolygonAttributes.CULL_NONE);
		ap.setPolygonAttributes(pa);
		shape.setAppearance(ap);

		// Set up a simple RotationInterpolator
		BoundingSphere bounds = new BoundingSphere(new Point3d(0.0, 0.0, 0.0), 5.0);
		TransformGroup tg = new TransformGroup();
		Transform3D yAxis = new Transform3D();
		Alpha rotationAlpha = new Alpha(-1, 4000);
		tg.setCapability(TransformGroup.ALLOW_TRANSFORM_WRITE);
		RotationInterpolator rotator = new RotationInterpolator(rotationAlpha, tg, yAxis, 0.0f, (float) Math.PI * 2.0f);
		rotator.setSchedulingBounds(bounds);

		shape.setGeometry(tri);
		tg.addChild(rotator);
		tg.addChild(shape);
		objRoot.addChild(tg);
		objRoot.compile();
		return objRoot;
	}

	public static void main(String[] args)
	{
		new ResolutionTest();
	}
}
