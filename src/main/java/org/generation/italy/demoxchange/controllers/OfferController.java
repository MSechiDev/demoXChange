package org.generation.italy.demoxchange.controllers;

import org.generation.italy.demoxchange.model.dto.CounterOfferRequest;
import org.generation.italy.demoxchange.model.dto.MakeOfferRequest;
import org.generation.italy.demoxchange.model.dto.OfferDto;
import org.generation.italy.demoxchange.security.AppUserPrincipal;
import org.generation.italy.demoxchange.services.OfferService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/offers")
public class OfferController {

    private final OfferService offerService;

    public OfferController(OfferService offerService) {
        this.offerService = offerService;
    }

    @PostMapping("/listings/{listingId}")
    @ResponseStatus(HttpStatus.CREATED)
    public OfferDto makeOffer(
            @PathVariable Long listingId,
            @RequestBody MakeOfferRequest request,
            @AuthenticationPrincipal AppUserPrincipal principal) {

        return offerService.makeOffer(
                listingId, request.itemIds(), request.message(), principal.getUser().getId());
    }

    @GetMapping("/sent")
    public List<OfferDto> sentOffers(@AuthenticationPrincipal AppUserPrincipal principal) {
        return offerService.sentOffers(principal.getUser().getId());
    }

    @GetMapping("/received")
    public List<OfferDto> receivedOffers(@AuthenticationPrincipal AppUserPrincipal principal) {
        return offerService.receivedOffers(principal.getUser().getId());
    }

    @PutMapping("/{offerId}/approve")
    public OfferDto approveOffer(
            @PathVariable Long offerId,
            @AuthenticationPrincipal AppUserPrincipal principal) {

        return offerService.approveOffer(offerId, principal.getUser().getId());
    }

    @PostMapping("/{offerId}/counter")
    @ResponseStatus(HttpStatus.CREATED)
    public OfferDto counterOffer(
            @PathVariable Long offerId,
            @RequestBody CounterOfferRequest request,
            @AuthenticationPrincipal AppUserPrincipal principal) {

        return offerService.counterOffer(
                offerId, request.itemIds(), request.message(), principal.getUser().getId());
    }
}