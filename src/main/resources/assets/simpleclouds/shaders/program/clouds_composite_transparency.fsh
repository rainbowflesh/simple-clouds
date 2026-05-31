#version 430

uniform sampler2D DiffuseSampler;
uniform sampler2D MainDepthSampler;
uniform sampler2D CloudsTexture;
uniform sampler2D CloudTransparencyTexture;
uniform sampler2D CloudsDepthTexture;
uniform int UseSceneDepthOcclusion;
uniform float SceneOcclusionAlphaFloor;

in vec2 texCoord;
in vec2 oneTexel;
out vec4 fragColor;

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
	vec4 transparentCloudCol = texture(CloudTransparencyTexture, texCoord);
	vec3 cloudPremul = cloudCol.rgb * cloudCol.a;
	float cloudAlpha = cloudCol.a;
	if (transparentCloudCol.a > 0.0)
	{
		cloudPremul = transparentCloudCol.rgb + cloudPremul * (1.0 - transparentCloudCol.a);
		cloudAlpha = transparentCloudCol.a + cloudAlpha * (1.0 - transparentCloudCol.a);
	}

	vec3 bg = texture(DiffuseSampler, texCoord).rgb;
	if (sceneHasGeometry && cloudInFrontOfScene)
		cloudAlpha = max(cloudAlpha, SceneOcclusionAlphaFloor);
	vec3 finalCol = cloudPremul + bg * (1.0 - cloudAlpha);
	fragColor = vec4(finalCol, 1.0);
}
