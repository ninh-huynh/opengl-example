#version 300 es

precision mediump float;

uniform sampler2D uTexture;
in vec2 position;
out vec4 outColor;
void main() {
    outColor = texture(uTexture, position);
}