package org.generation.italy.demoxchange.controllers;

import org.generation.italy.demoxchange.model.dto.CounterOfferRequest;
import org.generation.italy.demoxchange.model.dto.OfferDto;
import org.generation.italy.demoxchange.services.OfferService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/offers")
public class OfferController {

    private final OfferService offerService;

    public OfferController(OfferService offerService) {
        this.offerService = offerService;
    }

    @GetMapping("/sent")
    public List<OfferDto> sentOffers(@AuthenticationPrincipal Jwt jwt) {
        return offerService.sentOffers(currentUserId(jwt));
    }

    @GetMapping("/received")
    public List<OfferDto> receivedOffers(@AuthenticationPrincipal Jwt jwt) {
        return offerService.receivedOffers(currentUserId(jwt));
    }

    @PatchMapping("/{offerId}/approve")
    public OfferDto approveOffer(
            @PathVariable Long offerId,
            @AuthenticationPrincipal Jwt jwt) {

        return offerService.approveOffer(offerId, currentUserId(jwt));
    }

    @PostMapping("/{offerId}/counter")
    @ResponseStatus(HttpStatus.CREATED)
    public OfferDto counterOffer(
            @PathVariable Long offerId,
            @RequestBody CounterOfferRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        return offerService.counterOffer(
                offerId, request.itemIds(), request.message(), currentUserId(jwt));
    }

    private static Long currentUserId(Jwt jwt) {
        return jwt.getClaim("uid");
    }
}
