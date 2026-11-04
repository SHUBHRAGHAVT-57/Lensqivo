precision highp float;
uniform sampler2D uTexSampler;
uniform mat4 uColorMatrix;
uniform vec2 uTexelStep;
uniform float uSmooth;
uniform float uGlow;
uniform int uJpeg;
varying vec2 vTexCoord;

vec3 toLinear(vec3 c) {
    return mix(c / 12.92, pow((c + 0.055) / 1.055, vec3(2.4)), step(vec3(0.04045), c));
}
vec3 toSrgb(vec3 c) {
    c = max(c, vec3(0.0));
    return mix(c * 12.92, 1.055 * pow(c, vec3(1.0 / 2.4)) - 0.055, step(vec3(0.0031308), c));
}
vec3 sampleLinear(vec2 uv) {
    vec3 c = texture2D(uTexSampler, uv).rgb;
    return uJpeg == 1 ? toLinear(c) : c;
}
void addNeighbor(vec2 offset, vec3 center, inout vec3 total, inout float weights, inout vec3 blur) {
    vec3 neighbor = sampleLinear(vTexCoord + offset * uTexelStep);
    vec3 difference = neighbor - center;
    float weight = exp(-dot(difference, difference) / 0.006);
    total += neighbor * weight;
    weights += weight;
    blur += neighbor;
}
void main() {
    vec3 center = sampleLinear(vTexCoord);
    vec3 color = center;
    if (uSmooth > 0.0 || uGlow > 0.0) {
        vec3 total = center;
        vec3 blur = center;
        float weights = 1.0;
        addNeighbor(vec2(-1.0, -1.0), center, total, weights, blur);
        addNeighbor(vec2( 0.0, -1.0), center, total, weights, blur);
        addNeighbor(vec2( 1.0, -1.0), center, total, weights, blur);
        addNeighbor(vec2(-1.0,  0.0), center, total, weights, blur);
        addNeighbor(vec2( 1.0,  0.0), center, total, weights, blur);
        addNeighbor(vec2(-1.0,  1.0), center, total, weights, blur);
        addNeighbor(vec2( 0.0,  1.0), center, total, weights, blur);
        addNeighbor(vec2( 1.0,  1.0), center, total, weights, blur);
        vec3 encoded = toSrgb(center);
        float cb = 0.5 + dot(encoded, vec3(-0.168736, -0.331264, 0.5));
        float cr = 0.5 + dot(encoded, vec3(0.5, -0.418688, -0.081312));
        // A conservative chroma selector, not a face detector; warm objects can also qualify.
        float skin = smoothstep(0.27, 0.33, cb) * (1.0 - smoothstep(0.48, 0.53, cb))
                   * smoothstep(0.52, 0.56, cr) * (1.0 - smoothstep(0.66, 0.71, cr));
        color = mix(center, total / weights, 0.45 * uSmooth * skin);
        color += 0.06 * uGlow * max(blur / 9.0 - vec3(0.35), vec3(0.0)) * (1.0 - color);
    }
    color = clamp((uColorMatrix * vec4(color, 1.0)).rgb, 0.0, 1.0);
    gl_FragColor = vec4(uJpeg == 1 ? toSrgb(color) : color, 1.0);
}
