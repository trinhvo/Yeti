package barsan.opengl.rendering;

import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;

import javax.media.opengl.GL;
import javax.media.opengl.GL2;

import barsan.opengl.Yeti;
import barsan.opengl.math.Vector3;

import com.jogamp.common.nio.Buffers;

/**
 * Java wrapper for a GL data array stored in the graphics device memory.
 * @author Andrei Barsan
 */
public class VBO {

	private final int nativeHandle;
	public final int type;
	
	private FloatBuffer localBuffer;
	private int elementGroupCount;
	private int elementSizeOf;
	private int elementType;
	private int elementGroupSize;
	
	/**
	 * Default allocation of a GL_ARRAY_BUFFER of FLOATS in groups of 3.
	 * @param size
	 */
	public VBO(int size) {
		this(GL.GL_ARRAY_BUFFER, size, 3, Buffers.SIZEOF_FLOAT, GL.GL_FLOAT);
	}
	
	/**
	 * Default use case - just assumes we're storing floats in groups of 3 (Vector3s).
	 * @param gl	The GL contex used.
	 * @param type	The type of buffer to create (e.g. GL_ARRAY_BUFFER).
	 * @param size	How many element groups should the buffer be able to hold (assumes each group has three float elements).
	 */
	public VBO(int type, int size) {
		this(type, size, 3, Buffers.SIZEOF_FLOAT, GL.GL_FLOAT);
	}
	
	/**
	 * @param gl	The GL contex used.
	 * @param type	The type of buffer to create (e.g. GL_ARRAY_BUFFER).
	 * @param size	How many element groups should the buffer be able to hold (assumes each group has three float elements).
	 * @param elementGroupSize How many elements in a group (e.g. 3 for 3D coordinates such as vertices, 2 for 2D texture coords).
	 */
	public VBO(int type, int size, int elementGroupSize) {
		this(type, size, elementGroupSize, Buffers.SIZEOF_FLOAT, GL.GL_FLOAT);
	}
	
	public VBO(int type, int elementGroupCount, int elementGroupSize, int elementSizeOf, int elementType) {
		GL2 gl = Yeti.get().gl;
		
		int buff[] = new int[] { -1 };
		// TODO: request multiple buffers at once (good optimization when we
		// need dozens or even hundreds of meshes in one scene) 
		gl.glGenBuffers(1, buff, 0);
		nativeHandle = buff[0];
		
		this.type = type;
		this.elementGroupCount = elementGroupCount;
		this.elementGroupSize = elementGroupSize;
		this.elementSizeOf = elementSizeOf;
		this.elementType = elementType;
		
		if(elementSizeOf < 1 || elementSizeOf > 4) {
			Yeti.screwed("Only 1, 2, 3 and 4-byte elements allowed!");
		}
		
		if((type & (GL2.GL_ARRAY_BUFFER | GL2.GL_ELEMENT_ARRAY_BUFFER
				| GL2.GL_NORMAL_ARRAY | GL2.GL_TEXTURE_COORD_ARRAY)) == 0 ) {
			Yeti.screwed("Bad buffer type!");
		}
		
		gl.glBindBuffer(type, nativeHandle);
		gl.glBufferData(type, elementGroupCount * elementGroupSize * elementSizeOf,
				null, GL2.GL_DYNAMIC_DRAW);
		
		localBuffer = gl.glMapBuffer(type, GL2.GL_WRITE_ONLY)
				.order(ByteOrder.nativeOrder()).asFloatBuffer();
		
		gl.glUnmapBuffer(type);
	}
	
	/**
	 * Pushes a float array into the buffer.
	 * 
	 * @param el
	 * @return this object for chaining
	 */
	public VBO put(float elements[]) {
		GL2 gl = Yeti.get().gl;
		gl.glBindBuffer(type, nativeHandle);
		gl.glMapBuffer(type, GL2.GL_WRITE_ONLY);
		localBuffer.put(elements);		
		gl.glUnmapBuffer(type);		
		return this;
	}
	
	// Warning, might involve a bit too many native calls
	public VBO put(Vector3 element) {
		GL2 gl = Yeti.get().gl;
		gl.glBindBuffer(type, nativeHandle);
		gl.glMapBuffer(type, GL2.GL_WRITE_ONLY);
		localBuffer.put(element.x);
		localBuffer.put(element.y);
		localBuffer.put(element.z);
		gl.glUnmapBuffer(type);
		return this;
	}
	
	public VBO put(Vector3 elements[]) {
		GL2 gl = Yeti.get().gl;
		gl.glBindBuffer(type, nativeHandle);
		gl.glMapBuffer(type, GL2.GL_WRITE_ONLY);
		
		if(elementGroupSize != 3) 
			Yeti.warn("Putting vector3s in a VBO that has a group size of " + elementGroupSize + " !");
		
		for(Vector3 v : elements) {
			localBuffer.put(v.x);
			localBuffer.put(v.y);
			localBuffer.put(v.z);
		}		
		gl.glUnmapBuffer(type);		
		return this;
	}
	
	public VBO put(ArrayList<Vector3> elements) {
		GL2 gl = Yeti.get().gl;
		gl.glBindBuffer(type, nativeHandle);
		gl.glMapBuffer(type, GL2.GL_WRITE_ONLY); 	// we need to map it first!
													// otherwise unmap triggers GL_INVALID_OPERATION
		if(elementGroupSize != 3) 
			Yeti.warn("Putting vector3s in a VBO that has a group size of " + elementGroupSize + " !");
			
		for(Vector3 v : elements) {
			localBuffer.put(v.x);
			localBuffer.put(v.y);
			localBuffer.put(v.z);
		}		
		gl.glUnmapBuffer(type);		
		return this;
	}
	
	/**
	 * Most common use case. Assumes the data in the buffer is composed of float
	 * vectors of three elements each.
	 * 
	 * @param attributeIndex Attribute's index in the shader
	 * @return This object for chaining.
	 */
	public VBO use(int attributeIndex) {
		return use(attributeIndex, elementGroupSize, elementType, false, 0, 0);
	}
	
	// This guy will be private until further investigation;
	// data type, count and so on should be set in the constructor and *not*
	// get randomly changed at runtime.
	private VBO use(int attributeIndex, int groupSize, int dataType, 
			boolean normalized, int stride, long offset) {
		GL2 gl = Yeti.get().gl;
		gl.glBindBuffer(type, nativeHandle);
		gl.glEnableVertexAttribArray(attributeIndex);
		gl.glVertexAttribPointer(	attributeIndex, 
									groupSize, /* THIS IS NR OF STUFF PER GROUP, NOT SIZEOF FLOAT */
									dataType, normalized, stride, offset);
		return this;
	}
	
	public int getSize() {
		return elementGroupCount;
	}

	public int getHandle() {
		return nativeHandle;
	}

	public void cleanUp(int indexUsed) {
		GL2 gl = Yeti.get().gl;
		gl.glBindBuffer(type, 0);
		gl.glDisableVertexAttribArray(indexUsed);
	}
}
