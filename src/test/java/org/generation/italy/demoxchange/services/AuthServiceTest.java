package org.generation.italy.demoxchange.services;

import org.generation.italy.demoxchange.model.dto.CreateUserRequest;
import org.generation.italy.demoxchange.model.dto.LoginRequest;
import org.generation.italy.demoxchange.model.dto.LoginResponse;
import org.generation.italy.demoxchange.model.dto.UserDto;
import org.generation.italy.demoxchange.model.entities.AppUser;
import org.generation.italy.demoxchange.model.exceptions.BadRequestException;
import org.generation.italy.demoxchange.model.exceptions.ConflictException;
import org.generation.italy.demoxchange.model.repositories.AppUserRepository;
import org.generation.italy.demoxchange.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private AppUserRepository appUserRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    private AppUser user;

    @BeforeEach
    void setUp() {
        user = new AppUser("alice", "hashed", Set.of());
        ReflectionTestUtils.setField(user, "id", 1L);
        user.setEmail("alice@example.com");
    }

    @Test
    void login_validCredentials_returnsTokenAndRoles() {
        LoginRequest request = new LoginRequest("alice", "Password123!");
        when(appUserRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(jwtService.createToken(user)).thenReturn("fake-jwt-token");

        LoginResponse response = authService.login(request);

        assertThat(response.token()).isEqualTo("fake-jwt-token");
        verify(authenticationManager).authenticate(any());
    }

    @Test
    void createUser_usernameAlreadyExists_throwsConflict() {
        CreateUserRequest request = new CreateUserRequest("alice", "alice@example.com", "Password123!", Set.of("USER"));
        when(appUserRepository.existsByUsername("alice")).thenReturn(true);

        assertThatThrownBy(() -> authService.createUser(request))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void createUser_emailAlreadyExists_throwsConflict() {
        CreateUserRequest request = new CreateUserRequest("newuser", "alice@example.com", "Password123!", Set.of("USER"));
        when(appUserRepository.existsByUsername("newuser")).thenReturn(false);
        when(appUserRepository.existsByEmail("alice@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.createUser(request))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void createUser_invalidRole_throwsBadRequest() {
        CreateUserRequest request = new CreateUserRequest("newuser", "new@example.com", "Password123!", Set.of("SUPERADMIN"));
        when(appUserRepository.existsByUsername("newuser")).thenReturn(false);
        when(appUserRepository.existsByEmail("new@example.com")).thenReturn(false);

        assertThatThrownBy(() -> authService.createUser(request))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void createUser_validRequest_encodesPasswordAndSavesUser() {
        CreateUserRequest request = new CreateUserRequest("newuser", "new@example.com", "Password123!", Set.of("USER"));
        when(appUserRepository.existsByUsername("newuser")).thenReturn(false);
        when(appUserRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Password123!")).thenReturn("hashed-password");
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(invocation -> {
            AppUser saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 2L);
            return saved;
        });

        UserDto result = authService.createUser(request);

        assertThat(result.username()).isEqualTo("newuser");
        assertThat(result.email()).isEqualTo("new@example.com");
        assertThat(result.roles()).containsExactly("USER");

        ArgumentCaptor<AppUser> savedCaptor = ArgumentCaptor.forClass(AppUser.class);
        verify(appUserRepository).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().getPasswordHash()).isEqualTo("hashed-password");
    }
}
