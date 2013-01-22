package barsan.opengl.scenes;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.IOException;
import java.util.Collections;

import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.Threading;

import barsan.opengl.Yeti;
import barsan.opengl.math.Transform;
import barsan.opengl.math.Vector3;
import barsan.opengl.rendering.lights.PointLight;
import barsan.opengl.rendering.materials.BasicMaterial;
import barsan.opengl.rendering.materials.BumpComponent;
import barsan.opengl.rendering.materials.CubicEnvMappingMaterial;
import barsan.opengl.rendering.materials.Material;
import barsan.opengl.rendering.materials.TextureComponent;
import barsan.opengl.rendering.materials.ToonMaterial;
import barsan.opengl.rendering.Fog;
import barsan.opengl.rendering.Model;
import barsan.opengl.rendering.ModelInstance;
import barsan.opengl.rendering.PerspectiveCamera;
import barsan.opengl.rendering.Scene;
import barsan.opengl.rendering.SkyBox;
import barsan.opengl.resources.HeightmapBuilder;
import barsan.opengl.resources.ResourceLoader;
import barsan.opengl.util.Color;
import barsan.opengl.util.DebugGUI;
import barsan.opengl.util.TextHelper;

public class DemoScene extends Scene {
	
	PointLight pl;
	float a = 0.0f;
	Material ironbox;
	Material redShit, blueShit;
	boolean smoothRendering = true;
	
	SkyBox sb;
	Transform tct;
	
	ModelInstance m2;
	
	@Override
	public void init(GLAutoDrawable drawable) {
		super.init(drawable);
		
		try {
			ResourceLoader.loadObj("asteroid10k", "res/models/asteroid10k.obj");
			ResourceLoader.loadObj("asteroid1k", "res/models/asteroid1k.obj");
			ResourceLoader.loadObj("sphere", "res/models/prettysphere.obj");
			ResourceLoader.loadObj("bunny", "res/models/bunny.obj");
			ResourceLoader.loadObj("texcube", "res/models/texcube.obj");
			
			ResourceLoader.loadTexture("heightmap01", "res/tex/height.png");
			ResourceLoader.loadTexture("grass", "res/tex/grass01.jpg");
			ResourceLoader.loadTexture("stone", "res/tex/stone03.jpg");
			ResourceLoader.loadTexture("stone.bump", "res/tex/stone03.bump.jpg");
			ResourceLoader.loadTexture("billboard", "res/tex/tree_billboard.png");
			
			ResourceLoader.loadCubeTexture("skybox01", "jpg");
			
		} catch (IOException e) {
			System.out.println("Could not load the resources.");
			e.printStackTrace();
		}
		
		blueShit = new BasicMaterial(new Color(0.0f, 0.0f, 1.0f));
		redShit = new ToonMaterial(new Color(1.0f, 0.25f, 0.33f));
		
		// FIXME: this isn't right; the skybox should be drawn last in
		// order for as few fragments as possible to be processed, not first
		// so that we overwrite most of it!
		SkyBox sb = new SkyBox(Yeti.get().gl.getGL2(), ResourceLoader.cubeTexture("skybox01"), camera);
		modelInstances.add(sb);
		
		blueShit.setShininess(16);
		
		Model groundMesh = HeightmapBuilder.modelFromMap(Yeti.get().gl.getGL2(),
				ResourceLoader.texture("heightmap01"),
				ResourceLoader.textureData("heightmap01"),
				4.0f, 4.0f,
				-15.0f, 120.0f);
		
		/*
		modelInstances.add(new ModelInstance(
				groundMesh,
				new MultiTextureMaterial(ResourceLoader.texture("stone"),
						ResourceLoader.texture("grass"), -10, 25)
				//new ToonMaterial(ResourceLoader.texture("grass"))
				));
		//*/
		/*
		modelInstances.add(new ModelInstance(ResourceLoader.model("sphere"),
				new CubicEnvMappingMaterial(ResourceLoader.cubeTexture("skybox01"), ResourceLoader.texture("grass")),
				new Transform().updateTranslate(0.0f, 50.0f, -30.0f).updateScale(4.0f)));
		//*/
		
		Material bumpMat = new BasicMaterial();
		bumpMat.setTexture(ResourceLoader.texture("stone"));
		bumpMat.addComponent(new TextureComponent());
		bumpMat.addComponent(new BumpComponent(ResourceLoader.texture("stone.bump")));
		
		ModelInstance daddy;
		tct = new Transform().updateTranslate(15.0f, 50.0f, -40.0f).updateScale(1.0f);
		modelInstances.add(daddy = new ModelInstance(ResourceLoader.model("texcube"), 
				bumpMat, tct));
		
		daddy.addChild(new ModelInstance(ResourceLoader.model("sphere"),
				bumpMat, new Transform().updateTranslate(10.0f, 0.5f, 0.0f)));
		
		ModelInstance m1;
		daddy.addChild(m1 = new ModelInstance(ResourceLoader.model("sphere"),
				bumpMat, new Transform().updateTranslate(-10.0f, 0.5f, 0.0f)));
		
		m1.addChild(m2 = new ModelInstance(ResourceLoader.model("sphere"),
				bumpMat, new Transform().updateTranslate(-2.0f, 0.5f, 0.0f).updateScale(0.33f)));
		
		
		camera.setPosition(new Vector3(0.0f, 50.00f, 0.0f));
		camera.setDirection(new Vector3(0.0f, 0.0f, -1.0f));
		((PerspectiveCamera)camera).setFOV(45.0f);
		
		lights.add(pl = new PointLight(new Vector3(0f, 15f, 10f), new Color(0.75f, 0.80f, 0.75f, 1.0f)));
		
		globalAmbientLight.setColor(new Color(0.05f, 0.05f, 0.05f));
		
		fog = new Fog(new Color(0.0f, 0.0f, 0.0f, 0.0f));
		fog.fadeCamera(camera);
		fogEnabled = true;
		Yeti.get().gl.glClearColor(0.1f, 0.33f, 0.2f, 1.0f);
		
		gui = new DebugGUI(drawable.getAnimator(), getCamera());
		
		Yeti.debug("\n\tRendering controls: \n\tF - toggle Fog\n\tM - toggle sMoothing");
		Yeti.get().addKeyListener(new KeyListener() {
			
			public void keyPressed(KeyEvent e) { }
			public void keyTyped(KeyEvent e) { }
			
			public void keyReleased(KeyEvent e) {
				switch(e.getKeyCode()) {
				case KeyEvent.VK_F:
					fogEnabled = !fogEnabled;
					break;
					
				case KeyEvent.VK_M:
					smoothRendering = !smoothRendering;
					break;
				}
			}
		});
	}
	
	@Override
	public void display(GLAutoDrawable drawable) {
		a += 0.8 * getDelta() * 10;
		
		pl.getPosition().x = 10 * (float)(30 * Math.sin(a / 10));
		tct.updateRotation(0.0f, 1.0f, 0.0f, a * 15);
		
		m2.getTransform().updateRotation(0.0f, 1.0f, 0.0f, a * 10);
		float orbit = 2.0f;
		m2.getTransform().updateTranslate((float)Math.cos(a / 3) * orbit, 0.0f, (float)Math.sin(a / 3) * orbit);
		
		// Calls the renderer
		super.display(drawable);		
	}
}
