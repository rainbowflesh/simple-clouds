#version 150

uniform sampler2DShadow ShadowMap;
uniform sampler2D ShadowMapColor;
uniform sampler2D DepthSampler;
uniform sampler2D DiffuseSampler;
uniform mat4 InverseWorldProjMat;
uniform mat4 InverseModelViewMat;
uniform mat4 ShadowProjMat;
uniform mat4 ShadowModelViewMat;
uniform vec3 CameraPos;
uniform vec3 ShadowColorMultiplier;
uniform float ShadowSpan;
uniform float MinimumRadius;
uniform float FadeDistance;

in vec2 texCoord;
in vec2 oneTexel;
out vec4 fragColor;

vec3 screenToWorldPos(vec2 coord, float depth)
{
	vec3 ndc = vec3(coord * 2.0 - 1.0, depth);
  	vec4 view = InverseWorldProjMat * vec4(ndc, 1.0);
  	view.xyz /= view.w;
  	vec3 result = (InverseModelViewMat * view).xyz;
  	return result;
}

float shadowStrengthAt(vec3 pos)
{
	vec4 shadowMapPos = ShadowProjMat * ShadowModelViewMat * vec4(pos, 1.0);
	vec3 ndc = shadowMapPos.xyz / shadowMapPos.w;
	if (any(lessThan(ndc, vec3(-1.0))) || any(greaterThan(ndc, vec3(1.0))))
		return 0.0;
	vec3 coord = ndc * 0.5 + 0.5;
	return texture(ShadowMap, coord);
}

float shadowTransmissionAt(vec3 pos)
{
	vec4 shadowMapPos = ShadowProjMat * ShadowModelViewMat * vec4(pos, 1.0);
	vec3 ndc = shadowMapPos.xyz / shadowMapPos.w;
	if (any(lessThan(ndc, vec3(-1.0))) || any(greaterThan(ndc, vec3(1.0))))
		return 1.0;
	vec2 coord = ndc.xy * 0.5 + 0.5;
	return texture(ShadowMapColor, coord).r;
}

void main() 
{
	vec3 col = texture(DiffuseSampler, texCoord).rgb;
	float screenDepth = texture(DepthSampler, texCoord).r;
	if (screenDepth >= 1.0)
	{
		fragColor = vec4(col, 1.0);
		return;
	}
	
	vec3 localPos = screenToWorldPos(texCoord, screenDepth * 2.0 - 1.0);
	vec3 pos = CameraPos + localPos;
	
	float len = length(localPos);
	float f = FadeDistance;
	float invF = 1.0 / f;
	float start = MinimumRadius;
	float end = ShadowSpan / 2.0;
	
	if (len < start || len > end)
	{
		fragColor = vec4(col, 1.0);
		return;
	}
	
	float distFade = 1.0 - clamp(invF * (len - end + f), 0.0, 1.0);
	
	//https://learnopengl.com/Advanced-Lighting/Shadows/Shadow-Mapping
	float strength = 0.0;
	float transmission = 0.0;
	for (int x = -1; x <= 1; x++)
	{
		for (int y = -1; y <= 1; y++)
		{
			vec3 samplePos = pos + vec3(x, 0.0, y) * 10.0;
			float sampleStrength = shadowStrengthAt(samplePos);
			strength += sampleStrength;
			transmission += shadowTransmissionAt(samplePos) * sampleStrength;
		}
	}
	if (strength > 0.0001)
		transmission /= strength;
	else
		transmission = 1.0;
	strength /= 9.0;
	strength = pow(strength, 0.65);
	float occlusion = clamp(1.35 - transmission, 0.35, 1.0);
	strength *= occlusion;
	strength *= distFade;
	strength = clamp(strength, 0.0, 1.0);
	
	vec3 shadowCol = col * ShadowColorMultiplier;
	vec3 finalCol = mix(col, shadowCol, strength);
	fragColor = vec4(finalCol, 1.0);
}
