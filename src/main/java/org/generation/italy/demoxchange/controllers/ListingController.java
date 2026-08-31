package org.generation.italy.demoxchange.controllers;

import jakarta.validation.Valid;
import org.generation.italy.demoxchange.model.dto.CreateListingRequest;
import org.generation.italy.demoxchange.model.dto.ListingDto;
import org.generation.italy.demoxchange.model.dto.ListingSearchDto;
import org.generation.italy.demoxchange.model.dto.MakeOfferRequest;
import org.generation.italy.demoxchange.model.dto.OfferDto;
import org.generation.italy.demoxchange.model.dto.UpdateListingStatusRequest;
import org.generation.italy.demoxchange.services.ListingSearchService;
import org.generation.italy.demoxchange.services.ListingService;
import org.generation.italy.demoxchange.services.OfferService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/listings")
public class ListingController {
    private final ListingService listingService;
    private final ListingSearchService listingSearchService;
    private final OfferService offerService;

    public ListingController(ListingService listingService, ListingSearchService listingSearchService, OfferService offerService) {
        this.listingService = listingService;
        this.listingSearchService = listingSearchService;
        this.offerService = offerService;
    }

    @GetMapping
    public List<ListingSearchDto> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice
    ) {
        return listingSearchService.searchListings(keyword, categoryId, minPrice, maxPrice);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public ListingDto create(@Valid @RequestBody CreateListingRequest request,
                             @AuthenticationPrincipal Jwt jwt) {
        return listingService.createListing(extractUserId(jwt), request);
    }

    @GetMapping("/mine")
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

    @PostMapping("/{listingId}/offers")
    @ResponseStatus(HttpStatus.CREATED)
    public OfferDto makeOffer(@PathVariable Long listingId,
                              @RequestBody MakeOfferRequest request,
                              @AuthenticationPrincipal Jwt jwt) {
        return offerService.makeOffer(listingId, request.itemIds(), request.message(), extractUserId(jwt));
    }

    private static Long extractUserId(Jwt jwt) {
        return jwt.getClaim("uid");
    }
}
