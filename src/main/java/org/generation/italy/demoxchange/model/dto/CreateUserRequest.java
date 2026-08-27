package org.generation.italy.demoxchange.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.generation.italy.demoxchange.model.validation.StrongPassword;

import java.util.Set;

public record CreateUserRequest(
        @NotBlank
        @Size(max = 70)
        String username,

        @NotBlank
        @StrongPassword
        String password,

        @NotEmpty
        Set<@Pattern(regexp = "GUEST|USER|ADMIN", flags = Pattern.Flag.CASE_INSENSITIVE, message = "must be GUEST, USER, or ADMIN") String> roles
) {}
