#version 300 es

precision mediump float;

uniform mat4 uMVPMatrix;

layout(location = 0) in vec4 vPosition;
layout(location = 1) in vec4 vTextureCoordinate;
out vec2 position;

void main() {
    gl_Position = uMVPMatrix * vPosition;
    position = vTextureCoordinate.xy;
}