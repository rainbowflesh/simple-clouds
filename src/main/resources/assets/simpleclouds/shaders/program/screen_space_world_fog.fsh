#version 150

uniform sampler2D DiffuseSampler;
uniform sampler2D DiffuseDepthSampler;
uniform sampler2D StormFogSampler;
uniform sampler2D CloudDepthSampler;
uniform mat4 InverseWorldProjMat;
uniform mat4 InverseModelViewMat;
uniform float FogStart;
uniform float FogEnd;
uniform vec3 FogColor;
uniform int FogShape;

in vec2 texCoord;
in vec2 oneTexel;
out vec4 fragColor;

float fogDistance(vec3 pos, int shape)
{
	if (shape == 0)
		return length(pos);

	float distXZ = length(pos.xz);
	float distY = abs(pos.y);
	return max(distXZ, distY);
}

vec3 screenToWorldPos(vec2 coord, float depth)
{
	vec3 ndc = vec3(coord * 2.0 - 1.0, depth);
	vec4 view = InverseWorldProjMat * vec4(ndc, 1.0);
	view.xyz /= view.w;
	return (InverseModelViewMat * view).xyz;
}

void main()
{
	float screenDepth = texture(DiffuseDepthSampler, texCoord).x;
	vec4 col = texture(DiffuseSampler, texCoord);

	// Skip pure sky pixels (screenDepth == 1.0 = far plane). The sky has no
	// scene geometry, so distance-fog blending would apply at full intensity
	// and uniformly darken the entire sky, creating a flat overlay.
	// Also skip pixels where cloud geometry is clearly in front of the scene.
	// The small bias handles the pre-filled cloud depth case where non-cloud
	// pixels have cloudDepth == sceneDepth.
	float cloudDepth = texture(CloudDepthSampler, texCoord).x;
	if (cloudDepth < screenDepth - 0.0001 || screenDepth >= 1.0)
	{
		fragColor = vec4(col.rgb, 1.0);
		return;
	}

	// Use the blurred storm fog alpha directly — no raw-mask gating, which
	// would create hard-edged artifacts where the un-blurred fog cuts off.
	vec4 stormFogCol = texture(StormFogSampler, texCoord);
	if (stormFogCol.a <= 0.001)
	{
		fragColor = vec4(col.rgb, 1.0);
		return;
	}

	vec3 pos = screenToWorldPos(texCoord, screenDepth * 2.0 - 1.0);
	float fogDist = fogDistance(pos, FogShape);
	float fogValue = smoothstep(FogStart, FogEnd, fogDist);

	// Blend world fog color toward the storm fog color based on how much
	// storm fog is present at this screen angle, then apply at fog distance.
	vec3 fogCol = mix(FogColor, stormFogCol.rgb, stormFogCol.a);
	fragColor = vec4(mix(col.rgb, fogCol, fogValue), 1.0);
}
