#version 430

uniform sampler2D BayerMatrixSampler;

uniform vec4 ColorModulator;
uniform float DitherScale;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
uniform float InsideCloudFactor;

in vec4 vertexColor;
in float fogDistance;
in float vertexDistance;

out vec4 fragColor;

const float WALL_TRANSITION_DISTANCE = 64.0;
const float WALL_TRANSITION_MIN_OPACITY = 0.5;

float getWallTransitionOpacity(float distanceToCamera)
{
	float baseOpacity = mix(WALL_TRANSITION_MIN_OPACITY, 1.0, smoothstep(0.0, WALL_TRANSITION_DISTANCE, distanceToCamera));
	return mix(baseOpacity, 1.0, clamp(InsideCloudFactor, 0.0, 1.0));
}

void main() 
{
	float fogFactor = smoothstep(FogStart, FogEnd, fogDistance);
	float horizonFade = 1.0 - fogFactor;
	float fade = ColorModulator.a * getWallTransitionOpacity(vertexDistance) * horizonFade;
	
	vec4 color = vertexColor * vec4(ColorModulator.rgb, 1.0);
	color = mix(color, FogColor, fogFactor);
	
	fragColor = vec4(color.rgb, fade);
}
