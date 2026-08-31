package org.generation.italy.demoxchange.controllers;

import jakarta.validation.Valid;
import org.generation.italy.demoxchange.model.dto.ItemImageDto;
import org.generation.italy.demoxchange.model.dto.ReorderImagesRequest;
import org.generation.italy.demoxchange.services.ItemImageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/items/{itemId}/images")
public class ItemImageController {
    private final ItemImageService itemImageService;

    public ItemImageController(ItemImageService itemImageService) {
        this.itemImageService = itemImageService;
    }

    @GetMapping
    public List<ItemImageDto> list(@AuthenticationPrincipal Jwt jwt, @PathVariable long itemId) {
        return itemImageService.list(itemId, currentUserId(jwt));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ItemImageDto add(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long itemId,
            @RequestParam("file") MultipartFile file
    ) {
        return itemImageService.add(itemId, currentUserId(jwt), file);
    }

    @PatchMapping("/order")
    public List<ItemImageDto> reorder(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long itemId,
            @Valid @RequestBody ReorderImagesRequest request
    ) {
        return itemImageService.reorder(itemId, currentUserId(jwt), request.imageIds());
    }

    @DeleteMapping("/{imageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long itemId,
            @PathVariable long imageId
    ) {
        itemImageService.delete(itemId, currentUserId(jwt), imageId);
    }

    private static long currentUserId(Jwt jwt) {
        return jwt.getClaim("uid");
    }
}
