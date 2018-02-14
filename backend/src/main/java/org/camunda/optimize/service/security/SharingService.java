package org.camunda.optimize.service.security;

import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.ReportLocationDto;
import org.camunda.optimize.dto.optimize.query.report.result.ReportResultDto;
import org.camunda.optimize.dto.optimize.query.sharing.DashboardDefinitionShareDto;
import org.camunda.optimize.dto.optimize.query.sharing.DashboardShareDto;
import org.camunda.optimize.dto.optimize.query.sharing.EvaluatedDashboardShareDto;
import org.camunda.optimize.dto.optimize.query.sharing.EvaluatedReportShareDto;
import org.camunda.optimize.dto.optimize.query.sharing.ReportShareDto;
import org.camunda.optimize.dto.optimize.query.sharing.ReportShareLocationDto;
import org.camunda.optimize.dto.optimize.query.sharing.SharedResourceType;
import org.camunda.optimize.service.dashboard.DashboardService;
import org.camunda.optimize.service.es.reader.SharingReader;
import org.camunda.optimize.service.es.writer.SharingWriter;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.exceptions.OptimizeValidationException;
import org.camunda.optimize.service.report.ReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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

  /**
   * NOTE: this method does not perform validation
   */
  public IdDto crateNewReportShare(ReportShareDto createSharingDto) {
    String result;
    Optional<ReportShareDto> existing =
        sharingReader.findShareForReport(createSharingDto.getReportId(), createSharingDto.getType());

    result = existing
      .map(ReportShareDto::getId)
      .orElseGet(() -> sharingWriter.saveReportShare(createSharingDto).getId());

    IdDto id = new IdDto();
    id.setId(result);
    return id;
  }

  public IdDto crateNewDashboardShare(DashboardShareDto createSharingDto) {
    String result;
    Optional<DashboardShareDto> existing =
        sharingReader.findShareForDashboard(createSharingDto.getDashboardId(), createSharingDto.getType());

    result = existing
        .map(DashboardShareDto::getId)
        .orElseGet(() -> {
          this.shareReportsOfDashboard(createSharingDto);
          return sharingWriter.saveDashboardShare(createSharingDto).getId();
        });

    IdDto id = new IdDto();
    id.setId(result);
    return id;
  }

  private void shareReportsOfDashboard(DashboardShareDto createSharingDto) {
    try {
      DashboardDefinitionDto dashboardDefinition =
          dashboardService.getDashboardDefinition(createSharingDto.getDashboardId());

      if (dashboardDefinition.getReports() != null) {
        List<String> reportShares = new ArrayList<>();
        for (ReportLocationDto report : dashboardDefinition.getReports()) {
          IdDto idDto = this.crateNewReportShare(constructDashboardReportShareDto(report));
          reportShares.add(idDto.getId());
        }
        createSharingDto.setReportShares(reportShares);
      }

    } catch (IOException | OptimizeException e) {
      logger.error("can't find dashboard", e);
    }
  }

  private ReportShareDto constructDashboardReportShareDto(ReportLocationDto report) {
    ReportShareDto result = new ReportShareDto();
    result.setType(SharedResourceType.DASHBOARD_REPORT);
    result.setReportId(report.getId());
    return result;
  }

  public void validateDashboardShare(DashboardShareDto createSharingDto) {
    if (SharedResourceType.DASHBOARD.equals(createSharingDto.getType())) {
      try {
        dashboardService.getDashboardDefinition(createSharingDto.getDashboardId());
      } catch (IOException e) {
        logger.error("can't fetch dashboard [{}]", createSharingDto.getDashboardId(), e);
      } catch (OptimizeException e) {
        logger.error("can't fetch dashboard [{}]", createSharingDto.getDashboardId(), e);
        throw new OptimizeValidationException(e.getMessage());
      }
    } else {
      throw new OptimizeValidationException("Specified share type is not allowed");
    }
  }

  public void validateReportShare(ReportShareDto createSharingDto) {
    if (SharedResourceType.REPORT.equals(createSharingDto.getType())) {
      try {
        reportService.getReport(createSharingDto.getReportId());
      } catch (IOException e) {
        logger.error("can't fetch report [{}]", createSharingDto.getReportId(), e);
      } catch (OptimizeException e) {
        logger.error("can't fetch report [{}]", createSharingDto.getReportId(), e);
        throw new OptimizeValidationException(e.getMessage());
      }
    } else {
      throw new OptimizeValidationException("Specified share type is not allowed");
    }
  }

  public void deleteReportShare(String shareId) {
    Optional<ReportShareDto> base = sharingReader.findReportShare(shareId);
    base.ifPresent((share) -> sharingWriter.deleteReportShare(shareId));
  }

  public void deleteDashboardShare(String shareId) {
    Optional<DashboardShareDto> base = sharingReader.findDashboardShare(shareId);
    base.ifPresent((share) -> {
      if (share.getReportShares() != null) {
        for (String reportShare : share.getReportShares()) {
          sharingWriter.deleteReportShare(reportShare);
        }
      }
      sharingWriter.deleteDashboardShare(shareId);
    });
  }

  public Optional<EvaluatedReportShareDto> evaluateReport(String shareId) {
    Optional<ReportShareDto> base = sharingReader.findReportShare(shareId);

    Optional<EvaluatedReportShareDto> result = base
        .filter(share -> SharedResourceType.REPORT.equals(share.getType()) || SharedResourceType.DASHBOARD_REPORT.equals(share.getType()))
        .map(share -> this.constructReportShare(share))
        .orElseThrow(() -> new OptimizeRuntimeException("share [" + shareId + "] does not exist or is of unsupported type"));

    return result;
  }

  private Optional<EvaluatedReportShareDto> constructReportShare(ReportShareDto share) {
    Optional<EvaluatedReportShareDto> result = Optional.empty();
    EvaluatedReportShareDto wrapped = new EvaluatedReportShareDto(share);

    try {
      ReportResultDto reportResultDto = reportService.evaluateSavedReport(wrapped.getReportId());
      wrapped.setReport(reportResultDto);
      result = Optional.of(wrapped);
    } catch (IOException | OptimizeException e) {
      logger.error("can't evaluate shared report []", wrapped.getReportId());
    }

    return result;
  }

  public Optional<EvaluatedDashboardShareDto> evaluateDashboard(String shareId) {
    Optional<DashboardShareDto> base = sharingReader.findDashboardShare(shareId);

    Optional<EvaluatedDashboardShareDto> result = base
        .filter(share -> SharedResourceType.DASHBOARD.equals(share.getType()))
        .map(share -> constructDashboard(share))
        .orElseThrow(() -> new OptimizeRuntimeException("share [" + shareId + "] does not exist or is of unsupported type"));
    return result;
  }

  private Optional<EvaluatedDashboardShareDto> constructDashboard(DashboardShareDto share) {
    EvaluatedDashboardShareDto result = new EvaluatedDashboardShareDto(share);

    try {
      DashboardDefinitionDto dashboardDefinition = dashboardService.getDashboardDefinition(share.getDashboardId());
      DashboardDefinitionShareDto shareData = DashboardDefinitionShareDto.of(dashboardDefinition);
      shareData.setReportShares(constructReportShares(dashboardDefinition.getReports()));
      result.setDashboard(shareData);
    } catch (IOException | OptimizeException e) {
      logger.error("can't find dashboard [{}]", share.getDashboardId(), e);
    }

    return Optional.of(result);
  }

  private List<ReportShareLocationDto> constructReportShares(List<ReportLocationDto> reports) {
    List<ReportShareLocationDto> result = null;
    if (reports != null) {
      Map<String, ReportLocationDto> reportLocationsMap = new HashMap<>();
      for (ReportLocationDto report : reports) {
        reportLocationsMap.put(report.getId(), report);
      }

      List<ReportShareDto> dashboardReports = this.findSharesForDashboardReports(reportLocationsMap.keySet());

      result = new ArrayList<>();
      for (ReportShareDto reportShare : dashboardReports) {
        ReportShareLocationDto toAdd = constructReportShareLocation(reportLocationsMap, reportShare);
        result.add(toAdd);
      }
    }
    return result;
  }

  private List<ReportShareDto> findSharesForDashboardReports(Set<String> resourceIds) {
    return sharingReader.findReportSharesForResources(resourceIds, SharedResourceType.DASHBOARD_REPORT);
  }

  private ReportShareLocationDto constructReportShareLocation(
      Map<String, ReportLocationDto> reportLocationsMap, ReportShareDto reportShare) {
    ReportLocationDto reportLocationDto = reportLocationsMap.get(reportShare.getReportId());
    ReportShareLocationDto toAdd = mapToReportShareLocationDto(
        reportLocationDto, reportShare.getId(), reportShare.getReportId());
    return toAdd;
  }

  private ReportShareLocationDto mapToReportShareLocationDto(
      ReportLocationDto reportLocationDto, String shareId, String reportId) {
    ReportShareLocationDto toAdd = new ReportShareLocationDto();
    toAdd.setShareId(shareId);
    toAdd.setId(reportId);
    toAdd.setDimensions(reportLocationDto.getDimensions());
    toAdd.setPosition(reportLocationDto.getPosition());
    return toAdd;
  }

  public void deleteShareForReport(String reportId) {
    Optional<ReportShareDto> share = findShareForReport(reportId, SharedResourceType.DASHBOARD_REPORT);
    share.ifPresent(dto -> this.deleteReportShare(dto.getId()));

    share = findShareForReport(reportId, SharedResourceType.REPORT);
    share.ifPresent(dto -> this.deleteReportShare(dto.getId()));
  }

  public ReportShareDto findShareForReport(String resourceId) {
    return findShareForReport(resourceId, SharedResourceType.REPORT)
        .orElse(null);
  }

  private Optional<ReportShareDto> findShareForReport(String resourceId, SharedResourceType type) {
    return sharingReader.findShareForReport(resourceId, type);
  }

  public DashboardShareDto findShareForDashboard(String resourceId) {
    return findShareForDashboard(resourceId, SharedResourceType.DASHBOARD)
        .orElse(null);
  }

  private Optional<DashboardShareDto> findShareForDashboard(String resourceId, SharedResourceType type) {
    return sharingReader.findShareForDashboard(resourceId, type);
  }

  public void adjustDashboardShares(DashboardDefinitionDto updatedDashboard) {
    Optional<DashboardShareDto> dashboardShare = findShareForDashboard(
        updatedDashboard.getId(), SharedResourceType.DASHBOARD);

    dashboardShare.ifPresent(share -> {
      if (share.getReportShares() != null) {
        for (String dashboardReportShareId : share.getReportShares()) {
          this.deleteReportShare(dashboardReportShareId);
        }
      }

      if (updatedDashboard.getReports() != null) {
        List<String> reportShares = new ArrayList<>();
        for (ReportLocationDto report : updatedDashboard.getReports()) {
          IdDto idDto = this.crateNewReportShare(constructDashboardReportShareDto(report));
          reportShares.add(idDto.getId());
        }
        share.setReportShares(reportShares);
        sharingWriter.updateDashboardShare(share);
      }
    });

  }
}
