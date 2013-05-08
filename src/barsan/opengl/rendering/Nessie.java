package barsan.opengl.rendering;

import java.nio.IntBuffer;
import java.util.ArrayList;

import javax.media.opengl.GL;
import javax.media.opengl.GL2;
import javax.media.opengl.GL3;

import barsan.opengl.Yeti;
import barsan.opengl.math.MathUtil;
import barsan.opengl.math.Matrix4Stack;
import barsan.opengl.math.Vector3;
import barsan.opengl.rendering.cameras.Camera;
import barsan.opengl.rendering.cameras.PerspectiveCamera;
import barsan.opengl.rendering.lights.DirectionalLight;
import barsan.opengl.rendering.lights.Light;
import barsan.opengl.rendering.lights.PointLight;
import barsan.opengl.rendering.lights.SpotLight;
import barsan.opengl.rendering.techniques.DRGeometryPass;
import barsan.opengl.rendering.techniques.DRLightPass;
import barsan.opengl.rendering.techniques.FlatTechnique;
import barsan.opengl.rendering.techniques.NullTechnique;
import barsan.opengl.resources.ModelLoader;
import barsan.opengl.resources.ResourceLoader;
import barsan.opengl.util.Color;
import barsan.opengl.util.GLHelp;
import barsan.opengl.util.Settings;

/**
 * Nessie is our Deferred Renderer. The development process will involve several
 * stages before it gets on par with the forward renderer, in terms of features.
 * In terms of speed it will already be blazingly fast!
 * 
 * @author Andrei B�rsan
 */
public class Nessie extends Renderer {

	class GBuffer {
		private static final int POSITION_TEXTURE 	= 0;
		private static final int DIFFUSE_TEXTURE 	= 1;
		private static final int NORMAL_TEXTURE 	= 2;
		private static final int TEXCOORD_TEXTURE 	= 3;
		private static final int FINAL_TEXTURE		= 4;
		
		private static final int COMPONENT_COUNT 	= 5; 
		
		private int fboHandle = -1;	
		private int dtHandle = -1;
		private int handles[] = new int[COMPONENT_COUNT - 1];
		private int finalTexture = -1;
				
		private int width, height;
		
		public GBuffer(GL3 gl, int width, int height) {
			this.width = width;
			this.height = height;
			
			IntBuffer buff = IntBuffer.allocate(4);
			gl.glGenFramebuffers(1, buff);
			fboHandle = buff.get();
			if(fboHandle < 0) {
				fail("FBO creation failure.");
			}
			buff.clear();
			
			// Note: use GL2.GL_FRAMEBUFFER instead of GL2.GL_DRAW_FRAMEBUFFER
			gl.glBindFramebuffer(GL2.GL_FRAMEBUFFER, fboHandle);
			gl.glGenTextures(handles.length, buff);
			
			int k = 0;
			while(buff.hasRemaining()) {
				int h = buff.get();
				if(h < 0) {
					fail("Color texture creation error.");
				}
				handles[k++] = h;
				// Bind the texture so we can work on it
				gl.glBindTexture(GL2.GL_TEXTURE_2D, h);
				// Actually allocate the texture data
				gl.glTexImage2D(GL2.GL_TEXTURE_2D, 0, GL2.GL_RGBA32F, width, height, 0, GL2.GL_RGBA, GL2.GL_FLOAT, null);
				gl.glTexParameterf(GL2.GL_TEXTURE_2D, GL2.GL_TEXTURE_MIN_FILTER, GL2.GL_NEAREST);
		        gl.glTexParameterf(GL2.GL_TEXTURE_2D, GL2.GL_TEXTURE_MAG_FILTER, GL2.GL_NEAREST);
				// Bind the texture to the FBO
				gl.glFramebufferTexture2D(GL2.GL_FRAMEBUFFER, GL2.GL_COLOR_ATTACHMENT0 + k - 1, GL2.GL_TEXTURE_2D, h, 0);
			}
			buff.clear();
			
			gl.glGenTextures(1, buff);
			dtHandle = buff.get();
			if(dtHandle < 0) {
				fail("Could not create depth & stencil texture!");
			}
			
			gl.glBindTexture(GL2.GL_TEXTURE_2D, dtHandle);
			gl.glTexImage2D(GL2.GL_TEXTURE_2D, 0, GL2.GL_DEPTH32F_STENCIL8, width, height, 0, GL2.GL_DEPTH_COMPONENT, GL2.GL_FLOAT, null);
			gl.glFramebufferTexture2D(GL2.GL_FRAMEBUFFER, GL2.GL_DEPTH_STENCIL_ATTACHMENT, GL2.GL_TEXTURE_2D, dtHandle, 0);

			buff.clear();
			gl.glGenTextures(1, buff);
			finalTexture = buff.get();
			if(finalTexture < 0) {
				fail("Could not create final texture!");
			}
			gl.glBindTexture(GL2.GL_TEXTURE_2D, finalTexture);
			gl.glTexImage2D(GL2.GL_TEXTURE_2D, 0, GL2.GL_RGBA, width, height, 0, GL2.GL_RGB, GL2.GL_FLOAT, null);
			gl.glFramebufferTexture2D(GL2.GL_DRAW_FRAMEBUFFER, GL2.GL_COLOR_ATTACHMENT0 + FINAL_TEXTURE, GL2.GL_TEXTURE_2D, finalTexture, 0);
			
			IntBuffer colorBuffers = IntBuffer.wrap(new int[] { 
					GL2.GL_COLOR_ATTACHMENT0,
					GL2.GL_COLOR_ATTACHMENT1,
					GL2.GL_COLOR_ATTACHMENT2,
					GL2.GL_COLOR_ATTACHMENT3
			});
			// Actually enables Multiple Render Targets, which we need for deferred rendering
			gl.glDrawBuffers(colorBuffers.remaining(), colorBuffers);
			GLHelp.fboErr(gl);
			gl.glBindFramebuffer(GL2.GL_FRAMEBUFFER, 0);
		}
		
		public void startFrame() {
			gl.glBindFramebuffer(GL2.GL_DRAW_FRAMEBUFFER, fboHandle);
			gl.glDrawBuffer(GL2.GL_COLOR_ATTACHMENT4);
			gl.glClear(GL2.GL_COLOR_BUFFER_BIT);
		}
		
		public void bindForGeometryPass() {
			gl.glBindFramebuffer(GL2.GL_DRAW_FRAMEBUFFER, fboHandle);
			gl.glDrawBuffers(3, new int[] {
					GL2.GL_COLOR_ATTACHMENT0 + POSITION_TEXTURE,
					GL2.GL_COLOR_ATTACHMENT0 + DIFFUSE_TEXTURE,
					GL2.GL_COLOR_ATTACHMENT0 + NORMAL_TEXTURE,
					//GL2.GL_COLOR_ATTACHMENT0 + TEXCOORD_TEXTURE
			}, 0);
		}
		
		public void bindForStencilPass() {
			// No actual rendering during the stencil pass
			gl.glDrawBuffer(GL2.GL_NONE);
		}
		
		public void bindForLightPass() {
			
			// Need to bind the whole buffer, since it keeps getting un-bound
			// by the shadow map FBO
			gl.glBindFramebuffer(GL2.GL_DRAW_FRAMEBUFFER, fboHandle);
			
			if(mode == Mode.DrawGBuffer) {
				// Bind the FBO so we can blit from it
				gl.glBindFramebuffer(GL2.GL_READ_FRAMEBUFFER, fboHandle);
				gl.glDrawBuffer(GL2.GL_COLOR_ATTACHMENT0 + FINAL_TEXTURE);
			}
			else {
				gl.glDrawBuffer(GL2.GL_COLOR_ATTACHMENT0 + FINAL_TEXTURE);
				for(int i = 0; i < handles.length; ++i) {
					gl.glActiveTexture(GL2.GL_TEXTURE0 + i);	
					gl.glBindTexture(GL2.GL_TEXTURE_2D, handles[POSITION_TEXTURE + i]);
				}
			}
		}
		
		public void bindForFinalPass() {
			gl.glBindFramebuffer(GL2.GL_DRAW_FRAMEBUFFER, 0);
			gl.glBindFramebuffer(GL2.GL_READ_FRAMEBUFFER, fboHandle);
			gl.glReadBuffer(GL3.GL_COLOR_ATTACHMENT0 + FINAL_TEXTURE);
		}
		
		public void setReadBuffer(GL3 gl, int textureIndex) {
			gl.glReadBuffer(GL2.GL_COLOR_ATTACHMENT0 + textureIndex);
		}
		
		public void blitComponent(GL3 gl, int component, int x1, int y1, int x2, int y2) {
			setReadBuffer(gl, component);
		    gl.glBlitFramebuffer(0, 0, width, height,					// src
		                    x1, y1, x2, y2,								// dst
		                    GL2.GL_COLOR_BUFFER_BIT, GL2.GL_LINEAR);	// params
		}
		
		public void dispose(GL3 gl) {
			gl.glDeleteTextures(4, handles, 0);
			gl.glDeleteTextures(1, new int[] { dtHandle }, 0);
			gl.glDeleteFramebuffers(1, IntBuffer.wrap(new int[] { fboHandle }));
		}
		
		private void fail(String msg) {
			Yeti.screwed("Error creating GBuffer for the deferred renderer.\n" + msg);
		}
	}
	
	public enum Mode {
		DrawGBuffer,
		DrawLightVolumes,
		DrawComposedScene
	}
	
	// Shadow mapping
	int shadowMapW = 2048;
	int shadowMapH = 2048;
	int cubeMapSide = 1024;
	int fboShadowFlat = -1;
	int fboShaodwCube = -1;

	public Mode mode;
	private GBuffer gbuffer;
	private static final String pre = "[NESSIE] ";
	private NullTechnique nullTechnique;
	private FlatTechnique flatTechnique;
   	private DRLightPass lightPassTechnique;
	private DRGeometryPass geomPassTechnique;
	
	private Matrix4Stack nullStack = new Matrix4Stack();
	ModelInstance plVolume;
	ModelInstance slVolume;
	ModelInstance dlVolume;

	public Nessie(GL3 gl) {
		this(gl, Mode.DrawComposedScene);		
	}
	
	public Nessie(GL3 gl, Mode mode) {
		super(gl);
		this.mode = mode;
	}

	/**
	 * Called after the GL context is set up. 
	 */
	public void init() {
		nullTechnique = new NullTechnique();
	   	lightPassTechnique = new DRLightPass();
		geomPassTechnique = new DRGeometryPass(GBuffer.COMPONENT_COUNT);
		flatTechnique = new FlatTechnique();
		
		shadowQuality = ShadowQuality.High;
		
		Settings s = Yeti.get().settings;
		gbuffer = new GBuffer(gl, s.width, s.height);
		Yeti.debug(pre + "Created GBuffer.");
		
		gl.glStencilOpSeparate(GL.GL_BACK, GL.GL_KEEP, GL.GL_INCR, GL.GL_KEEP);
		gl.glStencilOpSeparate(GL.GL_FRONT, GL.GL_KEEP, GL.GL_DECR, GL.GL_KEEP);
		
		plVolume = new StaticModelInstance(ResourceLoader.model("DR_sphere"));
		slVolume = new StaticModelInstance(ResourceLoader.model("DR_cone"));
		dlVolume = new StaticModelInstance(ModelLoader.buildQuadXY(2.0f, 2.0f)); 
				
		slVolume.getMaterial().setDiffuse(new Color(0.6f, 0.2f, 0.2f, 1.0f));
		plVolume.getMaterial().setDiffuse(new Color(0.6f, 0.2f, 0.2f, 1.0f));
		
		int[] intBuffer = new int[1];
		gl.glGenFramebuffers(1, intBuffer, 0);
		fboShadowFlat = intBuffer[0];
		
		gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fboShadowFlat);
		gl.glGenTextures(1, intBuffer, 0);
		state.shadowTexture = intBuffer[0];
		
		gl.glBindTexture(GL2.GL_TEXTURE_2D, state.shadowTexture);
		gl.glTexImage2D(GL2.GL_TEXTURE_2D, 
						0,
						GL2.GL_DEPTH_COMPONENT16, 
						shadowMapW, shadowMapH,
						0,
						GL2.GL_DEPTH_COMPONENT,
						GL2.GL_UNSIGNED_BYTE, 
						null);
		
		gl.glTexParameteri(GL2.GL_TEXTURE_2D, GL2.GL_TEXTURE_MAG_FILTER, GL2.GL_NEAREST);
		gl.glTexParameteri(GL2.GL_TEXTURE_2D, GL2.GL_TEXTURE_MIN_FILTER, GL2.GL_NEAREST);
		gl.glTexParameteri(GL2.GL_TEXTURE_2D, GL2.GL_TEXTURE_WRAP_S, GL2.GL_CLAMP_TO_EDGE);
		gl.glTexParameteri(GL2.GL_TEXTURE_2D, GL2.GL_TEXTURE_WRAP_T, GL2.GL_CLAMP_TO_EDGE);
		// Do we need border color?
		//gl.glTexParameterfv(GL2.GL_TEXTURE_2D, GL2.GL_TEXTURE_BORDER_COLOR, new float[] {0.0f, 0.0f, 0.0f, 0.0f }, 0);
		
		
		gl.glFramebufferTexture2D(GL2.GL_FRAMEBUFFER, GL2.GL_DEPTH_ATTACHMENT,
				GL2.GL_TEXTURE_2D, state.shadowTexture, 0);	
		gl.glDrawBuffer(GL2.GL_NONE);
		
		gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, 0);
		GLHelp.fboErr(gl);
	}
	
	@Override
	public void render(Scene scene) {
		state.setAnisotropySamples(Yeti.get().settings.anisotropySamples);
		scene.getCamera().setFrustumFar(10e6f);
		state.setCamera(scene.getCamera());
		
		gbuffer.startFrame();
		geometryPass(scene);
		lightingPass(scene);
		finalizePass(scene);
		
		postProcessPass();
	}

	@Override
	public void dispose() {
		gbuffer.dispose(gl);		
	}
	
	private void geometryPass(Scene scene) {
		geomPassTechnique.setup(state);
		gbuffer.bindForGeometryPass();
		
		// Only the geometry pass updates the depth buffer
	    gl.glDepthMask(true);
	    gl.glClear(GL2.GL_COLOR_BUFFER_BIT | GL2.GL_DEPTH_BUFFER_BIT);
	    gl.glEnable(GL2.GL_DEPTH_TEST);
	    
		geomPassTechnique.renderModelInstances(state, scene.modelInstances);
	}	
	
	private void lightingPass(Scene scene) {		
		// Disable depth writing for this step
	    gl.glDepthMask(false);
	    gl.glDisable(GL2.GL_DEPTH_TEST);
		gbuffer.bindForLightPass();
		
		switch(mode) {
	    
	    case DrawGBuffer:
	    	
			int w = Yeti.get().settings.width;
			int h = Yeti.get().settings.height;
			int halfW = w / 2;
		    int halfH = h / 2;

		    // Just render the components of the GBuffer for testing
		    // Bottom left: POSITION
	    	gbuffer.blitComponent(gl, GBuffer.POSITION_TEXTURE, 0, 0, halfW, halfH);
		    // Top left: DIFFUSE
	    	gbuffer.blitComponent(gl, GBuffer.DIFFUSE_TEXTURE, 0, halfH, halfW, h);
		    // Top right: NORMAL
		    gbuffer.blitComponent(gl, GBuffer.NORMAL_TEXTURE, halfW, halfH, w, h);	
		    // Bottom right: TEXCOORD
		    gbuffer.blitComponent(gl, GBuffer.TEXCOORD_TEXTURE, halfW, 0, w, halfH);
		break;
		
	    case DrawComposedScene:	    	
	       	gl.glEnable(GL2.GL_STENCIL_TEST);
			for(Light l : scene.lights) {
				renderLightVolume(l, true);
			}
			gl.glDisable(GL2.GL_STENCIL_TEST);
	    	break;
		
	    case DrawLightVolumes:
	    	gl.glEnable(GL2.GL_STENCIL_TEST);
			for(Light l : scene.lights) {
				renderLightVolume(l, true);
			}
			gl.glDisable(GL2.GL_STENCIL_TEST);
			
	       	gl.glDisable(GL2.GL_DEPTH_TEST);
	    	gl.glEnable(GL2.GL_BLEND);
	    	gl.glDisable(GL2.GL_CULL_FACE);
	      	gl.glBlendEquation(GL2.GL_FUNC_ADD);
	      	gl.glBlendFunc(GL2.GL_ONE, GL2.GL_ONE);
	      	gbuffer.blitComponent(gl, GBuffer.POSITION_TEXTURE, 0, 0, 300, 300);
	      	flatTechnique.setup(state);
	       	for(Light l : scene.lights) {
				renderLightVolume(l, false);
			}
	    	
	    	break;
	    }
		
		GLHelp.dumpDepthBuffer(10, 10, 200, 200, 15.0f, state.shadowTexture);
		
		// Important to reset this, to allow font rendering and other stuff
		// that expect the default texture unit to be active to work
		gl.glActiveTexture(GL2.GL_TEXTURE0);
	}
	
	private void renderLightVolume(Light light, boolean computeLight) {
		// Perform the stencil step
		if(computeLight) {
			gl.glEnable(GL2.GL_DEPTH_TEST);
			
			if(light.castsShadows()) {
				gl.glDepthMask(true);	
				computeShadowMap(light);
			}

			// Re-bind the gbuffer
			this.gbuffer.bindForLightPass();
			
			nullTechnique.setup(state);
			gl.glDepthMask(false);
			gbuffer.bindForStencilPass();
			gl.glDisable(GL2.GL_CULL_FACE);
			gl.glClear(GL2.GL_STENCIL_BUFFER_BIT);
			
			// Stencil operations are simply set once, in the init() method
			gl.glStencilFunc(GL2.GL_ALWAYS, 0, 0);
		}
		
		switch(light.getType()) {
			case Directional:
				renderDLVol((DirectionalLight)light, computeLight);
				break;
				
			case Point:
				renderPLVol((PointLight)light, computeLight);
				break;
				
			case Spot:
				renderSLVol((SpotLight)light, computeLight);
				break;
		}
		
		if(computeLight) {
	       	gl.glCullFace(GL2.GL_BACK);
	       	gl.glDisable(GL2.GL_BLEND);
		}
	}

	private void prepareLightPass(RendererState state) {
		// Render the actual light volume
		gbuffer.bindForLightPass();
       	lightPassTechnique.setup(state);
       	gl.glStencilFunc(GL2.GL_NOTEQUAL, 0, 0xFF);
       	gl.glDisable(GL2.GL_DEPTH_TEST);	// finally done with the depth test!
       	
    	gl.glEnable(GL2.GL_BLEND);
      	gl.glBlendEquation(GL2.GL_FUNC_ADD);
      	gl.glBlendFunc(GL2.GL_ONE, GL2.GL_ONE);
      	
      	gl.glEnable(GL2.GL_CULL_FACE);
      	gl.glCullFace(GL2.GL_FRONT);
	}
	
	private void renderDLVol(DirectionalLight l, boolean computeLight) {
		// no stencil pass needed (yet - perhaps we can optimizie this and not
		// compute dir lights on stuff like the skybox)
		if(computeLight) {
			prepareLightPass(state);
			gl.glDisable(GL2.GL_STENCIL_TEST);
			gl.glDisable(GL2.GL_CULL_FACE);
			lightPassTechnique.drawDirectionalLight(dlVolume, l, state);
		}
	}
	
	private void renderPLVol(PointLight l, boolean computeLight) {
		// Compute transform for the null pass
		// TODO: cleaner code
		plVolume.getTransform()
			.setTranslate(l.getPosition())
			.setScale(l.getBoundingRadius())
			.refresh();
		
		if(computeLight) {
			nullTechnique.renderDude(plVolume, state, nullStack);
			prepareLightPass(state);
	       	lightPassTechnique.drawPointLight(plVolume, l, state);
		}
		else {
			flatTechnique.renderDude(plVolume, state, nullStack);
		}
	}
	
	private void renderSLVol(SpotLight l, boolean computeLight) {
		float h = l.getBoundingRadius();
		
		/*  Math time! */
		double outAngle = Math.acos(l.getCosOuter());
		float w = (float)( h * (Math.tan( outAngle )));
		
		Vector3 lightDir = l.getDirection();
		Vector3 pos = l.getPosition();
		Vector3 axis;
		float angle = 0.0f;
		if(lightDir.equals(Vector3.Y)) {
			axis = Vector3.Z.copy().inv();
			angle = 180.0f;
		} else if(lightDir.equals(Vector3.Y.copy().inv())) {
			axis = Vector3.Z.copy().inv();
			angle = 180.0f;
		}
		else {
			axis = new Vector3(Vector3.Y).cross(lightDir);
		}
		
		angle += MathUtil.RAD_TO_DEG * (float)Math.acos(Vector3.Y.dot(lightDir));
		// System.out.println(angle);
		//angle += 90.0f;
		
		// Compute cone scale and rotation based on the light
		slVolume.getTransform()
			.setTranslate(pos)
			.setScale(w, h, w)
			.setRotation(axis, angle)
			.refresh();
		
		if(computeLight) {
			nullTechnique.renderDude(slVolume, state, nullStack);
			prepareLightPass(state);
			lightPassTechnique.drawSpotLight(slVolume, l, state);
		} else {
			flatTechnique.renderDude(slVolume, state, nullStack);
		}
	}
	
	public void finalizePass(Scene scene) {
		gbuffer.bindForFinalPass();
		gl.glBlitFramebuffer(	0, 0, gbuffer.width, gbuffer.height,
								0, 0, gbuffer.width, gbuffer.height,
								GL2.GL_COLOR_BUFFER_BIT, GL2.GL_LINEAR);
	}
	
	private void computeShadowMap(Light light) {
		Camera aux = state.getCamera();
		int oldDim[] = new int[4];
		gl.glGetIntegerv(GL2.GL_VIEWPORT, oldDim, 0);
		
		switch(light.getType()) {
			case Spot:
				prepareSpotSM((SpotLight)light);
				break;
				
			default:
				throw new UnsupportedOperationException("Only Spot lights can cast shadows at the moment in Nessie");
		}
		
		//*
		nullTechnique.setup(state);
		Matrix4Stack ms = new Matrix4Stack();
		for(ModelInstance mi : state.getScene().modelInstances) {
			nullTechnique.renderDude(mi, state, ms);
		}//*/
		gl.glViewport(0, 0, oldDim[2], oldDim[3]);
		state.setCamera(aux);
	}
	
	private void prepareSpotSM(SpotLight spotLight) {
		gl.glBindFramebuffer(GL2.GL_FRAMEBUFFER, fboShadowFlat);
		gl.glClear(GL2.GL_DEPTH_BUFFER_BIT);
		
		Vector3 camDir = spotLight.getDirection().copy();
		
		PerspectiveCamera pc = new PerspectiveCamera(
				spotLight.getPosition().copy(),
				camDir,
				shadowMapW, 
				shadowMapH);
		
		float th = spotLight.getCosOuter();
		float angle = (float) (2.0 * Math.acos(th) * MathUtil.RAD_TO_DEG);
		pc.setFOV(angle);
		pc.setFrustumNear(1f);
		pc.setFrustumFar(240.0f);
		
		gl.glViewport(0, 0, shadowMapW, shadowMapH);
		
		state.setCamera(pc);
		state.depthProjection = pc.getProjection().cpy();
		state.depthView = pc.getView().cpy();
	}
	
	class Effect {
		private void apply(int srcHandle, int dstHandle) {
			// Perform necessary computations from src to dst
			// NOTE: should have n inputs and m outputs
		}
	}
		
	// Just blocking out what it's supposed to look like
	ArrayList<Effect> fx = new ArrayList<>();
	private void postProcessPass() {
		int srcHandle = 0,
			dstHandle = 0, 
			aux;
		
		for(Effect effect : fx) {
			effect.apply(srcHandle, dstHandle);
			aux = srcHandle;
			srcHandle = dstHandle;
			dstHandle = aux;
		}
		
		if(fx.size() % 2 == 0) {
			// render src to screen
		} else {
			// render dst to screen
		}
	}
}
