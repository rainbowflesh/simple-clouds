#version 430

uniform sampler2D BayerMatrixSampler;

uniform vec4 ColorModulator;
uniform float DitherScale;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in vec4 vertexColor;
in float fogDistance;
in float vertexDistance;

out vec4 fragColor;

const float WALL_TRANSITION_DISTANCE = 64.0;
const float WALL_TRANSITION_MIN_OPACITY = 0.5;

float getWallTransitionOpacity(float distanceToCamera)
{
	return mix(WALL_TRANSITION_MIN_OPACITY, 1.0, smoothstep(0.0, WALL_TRANSITION_DISTANCE, distanceToCamera));
}

void main() 
{
	float fade = ColorModulator.a * getWallTransitionOpacity(vertexDistance);
	float r = texture(BayerMatrixSampler, gl_FragCoord.xy * DitherScale).r;
	if (fade < r)
		discard;
	
	vec4 color = vertexColor * vec4(ColorModulator.rgb, 1.0);
	color = mix(color, FogColor, smoothstep(FogStart, FogEnd, fogDistance));
	
    fragColor = vec4(color.rgb, 1.0);
}
