#version 330

uniform mat4 mvpMatrix;
uniform mat4 mvMatrix;
uniform mat3 normalMatrix;
uniform vec3 vLightPosition;

uniform bool useTexture;

uniform bool	fogEnabled;
uniform float 	minFogDistance;
uniform float 	maxFogDistance;

in vec4 vVertex;
in vec3 vNormal;
in vec2 vTexCoord;

flat out vec3 	vVaryingNormal;
flat out vec3 	vVaryingLightDir;
	 out vec2 	vVaryingTexCoords;
flat out float 	fogFactor;

void main() {
	// Surface normal in eye coords
	vVaryingNormal = normalMatrix * vNormal;

	vec4 vPosition4 = mvMatrix * vVertex;
	vec3 vPosition3 = vPosition4.xyz / vPosition4.w;
	
	// Diffuse light
	// Vector to light source
	vVaryingLightDir = normalize(vLightPosition - vPosition3);

	if(useTexture) {
		vVaryingTexCoords = vTexCoord;
	}
	
	gl_Position = mvpMatrix * vVertex;
	
	if(fogEnabled) {
		float len = length(gl_Position);
		fogFactor = (len - minFogDistance) / (maxFogDistance - minFogDistance);
		fogFactor = clamp(fogFactor, 0, 1);
	}	
}