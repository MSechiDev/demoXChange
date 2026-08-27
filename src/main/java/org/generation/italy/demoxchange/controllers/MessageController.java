package org.generation.italy.demoxchange.controllers;

import jakarta.validation.Valid;
import org.generation.italy.demoxchange.model.dto.MessageDto;
import org.generation.italy.demoxchange.model.dto.SendMessageRequest;
import org.generation.italy.demoxchange.services.MessageService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class MessageController {
    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @GetMapping("/api/messages/mine")
    public List<MessageDto> findMine(@AuthenticationPrincipal Jwt jwt) {
        return messageService.findMine(currentUserId(jwt));
    }

    @GetMapping("/api/offers/{offerId}/messages")
    @PreAuthorize("@messageService.isParticipant(#offerId, authentication.principal.claims['uid'])")
    public List<MessageDto> findThread(@PathVariable long offerId, @AuthenticationPrincipal Jwt jwt) {
        return messageService.findThread(offerId, currentUserId(jwt));
    }

    @PostMapping("/api/offers/{offerId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@messageService.isParticipant(#offerId, authentication.principal.claims['uid'])")
    public MessageDto send(@PathVariable long offerId, @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SendMessageRequest request) {
        return messageService.send(offerId, currentUserId(jwt), request);
    }

    @DeleteMapping("/api/messages/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@messageService.isSender(#id, authentication.principal.claims['uid'])")
    public void delete(@PathVariable long id) {
        messageService.delete(id);
    }

    private static long currentUserId(Jwt jwt) {
        return jwt.getClaim("uid");
    }
}
