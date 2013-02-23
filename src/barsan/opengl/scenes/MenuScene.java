package barsan.opengl.scenes;

import java.awt.Color;
import java.awt.Font;
import java.awt.event.KeyEvent;
import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.List;

import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;

import com.jogamp.graph.curve.opengl.TextRenderer;

import barsan.opengl.Yeti;
import barsan.opengl.input.CameraInput;
import barsan.opengl.input.InputAdapter;
import barsan.opengl.input.InputProvider;
import barsan.opengl.rendering.Billboard;
import barsan.opengl.rendering.ModelInstance;
import barsan.opengl.rendering.Scene;
import barsan.opengl.resources.ResourceLoader;
import barsan.opengl.util.SceneHelper;
import barsan.opengl.util.TextHelper;

public class MenuScene extends Scene {

	protected CameraInput cameraInput;

	public interface MenuAction {
		public void performAction();
	}
	
	public static class TransitionAction implements MenuAction {
		private Scene target;
		
		public TransitionAction(Scene target) {
			this.target = target;
		}
		
		@Override
		public void performAction() {
			Yeti.get().loadScene(target);
		}
	}
	
	public static class DummyAction implements MenuAction {
		public void performAction() { }
	}
	
	public static class ExitAction implements MenuAction {
		@Override
		public void performAction() {
			Yeti.quit();
		}
	}
	
	public static class Menu {
		private List<MenuEntry> entries = new ArrayList<>();
		private Font font = new Font("serif", Font.BOLD, 48);
		private FontRenderContext context = new FontRenderContext(new AffineTransform(), true, false);
		private int index = 0;
		
		public class MenuEntry {
			private String text;
			private MenuAction action;
			private boolean selected;
			private boolean centered = true;
			private float widthCache = -1.0f;
			
			public MenuEntry(String text, MenuAction action) {
				this.setText(text);
				this.action = action;
			}
			
			public void activate() {
				action.performAction();
			}
			
			public String getText() {
				return text;
			}
			
			public void setText(String text) {
				this.text = text;
				widthCache = (float) font.getStringBounds(text, context).getWidth();
			}
			
			public void draw(int x, int y) {
				if(centered) {
					x -= widthCache / 2;
				}
				TextHelper.drawText(x, y, text, selected ? Color.YELLOW : Color.WHITE);
			}
		}
		
		
		public void addEntry(MenuEntry entry) {
			entries.add(entry);
			entries.get(0).selected = true;
		}
		
		public void draw() {
			TextHelper.setFont(font);
			
			int x = Yeti.get().settings.width / 2;
			int y = 320;
			int step = 42;
			
			for(int i = 0; i < entries.size(); i++) {
				entries.get(i).draw(x, y - i * step);
			}
		}
		
		public void goUp() {
			entries.get(index).selected = false;
			
			index--;
			if(index < 0) index = entries.size() - 1;
			
			entries.get(index).selected = true;
		}
		
		public void goDown() {
			entries.get(index).selected = false;
			
			index++;
			if(index >= entries.size()) index = 0;
			
			entries.get(index).selected = true;
		}
		
		public void activate() {
			entries.get(index).activate();
		}
		
		public void mouseUpdated(int newX, int newY) {
			// TODO: when mouse moved, check every entry's bounding rectangle
		}
	}
	
	private Menu menu = new Menu();
	
	@Override
	public void init(GLAutoDrawable drawable) {
		super.init(drawable);
		
		SceneHelper.quickSetup2D(this);
		ResourceLoader.loadTexture("background", "menuBackground.png");
		
		Billboard bb;
		addBillboard(bb = new Billboard(Yeti.get().gl, ResourceLoader.texture("background")));
		bb.getTransform().updateTranslate(0.0f, 0.0f, 0.0f);
		
		menu.addEntry(menu.new MenuEntry("Begin!", new TransitionAction(new GameScene())));
		menu.addEntry(menu.new MenuEntry("Light test", new TransitionAction(new LightTest())));
		menu.addEntry(menu.new MenuEntry("About", new DummyAction()));
		menu.addEntry(menu.new MenuEntry("Exit", new ExitAction()));
		
		addInput(new InputAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				if(e.getKeyCode() == KeyEvent.VK_UP) {
					menu.goUp();
				} else if(e.getKeyCode() == KeyEvent.VK_DOWN) {
					menu.goDown();
				} else if(e.getKeyCode() == KeyEvent.VK_SPACE
						|| e.getKeyCode() == KeyEvent.VK_ENTER) {
					menu.activate();
				}
			}
		});
	}
	
	@Override
	public void display(GLAutoDrawable drawable) {
		// Ideally, using a designated 2D text & sprite renderer would be the best idea.
		if(exiting) {
			exit();
			return;
		}
		
		GL2 gl = Yeti.get().gl;
		gl.glClear(GL2.GL_COLOR_BUFFER_BIT | GL2.GL_DEPTH_BUFFER_BIT);
		
		super.display(drawable);
		
		TextHelper.beginRendering(camera.getWidth(), camera.getHeight());
		{
			menu.draw();
		}
		TextHelper.endRendering();
	}
}