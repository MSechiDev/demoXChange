package org.generation.italy.demoxchange.services;

import org.generation.italy.demoxchange.model.dto.CreateReportRequest;
import org.generation.italy.demoxchange.model.dto.ReportDto;
import org.generation.italy.demoxchange.model.dto.ReviewReportRequest;
import org.generation.italy.demoxchange.model.entities.*;
import org.generation.italy.demoxchange.model.exceptions.BadRequestException;
import org.generation.italy.demoxchange.model.exceptions.ConflictException;
import org.generation.italy.demoxchange.model.exceptions.NotFoundException;
import org.generation.italy.demoxchange.model.repositories.AppUserRepository;
import org.generation.italy.demoxchange.model.repositories.ListingRepository;
import org.generation.italy.demoxchange.model.repositories.ReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock private ReportRepository reportRepository;
    @Mock private AppUserRepository appUserRepository;
    @Mock private ListingRepository listingRepository;

    @InjectMocks
    private ReportService reportService;

    private static final long REPORTER_ID = 1L;
    private static final long ADMIN_ID = 99L;

    private AppUser reporter;

    @BeforeEach
    void setUp() {
        reporter = new AppUser("alice", "hash", null);
        ReflectionTestUtils.setField(reporter, "id", REPORTER_ID);
    }

    @Test
    void create_bothTargetsSet_throwsBadRequest() {
        CreateReportRequest request = new CreateReportRequest("spam", null, 2L, 3L);

        assertThatThrownBy(() -> reportService.create(REPORTER_ID, request))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void create_noTargetSet_throwsBadRequest() {
        CreateReportRequest request = new CreateReportRequest("spam", null, null, null);

        assertThatThrownBy(() -> reportService.create(REPORTER_ID, request))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void create_reportingSelf_throwsBadRequest() {
        CreateReportRequest request = new CreateReportRequest("spam", null, REPORTER_ID, null);
        when(appUserRepository.findById(REPORTER_ID)).thenReturn(Optional.of(reporter));

        assertThatThrownBy(() -> reportService.create(REPORTER_ID, request))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void create_alreadyOpenReportAgainstUser_throwsConflict() {
        AppUser reportedUser = new AppUser("bob", "hash", null);
        ReflectionTestUtils.setField(reportedUser, "id", 2L);

        CreateReportRequest request = new CreateReportRequest("spam", "descrizione", 2L, null);
        when(appUserRepository.findById(REPORTER_ID)).thenReturn(Optional.of(reporter));
        when(appUserRepository.findById(2L)).thenReturn(Optional.of(reportedUser));
        when(reportRepository.existsByReporterIdAndReportedUserIdAndStatusIn(eq(REPORTER_ID), eq(2L), any()))
                .thenReturn(true);

        assertThatThrownBy(() -> reportService.create(REPORTER_ID, request))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void create_validUserReport_savesReport() {
        AppUser reportedUser = new AppUser("bob", "hash", null);
        ReflectionTestUtils.setField(reportedUser, "id", 2L);

        CreateReportRequest request = new CreateReportRequest("spam", "descrizione", 2L, null);
        when(appUserRepository.findById(REPORTER_ID)).thenReturn(Optional.of(reporter));
        when(appUserRepository.findById(2L)).thenReturn(Optional.of(reportedUser));
        when(reportRepository.existsByReporterIdAndReportedUserIdAndStatusIn(eq(REPORTER_ID), eq(2L), any()))
                .thenReturn(false);
        when(reportRepository.save(any(Report.class))).thenAnswer(invocation -> {
            Report saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 5L);
            return saved;
        });

        ReportDto result = reportService.create(REPORTER_ID, request);

        assertThat(result.reason()).isEqualTo("spam");
        assertThat(result.reportedUserId()).isEqualTo(2L);
        assertThat(result.status()).isEqualTo("aperta");
    }

    @Test
    void create_listingNotFound_throwsNotFound() {
        CreateReportRequest request = new CreateReportRequest("truffa", null, null, 7L);
        when(appUserRepository.findById(REPORTER_ID)).thenReturn(Optional.of(reporter));
        when(listingRepository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reportService.create(REPORTER_ID, request))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void review_alreadyClosed_throwsConflict() {
        Report report = new Report(reporter, ReportReason.spam);
        ReflectionTestUtils.setField(report, "id", 1L);
        report.setStatus(ReportStatus.risolta);

        when(reportRepository.findById(1L)).thenReturn(Optional.of(report));

        ReviewReportRequest request = new ReviewReportRequest("respinta", "nota");

        assertThatThrownBy(() -> reportService.review(1L, ADMIN_ID, request))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void review_validRequest_updatesReportStatus() {
        Report report = new Report(reporter, ReportReason.spam);
        ReflectionTestUtils.setField(report, "id", 1L);

        AppUser admin = new AppUser("admin", "hash", Set.of(UserRole.ADMIN));
        ReflectionTestUtils.setField(admin, "id", ADMIN_ID);

        when(reportRepository.findById(1L)).thenReturn(Optional.of(report));
        when(appUserRepository.findById(ADMIN_ID)).thenReturn(Optional.of(admin));
        when(reportRepository.saveAndFlush(report)).thenReturn(report);

        ReviewReportRequest request = new ReviewReportRequest("risolta", "gestito");
        ReportDto result = reportService.review(1L, ADMIN_ID, request);

        assertThat(result.status()).isEqualTo("risolta");
        assertThat(result.resolutionNote()).isEqualTo("gestito");
        assertThat(result.reviewedById()).isEqualTo(ADMIN_ID);
    }

    @Test
    void isReporter_delegatesToRepository() {
        when(reportRepository.existsByIdAndReporterId(1L, REPORTER_ID)).thenReturn(true);

        assertThat(reportService.isReporter(1L, REPORTER_ID)).isTrue();
    }
}
