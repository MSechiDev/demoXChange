package org.generation.italy.demoxchange.controllers;

import jakarta.validation.Valid;
import org.generation.italy.demoxchange.model.dto.CreateListingRequest;
import org.generation.italy.demoxchange.model.dto.ListingDto;
import org.generation.italy.demoxchange.model.dto.UpdateListingStatusRequest;
import org.generation.italy.demoxchange.services.ListingService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/listings")
public class ListingController {
    private final ListingService listingService;

    public ListingController(ListingService listingService) {
        this.listingService = listingService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public ListingDto create(@Valid @RequestBody CreateListingRequest request,
                             @AuthenticationPrincipal Jwt jwt) {
        return listingService.createListing(extractUserId(jwt), request);
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public List<ListingDto> findMine(@AuthenticationPrincipal Jwt jwt) {
        return listingService.findByOwner(extractUserId(jwt));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("isAuthenticated()")
    public ListingDto updateStatus(@PathVariable Long id,
                                  @Valid @RequestBody UpdateListingStatusRequest request,
                                  @AuthenticationPrincipal Jwt jwt) {
        return listingService.updateStatus(extractUserId(jwt), id, request.status());
    }

    private static Long extractUserId(Jwt jwt) {
        return jwt.getClaim("uid");
    }
}
