#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#ifdef GLASS
#moj_import <minecraft:globals.glsl>
#endif
#if defined(GLASS) || defined(IMAGE)
uniform sampler2D Sampler0;
#endif

in vec4 vertexColor;
in vec2 local;
flat in vec4 shape;
flat in float soft;

out vec4 fragColor;

// distance to a rounded box, negative inside
float box(vec2 p, vec2 hs, float r) {
    vec2 q = abs(p) - hs + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

void main() {
    float r = min(shape.z, min(shape.x, shape.y));
    float d = box(local, shape.xy, r);
    float aa = max(fwidth(d), 0.0001);
    float a;
    if (soft > 0.0) {
        // full strength at the edge, gone twice the blur out, so a widget shadow reads without a hard rim
        a = 1.0 - smoothstep(0.0, soft * 2.0, d);
    } else if (shape.w > 0.0) {
        // a ring inside the edge, shape.w thick
        a = clamp(0.5 - (abs(d + shape.w * 0.5) - shape.w * 0.5) / aa, 0.0, 1.0);
    } else {
        a = clamp(0.5 - d / aa, 0.0, 1.0);
    }
    vec4 color = vertexColor;
#ifdef GLASS
    color = vec4(texture(Sampler0, gl_FragCoord.xy / ScreenSize).rgb, 1.0) * vertexColor;
#endif
#ifdef IMAGE
    // the whole texture stretched over the box, corners cut by the same distance field
    color = texture(Sampler0, local / (shape.xy * 2.0) + 0.5) * vertexColor;
#endif
    color.a *= a;
    if (color.a <= 0.0) {
        discard;
    }
    fragColor = color * ColorModulator;
}
