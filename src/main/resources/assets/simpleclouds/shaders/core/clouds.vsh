#version 430

in vec3 Position;

#moj_import <simpleclouds:opaque.glsl>

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform mat4 ViewMat;
uniform mat4 CloudWorldMat;
uniform vec3 CameraPos;
uniform float EarthRadius;
uniform vec3 Light0_Direction;
uniform vec3 Light1_Direction;
uniform float LightPower;
uniform float AmbientLight;
uniform vec3 DarknessColorModifier;
uniform bool UseNormals;

out vec4 vertexColor;
out float fogDistance;
out float vertexDistance;

vec4 mixLight(vec3 lightDir0, vec3 lightDir1, vec3 normal, vec4 color) 
{
    lightDir0 = normalize(lightDir0);
    lightDir1 = normalize(lightDir1);
    float light0 = max(0.0, dot(lightDir0, normal));
    float light1 = max(0.0, dot(lightDir1, normal));
    float lightAccum = min(1.0, (light0 + light1) * LightPower + AmbientLight);
    return vec4(vec3(color.r * lightAccum, color.g * lightAccum, color.b), color.a);
}

void main() 
{
	SideInfo info = sides.data[gl_InstanceID];
	
	vec4 transformedPos = vec4(Position, 1.0) * transformations[uint(info.side)];
	vec3 sideOffset = vec3(info.x, info.y, info.z);
	vec4 finalPos = vec4(transformedPos.xyz * info.radius + sideOffset, 1.0);
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
	fogDistance = length(modelPos.xz);
	vertexDistance = length(modelPos.xyz);

	vec3 tint = mix(vec3(1.0), vec3(info.tintR, info.tintG, info.tintB), 0.45);
	vec4 finalCol = vec4(tint * mix(DarknessColorModifier, vec3(1.0), info.brightness), 1.0);
    if (UseNormals)
    {
	    vec3 normal = normals[uint(info.side)];
		vertexColor = mixLight(Light0_Direction, Light1_Direction, normal, finalCol);
	}
	else
	{
		vertexColor = finalCol;
	}
}
