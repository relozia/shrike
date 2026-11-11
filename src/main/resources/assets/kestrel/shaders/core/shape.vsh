#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;
// UV0: where this vertex is relative to the shape's middle, in the shape's own units
// UV1: half width and half height, UV2: corner radius and outline thickness, all times 8
// LineWidth: how soft the edge is (shadows), 0 is a plain anti aliased edge
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in float LineWidth;

out vec4 vertexColor;
out vec2 local;
flat out vec4 shape;
flat out float soft;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexColor = Color;
    local = UV0;
    shape = vec4(vec2(UV1) / 8.0, vec2(UV2) / 8.0);
    soft = LineWidth;
}
