package barsan.opengl;

import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import javax.media.opengl.DebugGL3bc;
import javax.media.opengl.GL3bc;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GLCapabilities;
import javax.media.opengl.GLEventListener;
import javax.media.opengl.GLProfile;

import barsan.opengl.editor.App;
import barsan.opengl.input.GlobalConsole;
import barsan.opengl.input.InputProvider;
import barsan.opengl.platform.CanvasFactory;
import barsan.opengl.rendering.Scene;
import barsan.opengl.rendering.lights.DirectionalLight;
import barsan.opengl.rendering.lights.Light;
import barsan.opengl.rendering.lights.Light.LightType;
import barsan.opengl.rendering.lights.PointLight;
import barsan.opengl.rendering.lights.SpotLight;
import barsan.opengl.resources.ResourceLoader;
import barsan.opengl.scenes.DemoScene;
import barsan.opengl.scenes.GameScene;
import barsan.opengl.scenes.LightTest;
import barsan.opengl.scenes.MenuScene;
import barsan.opengl.scenes.NessieTestScene;
import barsan.opengl.scenes.ProceduralScene;
import barsan.opengl.util.ConsoleRenderer;
import barsan.opengl.util.ReflectUtil;
import barsan.opengl.util.Settings;

import com.jogamp.opengl.util.Animator;

/**
 * The Yeti OpenGL game engine. Licensed under the BSD 2-clause license.
 * @author Andrei Barsan
 */
public class Yeti implements GLEventListener {
	
	// Miscellaneous settings
	public Settings settings;
	
	// Logging flags
	public boolean warnings = true;
	public boolean debug = true;
	
	// TODO: scene manager with a stack / graph of scenes
	private Scene currentScene;
	private Scene defaultScene;
	private Frame frame;
	private Cursor blankCursor;
	
	// I draw on this
	private Component canvasHost;
	
	// Swing application Yeti is hosted in (can be null if Yeti is runing in a
	// "dedicated" AWT frame.
	private App hostApp;
	
	// Active OpenGL context. Use GL3.0 by default, with backwards compatibility
	// for the fixed pipeline to allow debug drawing.
	public GL3bc gl;
	
	// Keeps everything in sync.
	private final Animator animator;
	
	boolean pendingInit = false;	// TODO: better interaction with input thread
	private Scene pendingScene;		// Used in transitions
	
	// Detects abnormal contex resets
	boolean engineInitialized = false;
	
	// Renders the input console, when needed
	ConsoleRenderer consoleRenderer;
	
	// Timing
	private long lastFrameStart;
	private long thisFrameStart;
	private float delta;
	
	/**
	 * Iterating through a package to find its classes is not as trivial
	 * as it might seem, so this will do. It's just tempanent anyway.
	 */
	static Class<?>[] availableScenes = new Class[] {
			DemoScene.class,
			ProceduralScene.class,
			LightTest.class,
			GameScene.class,
			MenuScene.class,
			NessieTestScene.class
	};
	static {		
		for(Class<?> c : availableScenes) {
			assert Scene.class.isAssignableFrom(c) : "Only instances of Scene allowed!";
		}
	}
	
	public static Class<?>[] getAvailableScenes() {
		return availableScenes;
	}
	
	private Yeti() {
		animator = new Animator();
	}
	
	private void startup() {
		settings = Settings.load();
		debug("Starting up Yeti...");
		
		// Setup transient fields
		if(settings.width == 0) {
			settings.width = 1024;
		}
		
		if(settings.height == 0) {
			settings.height = 768;
		}
		
		settings.playing = false;
		
		// Create blank cursor (used for hidin the mouse)
		// Transparent 16 x 16 pixel cursor image.
		BufferedImage cursorImg = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		blankCursor = Toolkit.getDefaultToolkit()
				.createCustomCursor(cursorImg, new Point(0, 0), "blank cursor");

	}
	
	public void transitionFinished() {
		currentScene = pendingScene;
		pendingInit = true;
	}
	
	public void loadScene(Scene newScene) {
		pendingScene = newScene;
		
		if(currentScene != null) {	
			currentScene.beginExit(this, newScene);
		} else {
			// No scene to transition out of, so we're instantly finished
			transitionFinished();
		}
	}
	
	public String executeCommand(String input) {
		String[] parts = input.split("\\s");
		
		if(parts.length == 0) {
			return "";
		}
		
		String cmd = parts[0];
		String[] args = Arrays.copyOfRange(parts, 1, parts.length);
		
		switch(cmd) {
		case "derp":
			return "HERP";
			
		case "exit":
		case "quit":
			Yeti.quit();
			return "Shutting down Yeti...";
			
		case "ls":
		case "scenes":
			String out = "Available scenes: ";
			for(Class<?> c : availableScenes) {
				out += c.getSimpleName() + "\n";
			}
			return out;
			
		case "l":
			Scene s = Yeti.get().currentScene;
			try {
				if(args.length <= 2) {
					return "At least 3 parameters needed.";
				}
				
				int id = Integer.parseInt(args[0]);
				List<Light> la = s.getLights();
				if(la.size() <= id) {
					return "Only " + la.size() + " lights available."; 
				}
				
				Light currentLight = la.get(id);
				Class<?> LC = 
					currentLight.getType() == LightType.Directional ? DirectionalLight.class
					: currentLight.getType() == LightType.Point ?	PointLight.class
					: SpotLight.class;
				
				Field lightFields[] = LC.getDeclaredFields();
				Field target = null;
				for(Field f : lightFields) {
					if(f.getName().toUpperCase().equals(args[1].toUpperCase())) {
						target = f;
						break;
					}
				}
				
				if(target == null) {
					return "Field " + args[1] + " not found.";
				}
				
				try {
					String parseParams[] = Arrays.copyOfRange(args, 2, args.length);
					ReflectUtil.setParameter(currentLight, target, parseParams);
					return "Set field " + target.getName() + " to " + Arrays.toString(parseParams);
					
				} catch(IllegalArgumentException | IllegalAccessException e) {
					e.printStackTrace();
					return "Bad parameter value(s)";
				}
			
			} catch(NumberFormatException e) {
				return "The first parameter must be a number.";
			}
			
		case "rs":
			// refresh shaders
			// Note: this can also be made into an utility that monitors file 
			// changes and recompiles shaders on the fly
			break;
			
		case "load":
			if(args.length > 0) {
				boolean found = false;
				for(Class<?> c : availableScenes)
				{
					if(c.getSimpleName().toUpperCase().equals(args[0].toUpperCase()))
					{
						found = true;
						try {
							loadScene((Scene) c.newInstance());
						} catch (InstantiationException e) {
							e.printStackTrace();
						} catch (IllegalAccessException e) {
							e.printStackTrace();
						}
						return "Loading scene: " + c.getSimpleName();
					}
				}
				if( ! found) {
					return "Scene " + args[0] + " not found.";
				}
			}
			else 
			{
				return "Please specify a scene name";
			}
			break;
		}
		
		return "Unknown command: " + cmd;		
	}
	
	/**
	 * Starts the Yeti application by creating the GL canvas and starting the 
	 * application loop.
	 *  
	 * @param app When hosted in a compatible app, a reference to the app that
	 * allows Yeti to send various data to the app. App can pe left null signifying
	 * that there is no app to communicate with (for instance, in the case of a
	 * game that doesn't require any e.g. Swing controls, apart from the canvas
	 * itself).
	 *  
	 * @param frame The window frame hosting Yeti. Can be the same as the host container.
	 * @param hostContainer The host container holding the canvas. Can be the frame itself.
	 * @param canvasFactory The factory providing the actual canvas on which Yeti
	 * is going to draw.
	 */
	public void startApplicationLoop(App app, Frame frame, Container hostContainer,
			CanvasFactory canvasFactory) {
		
		frame.addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				new Thread(new Runnable() {
					public void run() {
						animator.stop();
						System.exit(0);
					}
				}).start();
			}
		});
				
		this.frame = frame;
		
		if(app != null) {
			this.hostApp = app;
		}
		
		GLProfile.initSingleton();
		GLProfile glp = GLProfile.get(GLProfile.GL3bc);
		GLCapabilities capabilities = new GLCapabilities(glp);
		
		final Component glpanel = canvasFactory.createCanvas(capabilities);
		assert glpanel instanceof GLAutoDrawable : "A panel must be able to interact with OpenGL (implement GLAutoDrawable).";
		final GLAutoDrawable gld = (GLAutoDrawable) glpanel;
		
		gld.addGLEventListener(this);
		
		animator.add(gld);
		hostContainer.add(glpanel);
		
		canvasHost = glpanel;		
		canvasHost.setPreferredSize(new Dimension(settings.width, settings.height));
		frame.pack();
		frame.setVisible(true);
		
		animator.setUpdateFPSFrames(5, null);
		animator.start();
		
		glpanel.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				focusMouse();
			}
		});
		
		focusMouse();
	}
	
	public void focusMouse() {
		canvasHost.requestFocus();
		canvasHost.setCursor(blankCursor);
		settings.playing = true;
		if(currentScene != null)
			currentScene.play();
	}
	
	public float getDelta() {
		return delta;
	}
	
	/**
	 * Pause the interactive components of the running simulation.
	 */
	public void pause() {
		settings.playing = false;
		canvasHost.setCursor(Cursor.getDefaultCursor());
		currentScene.pause();		
	}
	
	private static Yeti instance;
	public static Yeti get() {
		if(instance == null) {
			instance = new Yeti();
			instance.startup();
		}
		return instance;
	}
	
	public static void debug(String message) {
		if(get().debug) System.out.printf("[DEBUG] %s\n", message);
	}
	
	public static void debug(String format, Object... stuff) {
		if(get().debug) System.out.printf("[DEBUG] " + format + "\n", stuff);
	}
	
	public static void warn(String message) {
		if(get().warnings) System.err.printf("[WARNING] %s\n", message);
	}
	
	public static void warn(String format, Object... stuff) {
		if(get().warnings) System.out.printf("[WARNING] " + format + "\n", stuff);
	}
	
	public static void screwed(String message) {
		System.err.flush();
		System.out.flush();
		System.err.printf("[FATAL] %s\n", message);
		if(Yeti.get().debug) {
			System.err.printf("Thread: %s\n", Thread.currentThread());
			StackTraceElement sad[] = new Throwable().getStackTrace();
			final int maxStack = 5;
			for(int i = 1; i < Math.min(maxStack + 1, sad.length); i++) {
				StackTraceElement el = sad[i];
				System.err.printf("\t- %s\n", el);
			}
		}
		System.exit(-1);
	}
	
	public static void screwed(String message, Throwable cause) {
		cause.printStackTrace();
		screwed(message);
	}
	
	public static void quit() {
		Yeti.debug("Shutting down...");
		Yeti.debug("Saving settings...");
		Settings.save(get().settings);
		Yeti.debug("Settings saved.");
		System.exit(0);		
	}
	
	@Override
	public void init(GLAutoDrawable drawable) {

		// Get the new context
		if(debug) {
			drawable.setGL(new DebugGL3bc(drawable.getGL().getGL3bc()));
		}
		gl = drawable.getGL().getGL3bc();
			
		if(engineInitialized) {
			Yeti.screwed("GL Context was reset. Yeti cannot handle that yet. :(");
			return;
		}
		
		if(hostApp != null) {
			// TODO: list of callbaks (also maybe make host apps implement some hook functionality)
			hostApp.generateGLKnobs(this);
		}
		
		// Only displays when actually in debug mode
		Yeti.debug("Running in debug GL mode"); 
		
		final int lastLoadedScene = settings.lastSceneIndex;
		ResourceLoader.init();	

		// Soon-to-be global controller of GL settings
		GlobalConsole console = new GlobalConsole();
		consoleRenderer = new ConsoleRenderer(console);
		addInputProvider(console);

		/*
		Thread.currentThread().setUncaughtExceptionHandler(new UncaughtExceptionHandler() {
			@Override
			public void uncaughtException(Thread t, Throwable e) {
				if(e instanceof GLException) {
					Yeti.screwed("GLException: \n" + e.getMessage(), e);
				} else {
					Yeti.screwed("Yeti general error: \n" + e);
				}
			}
		});
		//*/
		
		try {
			if( null != defaultScene) {
				loadScene(defaultScene);
			} else {
				loadScene((Scene)availableScenes[lastLoadedScene].newInstance());
			}
		} catch (InstantiationException | IllegalAccessException e) {
			e.printStackTrace();
		}
		
		engineInitialized = true;
		lastFrameStart = thisFrameStart = System.nanoTime();
	}
	
	@Override
	public void display(GLAutoDrawable drawable) {
		thisFrameStart = System.nanoTime();
		delta = (float) (( (double) thisFrameStart - lastFrameStart) / 1000000000d);
		if(pendingInit) {
			Yeti.debug("Pending init - so doing init! (display)");
			currentScene.init(drawable);
			pendingInit = false;
		}
		currentScene.display(drawable);
		consoleRenderer.render();
		lastFrameStart = thisFrameStart;
	}


	@Override
	public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
		if(pendingInit) {
			Yeti.debug("Pending init - so doing init! (reshape)");
			currentScene.init(drawable);
			pendingInit = false;
		}
		// TODO: if hosted in an application - make sure the sizes stay right
		// FIXME: currently makes the panel higher, although the originally 
		// requested size is 1024 x 768 (->1024x801)
		// Increasing toolbar height doesn't increase the height of the gray 
		// band. Then it has to be the window decorations.
		currentScene.reshape(drawable, x, y, width, height);
	}
	
	@Override
	public void dispose(GLAutoDrawable drawable) {
		ResourceLoader.cleanUp();
	}
	
	public void addInputProvider(InputProvider inputProvider) {
		if(inputProvider instanceof KeyListener) {
			frame.addKeyListener((KeyListener)inputProvider);
			canvasHost.addKeyListener((KeyListener)inputProvider);
		}
		
		if(inputProvider instanceof MouseListener) {
			frame.addMouseListener((MouseListener)inputProvider);
			canvasHost.addMouseListener((MouseListener)inputProvider);
		}
		
		if(inputProvider instanceof MouseMotionListener) {
			frame.addMouseMotionListener((MouseMotionListener)inputProvider);
			canvasHost.addMouseMotionListener((MouseMotionListener)inputProvider);
		}
		
		if(inputProvider instanceof MouseWheelListener) {
			frame.addMouseWheelListener((MouseWheelListener)inputProvider);
			canvasHost.addMouseWheelListener((MouseWheelListener)inputProvider);
		}
	}
	
	public void removeInputProvider(InputProvider inputProvider) {
		if(inputProvider instanceof KeyListener) {
			frame.removeKeyListener((KeyListener)inputProvider);
			canvasHost.removeKeyListener((KeyListener)inputProvider);
		}
		
		if(inputProvider instanceof MouseListener) {
			frame.removeMouseListener((MouseListener)inputProvider);
			canvasHost.removeMouseListener((MouseListener)inputProvider);
		}
		
		if(inputProvider instanceof KeyListener) {
			frame.removeMouseMotionListener((MouseMotionListener)inputProvider);
			canvasHost.removeMouseMotionListener((MouseMotionListener)inputProvider);
		}
		
		if(inputProvider instanceof MouseWheelListener) {
			frame.addMouseWheelListener((MouseWheelListener)inputProvider);
			canvasHost.addMouseWheelListener((MouseWheelListener)inputProvider);
		}
	}
	
	public Component getHostComponent() {
		return canvasHost;
	}
	
	public Frame getHostFrame() {
		return frame;
	}
	
	/**
	 * Returns the absolute X position of the canvas that the engine is 
	 * rendering on.
	 * 
	 * @return X position in pixels
	 */
	public int getCanvasX() {
		return canvasHost.getX() + frame.getX();
	}
	
	/**
	 * Returns the absolute X position of the canvas that the engine is 
	 * rendering on.
	 * 
	 * @return Y position in pixels
	 */
	public int getCanvasY() {
		return canvasHost.getY() + frame.getY();
	}
	
	public int getCanvasWidth() {
		return canvasHost.getWidth();
	}
	
	public int getCanvasHeight() {
		return canvasHost.getHeight();
		
	}
	
	public Scene getDefaultScene() {
		return defaultScene;
	}
	
	public void setDefaultScene(Scene defaultScene) {
		this.defaultScene = defaultScene;
	}
	
	boolean fullscreen = false;
	public void toggleFullscreen() {
		GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
		GraphicsDevice gd = ge.getDefaultScreenDevice();
		gd.setFullScreenWindow(frame);
	}
}
