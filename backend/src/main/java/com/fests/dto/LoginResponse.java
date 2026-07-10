package com.fests.dto;

public record LoginResponse(String accessToken, UserDto user) {
}
