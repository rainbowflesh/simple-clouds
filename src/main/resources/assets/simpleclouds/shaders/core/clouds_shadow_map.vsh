#version 430

in vec3 Position;

#moj_import <simpleclouds:opaque.glsl>

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec4 vertexColor;
out float height;
flat out int sideIndex;

void main() 
{
	SideInfo info = sides.data[gl_InstanceID];
	
	vec4 transformedPos = vec4(Position, 1.0) * transformations[uint(info.side)];
	vec3 sideOffset = vec3(info.x, info.y, info.z);
	vec4 finalPos = vec4(transformedPos.xyz * info.radius + sideOffset, 1.0);
	height = finalPos.y;
	sideIndex = info.side;
    gl_Position = ProjMat * ModelViewMat * finalPos;
	
	vertexColor = vec4(vec3(info.brightness), 1.0);
}
