package org.generation.italy.demoxchange.services;

import org.generation.italy.demoxchange.model.dto.CreateReportRequest;
import org.generation.italy.demoxchange.model.dto.ReportDto;
import org.generation.italy.demoxchange.model.dto.ReviewReportRequest;
import org.generation.italy.demoxchange.model.entities.AppUser;
import org.generation.italy.demoxchange.model.entities.Listing;
import org.generation.italy.demoxchange.model.entities.Report;
import org.generation.italy.demoxchange.model.entities.ReportReason;
import org.generation.italy.demoxchange.model.entities.ReportStatus;
import org.generation.italy.demoxchange.model.exceptions.BadRequestException;
import org.generation.italy.demoxchange.model.exceptions.ConflictException;
import org.generation.italy.demoxchange.model.exceptions.NotFoundException;
import org.generation.italy.demoxchange.model.repositories.AppUserRepository;
import org.generation.italy.demoxchange.model.repositories.ListingRepository;
import org.generation.italy.demoxchange.model.repositories.ReportRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
public class ReportService {
    private static final Set<ReportStatus> OPEN_STATUSES = EnumSet.of(ReportStatus.aperta, ReportStatus.in_revisione);

    private final ReportRepository reportRepository;
    private final AppUserRepository appUserRepository;
    private final ListingRepository listingRepository;

    public ReportService(ReportRepository reportRepository, AppUserRepository appUserRepository, ListingRepository listingRepository) {
        this.reportRepository = reportRepository;
        this.appUserRepository = appUserRepository;
        this.listingRepository = listingRepository;
    }

    @Transactional(readOnly = true)
    public List<ReportDto> findMine(long reporterId) {
        return reportRepository.findByReporterId(reporterId, Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(ReportService::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReportDto> findAll(ReportStatus status) {
        List<Report> reports = status == null
                ? reportRepository.findAll(Sort.by(Sort.Direction.ASC, "createdAt"))
                : reportRepository.findByStatus(status, Sort.by(Sort.Direction.ASC, "createdAt"));
        return reports.stream().map(ReportService::toDto).toList();
    }

    @Transactional(readOnly = true)
    public ReportDto findById(long id) {
        return toDto(getOrThrow(id));
    }

    @Transactional(readOnly = true)
    public boolean isReporter(long id, long userId) {
        return reportRepository.existsByIdAndReporterId(id, userId);
    }

    @Transactional
    public ReportDto create(long reporterId, CreateReportRequest request) {
        boolean hasUserTarget = request.reportedUserId() != null;
        boolean hasListingTarget = request.reportedListingId() != null;
        if (hasUserTarget == hasListingTarget) {
            throw new BadRequestException("invalid_target", "Exactly one of reportedUserId or reportedListingId must be set");
        }

        AppUser reporter = appUserRepository.findById(reporterId)
                .orElseThrow(() -> new NotFoundException("user_not_found", "User not found: " + reporterId));

        Report report = new Report(reporter, ReportReason.valueOf(request.reason()));
        report.setDescription(request.description());

        if (hasUserTarget) {
            if (request.reportedUserId() == reporterId) {
                throw new BadRequestException("cannot_report_self", "You cannot report yourself");
            }
            AppUser reportedUser = appUserRepository.findById(request.reportedUserId())
                    .orElseThrow(() -> new NotFoundException("user_not_found", "User not found: " + request.reportedUserId()));
            if (reportRepository.existsByReporterIdAndReportedUserIdAndStatusIn(reporterId, request.reportedUserId(), OPEN_STATUSES)) {
                throw new ConflictException("report_already_open", "You already have an open report against this user");
            }
            report.setReportedUser(reportedUser);
        } else {
            Listing reportedListing = listingRepository.findById(request.reportedListingId())
                    .orElseThrow(() -> new NotFoundException("listing_not_found", "Listing not found: " + request.reportedListingId()));
            if (reportRepository.existsByReporterIdAndReportedListingIdAndStatusIn(reporterId, request.reportedListingId(), OPEN_STATUSES)) {
                throw new ConflictException("report_already_open", "You already have an open report against this listing");
            }
            report.setReportedListing(reportedListing);
        }

        Report saved = reportRepository.save(report);
        return toDto(saved);
    }

    @Transactional
    public ReportDto review(long id, long adminUserId, ReviewReportRequest request) {
        Report report = getOrThrow(id);
        if (report.getStatus() == ReportStatus.risolta || report.getStatus() == ReportStatus.respinta) {
            throw new ConflictException("report_already_closed", "This report has already been closed");
        }

        AppUser admin = appUserRepository.findById(adminUserId)
                .orElseThrow(() -> new NotFoundException("user_not_found", "User not found: " + adminUserId));

        report.setStatus(ReportStatus.valueOf(request.status()));
        report.setResolutionNote(request.resolutionNote());
        report.setReviewedBy(admin);
        report.setReviewedAt(OffsetDateTime.now());

        Report flushed = reportRepository.saveAndFlush(report);
        return toDto(flushed);
    }

    private Report getOrThrow(long id) {
        return reportRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("report_not_found", "Report not found: " + id));
    }

    private static ReportDto toDto(Report report) {
        AppUser reportedUser = report.getReportedUser();
        Listing reportedListing = report.getReportedListing();
        AppUser reviewedBy = report.getReviewedBy();

        return new ReportDto(
                report.getId(),
                report.getReporter().getId(),
                report.getReporter().getUsername(),
                reportedUser == null ? null : reportedUser.getId(),
                reportedUser == null ? null : reportedUser.getUsername(),
                reportedListing == null ? null : reportedListing.getId(),
                report.getReason().name(),
                report.getDescription(),
                report.getStatus().name(),
                reviewedBy == null ? null : reviewedBy.getId(),
                reviewedBy == null ? null : reviewedBy.getUsername(),
                report.getReviewedAt(),
                report.getResolutionNote(),
                report.getCreatedAt()
        );
    }
}
