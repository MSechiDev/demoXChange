package org.generation.italy.demoxchange.services;

import org.generation.italy.demoxchange.model.dto.CreateUserRequest;
import org.generation.italy.demoxchange.model.dto.LoginRequest;
import org.generation.italy.demoxchange.model.dto.LoginResponse;
import org.generation.italy.demoxchange.model.dto.UserDto;
import org.generation.italy.demoxchange.model.entities.AppUser;
import org.generation.italy.demoxchange.model.entities.UserRole;
import org.generation.italy.demoxchange.model.exceptions.BadRequestException;
import org.generation.italy.demoxchange.model.exceptions.ConflictException;
import org.generation.italy.demoxchange.model.exceptions.NotFoundException;
import org.generation.italy.demoxchange.model.repositories.AppUserRepository;
import org.generation.italy.demoxchange.security.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AuthService {
    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(
            AppUserRepository appUserRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtService jwtService
    ) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password())
        );

        AppUser user = appUserRepository.findByUsername(request.username())
                .orElseThrow(() -> new NotFoundException("user_not_found", "User not found: " + request.username()));

        String token = jwtService.createToken(user);
        return new LoginResponse(token, user.getRoles().stream().map(Enum::name).toList());
    }

    @Transactional(readOnly = true)
    public UserDto getCurrentUser(long userId) {
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("user_not_found", "User not found: " + userId));
        return new UserDto(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.isEnabled(),
                user.getRoles().stream().map(Enum::name).collect(Collectors.toSet())
        );
    }

    @Transactional
    public UserDto createUser(CreateUserRequest request) {
        if (request.username() == null || request.username().isBlank()) {
            throw new BadRequestException("invalid_request", "Username is required");
        }
        if (request.password() == null || request.password().isBlank()) {
            throw new BadRequestException("invalid_request", "Password is required");
        }
        if (appUserRepository.existsByUsername(request.username())) {
            throw new ConflictException("username_unavailable", "Username already exists: " + request.username());
        }
        if (appUserRepository.existsByEmail(request.email())) {
            throw new ConflictException("email_unavailable", "Email already exists: " + request.email());
        }

        AppUser user = new AppUser();
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setEnabled(true);
        // Public self-registration can only ever create USER accounts. ADMIN accounts are
        // provisioned exclusively via DefaultAdminUserInitializer at startup; any role the
        // caller sends here is ignored so an unauthenticated request can't grant itself ADMIN.
        user.setRoles(Set.of(UserRole.USER));

        AppUser saved = appUserRepository.save(user);
        return new UserDto(
                saved.getId(),
                saved.getUsername(),
                saved.getEmail(),
                saved.isEnabled(),
                saved.getRoles().stream().map(Enum::name).collect(Collectors.toSet())
        );
    }
}
