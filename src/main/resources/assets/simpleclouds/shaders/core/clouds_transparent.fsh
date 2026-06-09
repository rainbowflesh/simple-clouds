// Weighted-blended order-independent transparency
// https://jcgt.org/published/0002/02/09/paper.pdf and http://casual-effects.blogspot.com/2015/03/implemented-weighted-blended-order.html

#version 430

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in vec4 vertexColor;
in float fogDistance;
in float vertexDistance;
flat in int faceVisible;

layout(location = 0) out vec4 accumColor;
layout(location = 1) out float revealage;

void main()
{
	if (faceVisible == 0)
		discard;
	float fogFactor = smoothstep(FogStart, FogEnd, fogDistance);
	float horizonFade = 1.0 - fogFactor;
	vec3 rgb = vertexColor.rgb * ColorModulator.rgb;
	vec4 color = vec4(rgb, vertexColor.a * ColorModulator.a * horizonFade);

	vec4 premul = vec4(color.rgb * color.a, color.a);

	float z = min(vertexDistance / 1000.0, 1.0);
	float weight = max(premul.a * 3000.0 * pow(1.0 - z, 3.0), 0.01);

	accumColor = premul * weight;
	revealage = premul.a;
}
