package org.generation.italy.demoxchange.controllers;

import org.generation.italy.demoxchange.model.dto.ExchangeDto;
import org.generation.italy.demoxchange.services.ExchangeService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/exchanges")
public class ExchangeController {
    private final ExchangeService exchangeService;

    public ExchangeController(ExchangeService exchangeService) {
        this.exchangeService = exchangeService;
    }

    @GetMapping("/mine")
    public List<ExchangeDto> findMine(@AuthenticationPrincipal Jwt jwt) {
        return exchangeService.findMine(currentUserId(jwt));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@exchangeService.isParticipant(#id, authentication.principal.claims['uid'])")
    public ExchangeDto findById(@PathVariable long id) {
        return exchangeService.findById(id);
    }

    @PutMapping("/{id}/confirm")
    @PreAuthorize("@exchangeService.isParticipant(#id, authentication.principal.claims['uid'])")
    public ExchangeDto confirm(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
        return exchangeService.confirm(id, currentUserId(jwt));
    }

    @PutMapping("/{id}/cancel")
    @PreAuthorize("@exchangeService.isParticipant(#id, authentication.principal.claims['uid'])")
    public ExchangeDto cancel(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
        return exchangeService.cancel(id, currentUserId(jwt));
    }

    private static long currentUserId(Jwt jwt) {
        return jwt.getClaim("uid");
    }
}
