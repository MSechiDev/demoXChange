package org.generation.italy.demoxchange.controllers;

import org.generation.italy.demoxchange.model.dto.ListingSearchDto;
import org.generation.italy.demoxchange.services.ListingSearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/listings")
public class ListingSearchController {

    private final ListingSearchService listingSearchService;

    public ListingSearchController(ListingSearchService listingSearchService) {
        this.listingSearchService = listingSearchService;
    }

    @GetMapping("/search")
    public ResponseEntity<List<ListingSearchDto>> searchListings(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice
    ) {
        List<ListingSearchDto> results = listingSearchService.searchListings(keyword, categoryId, minPrice, maxPrice);
        return ResponseEntity.ok(results);
    }
}