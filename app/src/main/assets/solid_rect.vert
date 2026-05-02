#version 300 es

precision mediump float;

uniform mat4 uMVPMatrix;

layout(location = 0) in vec4 vPosition;

void main() {
    gl_Position = uMVPMatrix * vPosition;
}