package barsan.opengl.rendering;

import javax.media.opengl.GL2;

import barsan.opengl.math.Matrix4;
import barsan.opengl.math.Matrix4Stack;

import com.jogamp.opengl.util.texture.Texture;

public class SkyBox extends ModelInstance {

	private Camera camera;

	public SkyBox(GL2 gl, CubeTexture cubeTexture, Camera toFollow) {
		super(new Cube(gl, 20.0f, true), new CubeMapMaterial());

		Texture t = cubeTexture.getTexture();
		CubeMapMaterial cmm = (CubeMapMaterial) getMaterial();
		cmm.setTexture(t);
		
		cmm.setWriteDepthBuffer(false);
		cmm.setCheckDepthBuffer(true);
		

		// this.cubeTexture = cubeTexture;
		setTransform(new Matrix4());
		camera = toFollow;
	}

	@Override
	public void render(RendererState rendererState, Matrix4Stack mstack) {
		getTransform().setMatrix(camera.getView());
		
		// Invert the camera transform
		// Then clear the rotation, so it stands still around the player. Otherwise,
		// the camera rotation and the skybox rotation cancel each other out!
		getTransform().get().inv().clearRotation();
		
		super.render(rendererState, mstack);
	}
}
