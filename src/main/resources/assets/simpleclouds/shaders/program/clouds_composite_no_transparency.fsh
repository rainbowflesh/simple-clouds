#version 430

uniform sampler2D DiffuseSampler;
uniform sampler2D MainDepthSampler;
uniform sampler2D CloudsTexture;
uniform sampler2D CloudsDepthTexture;
uniform int UseSceneDepthOcclusion;

in vec2 texCoord;
in vec2 oneTexel;
out vec4 fragColor;

void main() 
{
	float sceneDepth = texture(MainDepthSampler, texCoord).r;
	float cloudDepth = texture(CloudsDepthTexture, texCoord).r;
	if (UseSceneDepthOcclusion != 0 && sceneDepth < cloudDepth)
	{
		fragColor = vec4(texture(DiffuseSampler, texCoord).rgb, 1.0);
		return;
	}

	vec4 cloudCol = texture(CloudsTexture, texCoord);
	vec3 bg = texture(DiffuseSampler, texCoord).rgb;
	vec3 finalCol = bg;
	finalCol = vec3(cloudCol.rgb * cloudCol.a + finalCol * (1.0 - cloudCol.a));
	fragColor = vec4(finalCol, 1.0);
}
