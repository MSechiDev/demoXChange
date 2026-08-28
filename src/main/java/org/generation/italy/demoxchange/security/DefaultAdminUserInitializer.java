package org.generation.italy.demoxchange.security;

import org.generation.italy.demoxchange.model.entities.AppUser;
import org.generation.italy.demoxchange.model.entities.UserRole;
import org.generation.italy.demoxchange.model.repositories.AppUserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class DefaultAdminUserInitializer implements CommandLineRunner {
    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    public DefaultAdminUserInitializer(AppUserRepository appUserRepository, PasswordEncoder passwordEncoder) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        // Read env vars. Do NOT default the password to an insecure value.
        String username = System.getenv().getOrDefault("APP_HEAD_USERNAME", "admin");
        String password = System.getenv().get("APP_HEAD_PASSWORD"); // intentionally no default

        if (appUserRepository.existsByUsername(username)) {
            return;
        }

        // If password is not provided or is obviously insecure, skip automatic creation and warn.
        if (password == null || password.isBlank()) {
            System.err.println("[WARN] APP_HEAD_PASSWORD not set — skipping creation of default HEAD user. " +
                    "To create a HEAD user at startup set APP_HEAD_USERNAME and APP_HEAD_PASSWORD environment variables.");
            return;
        }

        if ("admin".equals(password) || "password".equalsIgnoreCase(password)) {
            System.err.println("[WARN] APP_HEAD_PASSWORD is insecure (uses a common default) — skipping creation of default HEAD user. " +
                    "Use a strong random password via APP_HEAD_PASSWORD.");
            return;
        }

        AppUser user = new AppUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setEnabled(true);
        user.setRoles(Set.of(UserRole.ADMIN));
        appUserRepository.save(user);
    }
}
