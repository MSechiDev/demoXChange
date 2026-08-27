package org.generation.italy.demoxchange.model.repositories;

import org.generation.italy.demoxchange.model.entities.Report;
import org.generation.italy.demoxchange.model.entities.ReportStatus;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ReportRepository extends JpaRepository<Report, Long> {
    List<Report> findByReporterId(Long reporterId, Sort sort);
    List<Report> findByStatus(ReportStatus status, Sort sort);
    boolean existsByReporterIdAndReportedUserIdAndStatusIn(Long reporterId, Long reportedUserId, Collection<ReportStatus> statuses);
    boolean existsByReporterIdAndReportedListingIdAndStatusIn(Long reporterId, Long reportedListingId, Collection<ReportStatus> statuses);
    boolean existsByIdAndReporterId(Long id, Long reporterId);
}
