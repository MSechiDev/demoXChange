package org.generation.italy.demoxchange.model.dto;

import java.util.Set;

public record UserDto(
        long id,
        String username,
        String email,
        boolean enabled,
        Set<String> roles
) {}

