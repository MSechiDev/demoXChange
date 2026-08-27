package org.generation.italy.demoxchange.controllers;

import org.generation.italy.demoxchange.model.dto.UserProfileDto;
import org.generation.italy.demoxchange.services.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/{id}/profile")
    public UserProfileDto getProfile(@PathVariable long id) {
        return userService.getProfile(id);
    }
}