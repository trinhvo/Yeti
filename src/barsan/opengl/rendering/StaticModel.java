package barsan.opengl.rendering;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.media.opengl.GL;
import javax.media.opengl.GL2;

import barsan.opengl.Yeti;
import barsan.opengl.rendering.materials.Material;
import barsan.opengl.resources.ModelLoader.Face;
import barsan.opengl.resources.ModelLoader.Group;

/**
 * @author Andrei Barsan
 * 
 * FIXME: maybe repair the weird fors?
 */
public class StaticModel extends Model {
	
	public static final int COORDS_PER_POINT = 3;
	public static final int TEX_COORDS_PER_POINT = 2;

	// Vertex buffers for faster rendering
	private VBO vertices;
	private VBO texcoords;
	private VBO normals;
	private VBO tangents;
	private VBO binormals;
	
	protected HashMap<String, Group> groups = new HashMap<>();
	public Group master = new Group();
	
	private String name;
	protected final GL gl;	// TODO: maybe refactor this out
	
	public StaticModel(GL gl, String name) {
		this.gl = gl;
		this.name = name;
		groups.put("default", new Group());
	}
	
	static float uv[] = new float[2];
	public void buildVBOs() {
		assert master.faces.size() > 0 : "Empty model";
		
		/* If no materials were loaded/specified, just create a basic one. */
		if(null == defaultMaterialGroups) {
			defaultMaterialGroups = new ArrayList<MaterialGroup>();
		}
		if(defaultMaterialGroups.isEmpty()) {
			MaterialGroup defMG = new MaterialGroup(0, 
					master.faces.size(), 
					new Material());
			defaultMaterialGroups.add(defMG);
			
		}
		
		int size = master.faces.size() * pointsPerFace;
		
		vertices = 	new VBO(GL2.GL_ARRAY_BUFFER, size, COORDS_PER_POINT);
		normals = 	new VBO(GL2.GL_ARRAY_BUFFER, size, COORDS_PER_POINT);
		texcoords = new VBO(GL2.GL_ARRAY_BUFFER, size, TEX_COORDS_PER_POINT);
		tangents = 	new VBO(GL2.GL_ARRAY_BUFFER, size, COORDS_PER_POINT);
		binormals = new VBO(GL2.GL_ARRAY_BUFFER, size, COORDS_PER_POINT);

		for(MaterialGroup mg : defaultMaterialGroups) {
		
			vertices.open();		
			for(int i = mg.beginIndex; i < mg.beginIndex + mg.length; ++i) {
				Face f = master.faces.get(i);
				for(int p = 0; p < pointsPerFace; ++p) {
					vertices.append(f.points[p]);
				}
			}
			vertices.close();
			
			if(master.faces.get(mg.beginIndex).normals != null) {
				normals.open();
				for(int i = mg.beginIndex; i < mg.beginIndex + mg.length; ++i) {
					Face f = master.faces.get(i);
					for(int p = 0; p < pointsPerFace; ++p) {
						normals.append(f.normals[p]);
					}
				}
				normals.close();
				
				for(Face f : master.faces) {
					f.computeTangBinorm();
				}
				
				tangents.open();
				for(int i = mg.beginIndex; i < mg.beginIndex + mg.length; ++i) {
					Face f = master.faces.get(i);
					for(int p = 0; p < pointsPerFace; ++p) {
						tangents.append(f.tangents[p]);
					}
				}
				tangents.close();
				
				binormals.open();
				for(int i = mg.beginIndex; i < mg.beginIndex + mg.length; ++i) {
					Face f = master.faces.get(i);
					for(int p = 0; p < pointsPerFace; ++p) {
						binormals.append(f.binormals[p]);
					}
				}
				binormals.close();
			}
			
			if(master.faces.get(0).texCoords != null) {
				texcoords.open();
				for(int i = mg.beginIndex; i < mg.beginIndex + mg.length; ++i) {
					Face f = master.faces.get(i);
					for(int p = 0; p < pointsPerFace; ++p) {
						uv[0] = f.texCoords[p].x;
						uv[1] = f.texCoords[p].y;
						texcoords.append(uv);
					}
				}
				texcoords.close();
			}
		
		}
		
		if(Yeti.get().settings.debugModels) {
			Yeti.debug(String.format("VBOs for \"%s\" built. Normal element count: %d;" +
					" Geometry element count: %d. Also added precomputed tangents and binormals.",
				getName(), vertices.getSize(), normals.getSize()));
		}
	}
	
	public void addFace(Face face) {
		addFace("default", face);
	}
	
	public void addFace(String groupName, Face face) {
		groups.get(groupName).faces.add(face);
		master.faces.add(face);
	}
	
	@Override
	public int getArrayLength() {
		return vertices.getSize();		
	}
	
	@Override
	public VBO getTexCoords() {
		return texcoords;
	}
	
	public void dispose() {
		gl.glDeleteBuffers(3, new int[] { 	vertices.getHandle(), 
											normals.getHandle(),
											texcoords.getHandle(),
										}, 0);
	}
	
	public VBO getVertices() {
		return vertices;
	}
	
	public VBO getNormals() {
		return normals;
	}
	
	public VBO getTangents() {
		return tangents;
	}
	
	public VBO getBinormals() {
		return binormals;
	}
	
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}
	
	public VBO getTexcoords() {
		return texcoords;
	}

	public Map<String, Group> getGroups() {
		return groups;
	}
}
