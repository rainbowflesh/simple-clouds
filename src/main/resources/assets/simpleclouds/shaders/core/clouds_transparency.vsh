#version 430

in vec3 Position;

struct TransparentCubeInfo {
	float x;
	float y;
	float z;
	float brightness;
	float alpha;
	float radius;
	float tintR;
	float tintG;
	float tintB;
};

layout(std430) restrict readonly buffer TransparentCubeInfoBuffer {
    TransparentCubeInfo data[];
}
cubesTransparent;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform mat4 ViewMat;
uniform mat4 CloudWorldMat;
uniform vec3 CameraPos;
uniform float EarthRadius;
uniform vec3 DarknessColorModifier;

out vec4 vertexColor;
out float fogDistance;
out float vertexDistance;

void main() 
{
	TransparentCubeInfo info = cubesTransparent.data[gl_InstanceID];
	vec3 cubeOffset = vec3(info.x, info.y, info.z);
	vec4 finalPos = vec4(Position * info.radius + cubeOffset, 1.0);
	vec4 worldPos = CloudWorldMat * finalPos;
	vec3 cameraRelativePos = worldPos.xyz - CameraPos;
	if (EarthRadius < -1.0 || EarthRadius > 1.0)
	{
		float localRadius = EarthRadius + worldPos.y;
		float phi = length(cameraRelativePos.xz) / localRadius;
		cameraRelativePos.y += (cos(phi) - 1.0) * localRadius;
		if (phi != 0.0)
			cameraRelativePos.xz = cameraRelativePos.xz * sin(phi) / phi;
	}
	vec4 modelPos = ViewMat * vec4(cameraRelativePos, 1.0);
    gl_Position = ProjMat * modelPos;
	vec3 tint = vec3(info.tintR, info.tintG, info.tintB);
	vertexColor = vec4(tint * mix(DarknessColorModifier, vec3(1.0), info.brightness), info.alpha);
	fogDistance = length(modelPos.xz);
	vertexDistance = length(modelPos.xyz);
}