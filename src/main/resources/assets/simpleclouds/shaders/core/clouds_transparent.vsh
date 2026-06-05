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
	uint visibleFaceMask;
};

layout(std430) restrict readonly buffer TransparentCubeInfoBuffer {
    TransparentCubeInfo data[];
} cubes;

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
flat out int faceVisible;

vec3 applyEarthCurvature(vec4 worldPos)
{
	vec3 cameraRelativePos = worldPos.xyz - CameraPos;
	if (EarthRadius < -1.0 || EarthRadius > 1.0)
	{
		float localRadius = EarthRadius + worldPos.y;
		float phi = length(cameraRelativePos.xz) / localRadius;
		cameraRelativePos.y += (cos(phi) - 1.0) * localRadius;
		if (phi != 0.0)
			cameraRelativePos.xz = cameraRelativePos.xz * sin(phi) / phi;
	}
	return cameraRelativePos;
}

void main()
{
	TransparentCubeInfo info = cubes.data[gl_InstanceID];
	int faceIndex = gl_VertexID / 4;
	faceVisible = int((info.visibleFaceMask >> uint(faceIndex)) & 1u);
	vec4 finalPos = vec4(Position * info.radius + vec3(info.x, info.y, info.z), 1.0);
	vec4 worldPos = CloudWorldMat * finalPos;
	vec3 cameraRelativePos = applyEarthCurvature(worldPos);
	vec4 modelPos = ViewMat * vec4(cameraRelativePos, 1.0);
	gl_Position = ProjMat * modelPos;
	fogDistance = length(modelPos.xz);
	vertexDistance = length(modelPos.xyz);
	vec3 tint = mix(vec3(1.0), vec3(info.tintR, info.tintG, info.tintB), 0.45);
	vertexColor = vec4(tint * mix(DarknessColorModifier, vec3(1.0), info.brightness), info.alpha);
}
