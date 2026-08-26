package org.generation.italy.demoxchange.controllers;

import jakarta.validation.Valid;
import org.generation.italy.demoxchange.model.dto.CreateReportRequest;
import org.generation.italy.demoxchange.model.dto.ReportDto;
import org.generation.italy.demoxchange.model.dto.ReviewReportRequest;
import org.generation.italy.demoxchange.model.entities.ReportStatus;
import org.generation.italy.demoxchange.services.ReportService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.List;

@RestController
@RequestMapping("/api/reports")
public class ReportController {
    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/mine")
    public List<ReportDto> findMine(@AuthenticationPrincipal Jwt jwt) {
        return reportService.findMine(currentUserId(jwt));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<ReportDto> findAll(@RequestParam(required = false) String status) {
        ReportStatus filter = status == null ? null : ReportStatus.valueOf(status);
        return reportService.findAll(filter);
    }

    @GetMapping("/{id}")
    public ReportDto findById(@PathVariable long id, @AuthenticationPrincipal Jwt jwt, Authentication authentication) {
        return reportService.findById(id, currentUserId(jwt), isAdmin(authentication));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReportDto create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateReportRequest request) {
        return reportService.create(currentUserId(jwt), request);
    }

    @PutMapping("/{id}/review")
    @PreAuthorize("hasRole('ADMIN')")
    public ReportDto review(@PathVariable long id, @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ReviewReportRequest request) {
        return reportService.review(id, currentUserId(jwt), request);
    }

    private static long currentUserId(Jwt jwt) {
        return jwt.getClaim("uid");
    }

    private static boolean isAdmin(Authentication authentication) {
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        return authorities.stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}
