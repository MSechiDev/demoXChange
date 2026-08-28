package org.generation.italy.demoxchange.controllers;

import jakarta.validation.Valid;
import org.generation.italy.demoxchange.model.dto.CreateItemRequest;
import org.generation.italy.demoxchange.model.dto.ItemDto;
import org.generation.italy.demoxchange.model.dto.UpdateItemRequest;
import org.generation.italy.demoxchange.model.entities.ItemCondition;
import org.generation.italy.demoxchange.services.ItemService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/items")
public class ItemController {
    private final ItemService itemService;

    public ItemController(ItemService itemService) {
        this.itemService = itemService;
    }

    @GetMapping
    public List<ItemDto> search(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) ItemCondition condition,
            @RequestParam(required = false) BigDecimal minValue,
            @RequestParam(required = false) BigDecimal maxValue,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "false") boolean includeArchived
    ) {
        return itemService.search(currentUserId(jwt), categoryId, condition, minValue, maxValue, includeArchived, q);
    }

    @GetMapping("/{id}")
    public ItemDto findById(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return itemService.findByIdForOwner(id, currentUserId(jwt));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ItemDto create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateItemRequest request) {
        return itemService.create(currentUserId(jwt), request);
    }

    @PutMapping("/{id}")
    public ItemDto update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long id,
            @Valid @RequestBody UpdateItemRequest request
    ) {
        return itemService.update(id, currentUserId(jwt), request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        itemService.delete(id, currentUserId(jwt));
    }

    private static long currentUserId(Jwt jwt) {
        return jwt.getClaim("uid");
    }
}
