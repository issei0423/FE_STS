package com.fests.dto;

public record LoginResponse(String accessToken, String refreshToken, UserDto user) {
}
