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
  public IdDto crateNewReportShareIfAbsent(ReportShareDto createSharingDto) {
    Optional<ReportShareDto> existing =
        sharingReader.findShareForReport(createSharingDto.getReportId(), createSharingDto.getType());

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

      if (dashboardDefinition.getReports() != null && !dashboardDefinition.getReports().isEmpty()) {
        List<String> newReportShares = createReportShares(dashboardDefinition);
        createSharingDto.setReportShares(newReportShares);
      }

    } catch (IOException | OptimizeException e) {
      logger.error("can't find dashboard", e);
    }
  }

  private ReportShareDto constructDashboardReportShareDto(ReportLocationDto report) {
    ReportShareDto result = new ReportShareDto();
    result.setType(SharedResourceType.DASHBOARD_REPORT);
    result.setReportId(report.getId());
    result.setPosition(report.getPosition());
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
    } catch (OptimizeException e) {
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
      shareData.setReportShares(constructReportShares(dashboardDefinition.getReports(), share.getReportShares()));
      result.setDashboard(shareData);
    } catch (IOException | OptimizeException e) {
      logger.error("can't find dashboard [{}]", share.getDashboardId(), e);
    }

    return Optional.of(result);
  }

  private List<ReportShareLocationDto> constructReportShares(List<ReportLocationDto> reports, List<String> reportShares) {
    List<ReportShareLocationDto> result = null;
    if (reports != null && !reports.isEmpty()) {
      Map<String, ReportLocationDto> reportLocationsMap = new HashMap<>();
      for (ReportLocationDto report : reports) {
        String key = getReportKey(report);
        reportLocationsMap.put(key, report);
      }

      List<ReportShareDto> dashboardReports = this.findShares(reportShares);

      result = new ArrayList<>();
      for (ReportShareDto reportShare : dashboardReports) {
        if (reportShares.contains(reportShare.getId())) {
          ReportShareLocationDto toAdd = constructReportShareLocation(reportLocationsMap, reportShare);
          result.add(toAdd);
        }
      }
    } else {
      return new ArrayList<>();
    }
    return result;
  }

  private String getReportKey(ReportLocationDto report) {
    String position = report.getPosition() != null ? (String.valueOf(report.getPosition().getX()) + String.valueOf(report.getPosition().getY())) : "null-null";
    return report.getId() + position;
  }

  private List<ReportShareDto> findShares(List<String> shareIds) {
    return sharingReader.findReportShares(shareIds);
  }

  private ReportShareLocationDto constructReportShareLocation(
      Map<String, ReportLocationDto> reportLocationsMap, ReportShareDto reportShare) {
    ReportLocationDto reportLocationDto = reportLocationsMap.get(getReportKey(reportShare));
    ReportShareLocationDto toAdd = mapToReportShareLocationDto(
        reportLocationDto, reportShare.getId(), reportShare.getReportId());
    return toAdd;
  }

  private String getReportKey(ReportShareDto reportShare) {
    String position = reportShare.getPosition() != null ? (String.valueOf(reportShare.getPosition().getX()) + String.valueOf(reportShare.getPosition().getY())) : "null-null";
    return reportShare.getReportId() + position;
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
        this.deleteReportShares(share.getReportShares());
      }

      if (updatedDashboard.getReports() != null) {
        List<String> newReportShares = createReportShares(updatedDashboard);
        share.setReportShares(newReportShares);
        sharingWriter.updateDashboardShare(share);
      }
    });

  }

  private List<String> createReportShares(DashboardDefinitionDto updatedDashboard) {
    List<ReportShareDto> toPersist = new ArrayList<>();
    for (ReportLocationDto report : updatedDashboard.getReports()) {
      ReportShareDto createSharingDto = constructDashboardReportShareDto(report);
      toPersist.add(createSharingDto);
    }
    return this.createNewReportShares(toPersist);
  }

  private List<String> createNewReportShares(List<ReportShareDto> toPersist) {
    return sharingWriter.saveReportShares(toPersist);
  }

  private void deleteReportShares(List<String> reportShares) {
    sharingWriter.deleteReportShares(reportShares);
  }
}
