#version 330

// the new frame mixed with the blurred frame before it. Strength is how much of the old one stays
uniform sampler2D InSampler;
uniform sampler2D PrevSampler;

layout(std140) uniform MotionConfig {
    float Strength;
};

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec4 now = texture(InSampler, texCoord);
    vec4 before = texture(PrevSampler, texCoord);
    fragColor = vec4(mix(now.rgb, before.rgb, Strength), 1.0);
}
