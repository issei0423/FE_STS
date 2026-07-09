package com.fests.dto;

import com.fests.entity.User;

public record UserDto(
    Long id,
    String lastName,
    String firstName,
    String email,
    User.Role role,
    String iconUrl
) {
    public static UserDto from(User user, String iconUrl) {
        return new UserDto(
            user.getId(),
            user.getLastName(),
            user.getFirstName(),
            user.getEmail(),
            user.getRole(),
            iconUrl
        );
    }
}
