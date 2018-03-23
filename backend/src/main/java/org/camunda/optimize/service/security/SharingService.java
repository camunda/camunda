package org.camunda.optimize.service.security;

import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.result.ReportResultDto;
import org.camunda.optimize.dto.optimize.query.sharing.DashboardShareDto;
import org.camunda.optimize.dto.optimize.query.sharing.ReportShareDto;
import org.camunda.optimize.dto.optimize.query.sharing.ShareSearchDto;
import org.camunda.optimize.dto.optimize.query.sharing.ShareSearchResultDto;
import org.camunda.optimize.dto.optimize.query.sharing.ShareStatusDto;
import org.camunda.optimize.service.dashboard.DashboardService;
import org.camunda.optimize.service.es.reader.SharingReader;
import org.camunda.optimize.service.es.writer.SharingWriter;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.report.ReportService;
import org.camunda.optimize.service.util.ValidationHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @author Askar Akhmerov
 */
@Component
public class SharingService  {
  private final Logger logger = LoggerFactory.getLogger(getClass());

  @Autowired
  private SharingWriter sharingWriter;

  @Autowired
  private SharingReader sharingReader;

  @Autowired
  private ReportService reportService;

  @Autowired
  private DashboardService dashboardService;

  public IdDto createNewReportShareIfAbsent(ReportShareDto createSharingDto) {
    Optional<ReportShareDto> existing =
        sharingReader.findShareForReport(createSharingDto.getReportId());

    IdDto result = existing
        .map(share -> {
          IdDto idDto = new IdDto();
          idDto.setId(share.getId());
          return idDto;
        })
        .orElseGet(() -> createNewReportShare(createSharingDto));
    return result;
  }

  private IdDto createNewReportShare(ReportShareDto createSharingDto) {
    String result = sharingWriter.saveReportShare(createSharingDto).getId();
    IdDto id = new IdDto();
    id.setId(result);
    return id;
  }

  public IdDto crateNewDashboardShare(DashboardShareDto createSharingDto) {
    String result;
    Optional<DashboardShareDto> existing =
        sharingReader.findShareForDashboard(createSharingDto.getDashboardId());

    result = existing
        .map(DashboardShareDto::getId)
        .orElseGet(() -> {
          this.addReportInformation(createSharingDto);
          return sharingWriter.saveDashboardShare(createSharingDto).getId();
        });

    IdDto id = new IdDto();
    id.setId(result);
    return id;
  }

  private void addReportInformation(DashboardShareDto createSharingDto) {
    DashboardDefinitionDto dashboardDefinition =
        dashboardService.getDashboardDefinition(createSharingDto.getDashboardId());
    createSharingDto.setReportShares(dashboardDefinition.getReports());
  }

  public void validateDashboardShare(DashboardShareDto dashboardShare) {
    ValidationHelper.ensureNotEmpty("dashboardId", dashboardShare.getDashboardId());
    dashboardService.getDashboardDefinition(dashboardShare.getDashboardId());
  }

  public void validateReportShare(ReportShareDto reportShare) {
    ValidationHelper.ensureNotEmpty("reportId", reportShare.getReportId());
    reportService.getReport(reportShare.getReportId());
  }

  public void deleteReportShare(String shareId) {
    Optional<ReportShareDto> base = sharingReader.findReportShare(shareId);
    base.ifPresent((share) -> sharingWriter.deleteReportShare(shareId));
  }

  public void deleteDashboardShare(String shareId) {
    Optional<DashboardShareDto> base = sharingReader.findDashboardShare(shareId);
    base.ifPresent((share) -> sharingWriter.deleteDashboardShare(shareId));
  }

  public ReportResultDto evaluateReport(String shareId) {
    Optional<ReportShareDto> optionalReportShare = sharingReader.findReportShare(shareId);

    return optionalReportShare
        .map(this::constructReportShare)
        .orElseThrow(() -> new OptimizeRuntimeException("share [" + shareId + "] does not exist or is of unsupported type"));
  }

  public ReportResultDto evaluateReportForSharedDashboard(String dashboardShareId, String reportId) throws OptimizeException {
    Optional<DashboardShareDto> sharedDashboard = sharingReader.findDashboardShare(dashboardShareId);
    ReportResultDto result;

    if (sharedDashboard.isPresent()) {
      DashboardShareDto share = sharedDashboard.get();
      boolean hasGivenReport = share.getReportShares().stream().anyMatch(r -> r.getId().equals(reportId));
      if (hasGivenReport) {
        result = reportService.evaluateSavedReport(reportId);
      } else {
        String reason = "Cannot evaluate report [" + reportId +
            "] for shared dashboard id [" + dashboardShareId + "]. Given report is not contained in dashboard.";
        logger.error(reason);
        throw new OptimizeRuntimeException(reason);
      }
    } else {
      String reason = "Cannot evaluate report [" + reportId +
          "] for shared dashboard id [" + dashboardShareId + "]. Dashboard does not exist.";
      logger.error(reason);
      throw new OptimizeRuntimeException(reason);
    }

   return result;
  }

  private ReportResultDto constructReportShare(ReportShareDto share) {
    try {
      return reportService.evaluateSavedReport(share.getReportId());
    } catch (OptimizeException e) {
      String reason = "Cannot evaluate shared report [" + share.getReportId() + "]";
      logger.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
  }

  public Optional<DashboardDefinitionDto> evaluateDashboard(String shareId) {
    Optional<DashboardShareDto> sharedDashboard = sharingReader.findDashboardShare(shareId);

    return sharedDashboard
      .map(this::constructDashboard)
      .orElseThrow(() -> new OptimizeRuntimeException("dashboard share [" + shareId + "] does not exist or is of unsupported type"));
  }

  private Optional<DashboardDefinitionDto> constructDashboard(DashboardShareDto share) {
    DashboardDefinitionDto result = dashboardService.getDashboardDefinition(share.getDashboardId());
    return Optional.of(result);
  }

  public void deleteShareForReport(String reportId) {
    Optional<ReportShareDto> share = findShareForReport(reportId);
    share.ifPresent(dto -> this.deleteReportShare(dto.getId()));
  }


  public Optional<ReportShareDto> findShareForReport(String resourceId) {
    return sharingReader.findShareForReport(resourceId);
  }

  public Optional<DashboardShareDto> findShareForDashboard(String resourceId) {
    return sharingReader.findShareForDashboard(resourceId);
  }

  public void adjustDashboardShares(DashboardDefinitionDto updatedDashboard) {
    Optional<DashboardShareDto> dashboardShare =
      findShareForDashboard(
        updatedDashboard.getId());

    dashboardShare.ifPresent(share -> {
        share.setReportShares(updatedDashboard.getReports());
        sharingWriter.updateDashboardShare(share);
    });
  }

  public ShareSearchResultDto checkShareStatus(ShareSearchDto searchRequest) {
    ShareSearchResultDto result = new ShareSearchResultDto();
    if (searchRequest != null && searchRequest.getReports() != null && !searchRequest.getReports().isEmpty()) {
      Map<String, ReportShareDto> shareForReports = sharingReader.findShareForReports(searchRequest.getReports());

      for (String reportId : searchRequest.getReports()) {
        ShareStatusDto toAdd = new ShareStatusDto();
        toAdd.setId(reportId);
        if (shareForReports.containsKey(reportId)) {
          toAdd.setShared(true);
        }
        result.getReports().add(toAdd);
      }
    }

    if (searchRequest != null && searchRequest.getDashboards() != null && !searchRequest.getDashboards().isEmpty()) {
      Map<String, DashboardShareDto> shareForReports = sharingReader.findShareForDashboards(searchRequest.getDashboards());

      for (String dashboardId : searchRequest.getDashboards()) {
        ShareStatusDto toAdd = new ShareStatusDto();
        toAdd.setId(dashboardId);
        if (shareForReports.containsKey(dashboardId)) {
          toAdd.setShared(true);
        }
        result.getDashboards().add(toAdd);
      }
    }

    return result;
  }
}
