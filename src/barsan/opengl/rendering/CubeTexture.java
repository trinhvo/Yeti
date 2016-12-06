package barsan.opengl.rendering;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL3;

import com.jogamp.opengl.GL3;
import com.jogamp.opengl.util.texture.Texture;

/**
 * TODO: refactor this horror!
 * 
 * @author Andrei B�rsan
 *
 */
public class CubeTexture {
	
	private Texture texture;
	
	public String[] names = new String[] {
			"xpos", "xneg", "ypos", "yneg", "zpos", "zneg"
	};
	
	public static int[] cubeSlots = new int[] {
		GL3.GL_TEXTURE_CUBE_MAP_POSITIVE_X,
		GL3.GL_TEXTURE_CUBE_MAP_NEGATIVE_X,
		GL3.GL_TEXTURE_CUBE_MAP_POSITIVE_Y,
		GL3.GL_TEXTURE_CUBE_MAP_NEGATIVE_Y,
		GL3.GL_TEXTURE_CUBE_MAP_POSITIVE_Z,
		GL3.GL_TEXTURE_CUBE_MAP_NEGATIVE_Z
	};
	
	public CubeTexture() {
		texture = new Texture(GL3.GL_TEXTURE_CUBE_MAP);
	}
	
	public Texture getTexture() {
		return texture;
	}
	
	public void bind(GL gl) {
		texture.bind(gl);
	}
	
	public void dispose(GL gl) {
		texture.destroy(gl);
	}
}
