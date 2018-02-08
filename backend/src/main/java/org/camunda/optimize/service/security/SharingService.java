package org.camunda.optimize.service.security;

import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.ReportLocationDto;
import org.camunda.optimize.dto.optimize.query.report.result.ReportResultDto;
import org.camunda.optimize.dto.optimize.query.sharing.EvaluatedReportShareDto;
import org.camunda.optimize.dto.optimize.query.sharing.SharedResourceType;
import org.camunda.optimize.dto.optimize.query.sharing.SharingDto;
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
   * @param createSharingDto
   * @return
   */
  public IdDto crateNewShare(SharingDto createSharingDto) {
    String result;
    Optional<SharingDto> existing =
        sharingReader.findShareForResource(createSharingDto.getResourceId(), createSharingDto.getType());

    result = existing
      .map(SharingDto::getId)
      .orElseGet(() -> {
        if (SharedResourceType.DASHBOARD.equals(createSharingDto.getType())) {
          this.shareReportsOfDashboard(createSharingDto);
        }
        return sharingWriter.saveShare(createSharingDto).getId();
      });

    IdDto id = new IdDto();
    id.setId(result);
    return id;
  }

  private void shareReportsOfDashboard(SharingDto createSharingDto) {
    try {
      DashboardDefinitionDto dashboardDefinition =
          dashboardService.getDashboardDefinition(createSharingDto.getResourceId());

      if (dashboardDefinition.getReports() != null) {
        for (ReportLocationDto report : dashboardDefinition.getReports()) {
          this.crateNewShare(constructShareDto(report));
        }
      }

    } catch (IOException e) {
      logger.error("can't find dashboard", e);
    } catch (OptimizeException e) {
      logger.error("can't find dashboard", e);
    }
  }

  private SharingDto constructShareDto(ReportLocationDto report) {
    SharingDto result = new SharingDto();
    result.setType(SharedResourceType.DASHBOARD_REPORT);
    result.setResourceId(report.getId());
    return result;
  }

  public void validate(SharingDto createSharingDto) {
    if (SharedResourceType.REPORT.equals(createSharingDto.getType())) {
      try {
        reportService.getReport(createSharingDto.getResourceId());
      } catch (IOException e) {
        logger.error("can't fetch report [{}]", createSharingDto.getResourceId(), e);
      } catch (OptimizeException e) {
        logger.error("can't fetch report [{}]", createSharingDto.getResourceId(), e);
        throw new OptimizeValidationException(e.getMessage());
      }
    } else if (SharedResourceType.DASHBOARD.equals(createSharingDto.getType())) {
      try {
        dashboardService.getDashboardDefinition(createSharingDto.getResourceId());
      } catch (IOException e) {
        logger.error("can't fetch dashboard [{}]", createSharingDto.getResourceId(), e);
      } catch (OptimizeException e) {
        logger.error("can't fetch dashboard [{}]", createSharingDto.getResourceId(), e);
        throw new OptimizeValidationException(e.getMessage());
      }
    } else {
      throw new OptimizeValidationException("Specified share type is not allowed");
    }
  }

  public void deleteShare(String shareId) {
    sharingWriter.deleteShare(shareId);
  }

  public Optional<EvaluatedReportShareDto> evaluate(String shareId) {
    Optional<EvaluatedReportShareDto> result = Optional.empty();
    Optional<SharingDto> base = sharingReader.findShare(shareId);

    if (base.isPresent()) {
      EvaluatedReportShareDto wrapped = new EvaluatedReportShareDto(base.get());
      try {
        ReportResultDto reportResultDto = reportService.evaluateSavedReport(wrapped.getResourceId());
        wrapped.setReport(reportResultDto);
        result = Optional.of(wrapped);
      } catch (IOException e) {
        logger.error("can't evaluate shared report []", wrapped.getResourceId());
      } catch (OptimizeException e) {
        logger.error("can't evaluate shared report []", wrapped.getResourceId());
      }
    } else {
      throw new OptimizeRuntimeException("share [" + shareId + "] does not exist");
    }

    return result;
  }

  public SharingDto findShare(String shareId) {
    return sharingReader.findShare(shareId).orElse(null);
  }

  public void deleteShareForReport(String reportId) {
    Optional<SharingDto> share = findShareForResource(reportId, SharedResourceType.DASHBOARD_REPORT);
    share.ifPresent(dto -> this.deleteShare(dto.getId()));

    share = findShareForResource(reportId, SharedResourceType.REPORT);
    share.ifPresent(dto -> this.deleteShare(dto.getId()));
  }

  public SharingDto findShareForReport(String resourceId) {
    return findShareForResource(resourceId, SharedResourceType.REPORT)
        .orElse(null);
  }

  private Optional<SharingDto> findShareForResource(String resourceId, SharedResourceType type) {
    return sharingReader.findShareForResource(resourceId, type);
  }

  public SharingDto findShareForDashboard(String resourceId) {
    return findShareForResource(resourceId, SharedResourceType.DASHBOARD)
        .orElse(null);
  }
}
