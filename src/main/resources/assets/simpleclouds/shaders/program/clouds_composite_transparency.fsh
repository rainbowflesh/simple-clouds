// Resolves transparent clouds via weighted-blended order-independent transparency before compositing.
// https://jcgt.org/published/0002/02/09/paper.pdf and http://casual-effects.blogspot.com/2015/03/implemented-weighted-blended-order.html

#version 430

#define EPSILON 0.00001

uniform sampler2D DiffuseSampler;
uniform sampler2D MainDepthSampler;
uniform sampler2D CloudsTexture;
uniform sampler2D AccumTexture;
uniform sampler2D RevealageTexture;
uniform sampler2D CloudsDepthTexture;
uniform int UseSceneDepthOcclusion;
uniform float SceneOcclusionAlphaFloor;

in vec2 texCoord;
in vec2 oneTexel;
out vec4 fragColor;

float max4(vec4 col)
{
	return max(max(max(col.r, col.g), col.b), col.a);
}

void main()
{
	float sceneDepth = texture(MainDepthSampler, texCoord).r;
	float cloudDepth = texture(CloudsDepthTexture, texCoord).r;
	bool sceneHasGeometry = sceneDepth < 1.0;
	bool cloudInFrontOfScene = cloudDepth < sceneDepth;
	if (UseSceneDepthOcclusion != 0 && sceneHasGeometry && !cloudInFrontOfScene)
	{
		fragColor = vec4(texture(DiffuseSampler, texCoord).rgb, 1.0);
		return;
	}

	vec4 cloudCol = texture(CloudsTexture, texCoord);
	vec3 cloudPremul = cloudCol.rgb * cloudCol.a;
	float cloudAlpha = cloudCol.a;

	ivec2 uv = ivec2(gl_FragCoord.xy);
	float revealage = texelFetch(RevealageTexture, uv, 0).r;
	if (revealage < 1.0)
	{
		vec4 accum = texelFetch(AccumTexture, uv, 0);
		if (isinf(max4(abs(accum))))
			accum.rgb = vec3(accum.a);

		vec3 avg = accum.rgb / max(accum.a, EPSILON);
		vec3 transparentPremul = avg * (1.0 - revealage);
		float transparentAlpha = 1.0 - revealage;

		cloudPremul = transparentPremul + cloudPremul * (1.0 - transparentAlpha);
		cloudAlpha = transparentAlpha + cloudAlpha * (1.0 - transparentAlpha);
	}

	vec3 bg = texture(DiffuseSampler, texCoord).rgb;
	if (sceneHasGeometry && cloudInFrontOfScene)
		cloudAlpha = max(cloudAlpha, SceneOcclusionAlphaFloor);
	vec3 finalCol = cloudPremul + bg * (1.0 - cloudAlpha);
	fragColor = vec4(finalCol, 1.0);
}
