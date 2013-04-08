#version 400 core

uniform mat4 mvpMatrix;
//uniform mat4 mvMatrix;
uniform mat4 mMatrix;
//uniform mat4 vMatrix;

//uniform mat3 vMatrix3x3;

//uniform mat3 normalMatrix;
uniform bool useTexture;

layout(location = 0) in vec4 vVertex;
layout(location = 1) in vec3 vNormal;
layout(location = 2) in vec2 vTexCoord;

out vec3 WorldPos;
out vec2 TexCoord;
out vec3 Normal; 

void main() {
	Normal = (mMatrix * vec4(vNormal, 0.0f)).xyz;
	WorldPos = (mMatrix * vVertex).xyz; 

	//vec4 vPosition4 = mvMatrix * vVertex;
	//vec3 vPosition3 = vPosition4.xyz / vPosition4.w;
		
	if(useTexture) {
		TexCoord = vTexCoord;
	} else {
		TexCoord = vec2(0.0f, 0.0f);
	}
	
	// Projected vertex
	gl_Position = mvpMatrix * vVertex;	
}