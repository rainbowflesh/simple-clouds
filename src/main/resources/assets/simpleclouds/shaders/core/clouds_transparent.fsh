#version 430

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in vec4 vertexColor;
in float fogDistance;
in float vertexDistance;

out vec4 fragColor;

void main()
{
	float fogFactor = smoothstep(FogStart, FogEnd, fogDistance);
	float horizonFade = 1.0 - fogFactor;
	vec3 rgb = vertexColor.rgb * ColorModulator.rgb;
	fragColor = vec4(rgb, vertexColor.a * ColorModulator.a * horizonFade);
}
