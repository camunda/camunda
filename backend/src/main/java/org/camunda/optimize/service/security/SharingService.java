/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.ReportLocationDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.sharing.DashboardShareDto;
import org.camunda.optimize.dto.optimize.query.sharing.ReportShareDto;
import org.camunda.optimize.dto.optimize.query.sharing.ShareSearchDto;
import org.camunda.optimize.dto.optimize.query.sharing.ShareSearchResultDto;
import org.camunda.optimize.dto.optimize.rest.AuthorizedReportEvaluationResult;
import org.camunda.optimize.dto.optimize.rest.ConflictedItemDto;
import org.camunda.optimize.service.dashboard.DashboardService;
import org.camunda.optimize.service.es.reader.SharingReader;
import org.camunda.optimize.service.es.report.PlainReportEvaluationHandler;
import org.camunda.optimize.service.es.writer.SharingWriter;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.exceptions.evaluation.ReportEvaluationException;
import org.camunda.optimize.service.relations.DashboardReferencingService;
import org.camunda.optimize.service.relations.ReportReferencingService;
import org.camunda.optimize.service.report.ReportService;
import org.camunda.optimize.service.util.ValidationHelper;
import org.springframework.stereotype.Component;

import javax.ws.rs.ForbiddenException;
import javax.ws.rs.NotFoundException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
@Slf4j
public class SharingService implements ReportReferencingService, DashboardReferencingService {

  private final SharingWriter sharingWriter;
  private final SharingReader sharingReader;
  private final PlainReportEvaluationHandler reportEvaluationHandler;
  private final DashboardService dashboardService;
  private final ReportService reportService;

  public IdDto createNewReportShareIfAbsent(ReportShareDto createSharingDto, String userId) {
    validateAndCheckAuthorization(createSharingDto, userId);
    Optional<ReportShareDto> existing =
      sharingReader.findShareForReport(createSharingDto.getReportId());

    return existing
      .map(share -> new IdDto(share.getId()))
      .orElseGet(() -> createNewReportShare(createSharingDto));
  }

  @Override
  public Set<ConflictedItemDto> getConflictedItemsForReportDelete(final ReportDefinitionDto reportDefinition) {
    //NOOP
    return Collections.emptySet();
  }

  @Override
  public void handleReportDeleted(final ReportDefinitionDto reportDefinition) {
    deleteShareForReport(reportDefinition.getId());
  }

  @Override
  public Set<ConflictedItemDto> getConflictedItemsForReportUpdate(final ReportDefinitionDto currentDefinition,
                                                                  final ReportDefinitionDto updateDefinition) {
    //NOOP
    return Collections.emptySet();
  }

  @Override
  public void handleReportUpdated(final String id, final ReportDefinitionDto updateDefinition) {
    //NOOP
  }

  @Override
  public void handleDashboardDeleted(final DashboardDefinitionDto definition) {
    deleteShareForDashboard(definition.getId());
  }

  @Override
  public void handleDashboardUpdated(final DashboardDefinitionDto updatedDashboard) {
    Optional<DashboardShareDto> dashboardShare = findShareForDashboard(updatedDashboard.getId());

    dashboardShare.ifPresent(share -> {
      share.setReportShares(updatedDashboard.getReports());
      sharingWriter.updateDashboardShare(share);
    });
  }

  private IdDto createNewReportShare(ReportShareDto createSharingDto) {
    String result = sharingWriter.saveReportShare(createSharingDto).getId();
    return new IdDto(result);
  }

  public IdDto crateNewDashboardShare(DashboardShareDto createSharingDto, String userId) {
    validateAndCheckAuthorization(createSharingDto.getDashboardId(), userId);

    String result;
    Optional<DashboardShareDto> existing =
      sharingReader.findShareForDashboard(createSharingDto.getDashboardId());

    result = existing
      .map(DashboardShareDto::getId)
      .orElseGet(() -> {
        this.addReportInformation(createSharingDto, userId);
        return sharingWriter.saveDashboardShare(createSharingDto).getId();
      });

    return new IdDto(result);
  }

  private void addReportInformation(DashboardShareDto createSharingDto, String userId) {
    DashboardDefinitionDto dashboardDefinition =
      dashboardService.getDashboardDefinition(createSharingDto.getDashboardId(), userId).getDefinitionDto();
    createSharingDto.setReportShares(dashboardDefinition.getReports());
  }

  public void validateAndCheckAuthorization(String dashboardId, String userId) {
    ValidationHelper.ensureNotEmpty("dashboardId", dashboardId);
    try {
      DashboardDefinitionDto dashboardDefinition =
        dashboardService.getDashboardDefinition(dashboardId, userId).getDefinitionDto();

      List<String> authorizedReportIds = reportService
        .findAndFilterReports(userId)
        .stream()
        .map(authorizedReportDefinitionDto -> authorizedReportDefinitionDto.getDefinitionDto().getId())
        .collect(Collectors.toList());

      for (ReportLocationDto reportLocationDto : dashboardDefinition.getReports()) {
        if (!authorizedReportIds.contains(reportLocationDto.getId()) && !isAnExternalResourceReport(reportLocationDto)) {
          String errorMessage = "User [" + userId + "] is not authorized to share dashboard [" +
            dashboardDefinition.getName() + "] because he is not authorized to see contained report [" +
            reportLocationDto.getId() + "]";
          throw new ForbiddenException(errorMessage);
        }
      }

    } catch (OptimizeRuntimeException | NotFoundException e) {
      String errorMessage = "Could not retrieve dashboard [" +
        dashboardId + "]. Probably it does not exist.";
      throw new OptimizeRuntimeException(errorMessage, e);
    }
  }

  private boolean isAnExternalResourceReport(ReportLocationDto reportLocationDto) {
    return reportLocationDto.getId().isEmpty();
  }

  private void validateAndCheckAuthorization(ReportShareDto reportShare, String userId) {
    ValidationHelper.ensureNotEmpty("reportId", reportShare.getReportId());
    try {
      reportService.getReportDefinition(reportShare.getReportId(), userId);
    } catch (OptimizeRuntimeException | NotFoundException e) {
      String errorMessage = "Could not retrieve report [" +
        reportShare.getReportId() + "]. Probably it does not exist.";
      throw new OptimizeRuntimeException(errorMessage, e);
    }
  }

  public void deleteReportShare(String shareId) {
    sharingWriter.deleteReportShare(shareId);
  }

  public void deleteDashboardShare(String shareId) {
    sharingWriter.deleteDashboardShare(shareId);
  }

  public AuthorizedReportEvaluationResult evaluateReportShare(String shareId) {
    Optional<ReportShareDto> optionalReportShare = sharingReader.getReportShare(shareId);

    return optionalReportShare
      .map(this::constructReportShare)
      .orElseThrow(() -> new OptimizeRuntimeException("share [" + shareId + "] does not exist or is of unsupported " +
                                                        "type"));
  }

  public AuthorizedReportEvaluationResult evaluateReportForSharedDashboard(String dashboardShareId, String reportId) {
    Optional<DashboardShareDto> sharedDashboard = sharingReader.findDashboardShare(dashboardShareId);
    AuthorizedReportEvaluationResult result;

    DashboardShareDto share = sharedDashboard.orElseThrow(
      () -> new OptimizeRuntimeException(String.format("Could not find dashboard share for id [%s]", dashboardShareId))
    );
    boolean hasGivenReport = share.getReportShares().stream().anyMatch(r -> r.getId().equals(reportId));
    if (hasGivenReport) {
      result = evaluateReport(reportId);
    } else {
      String reason = "Cannot evaluate report [" + reportId +
        "] for shared dashboard id [" + dashboardShareId + "]. Given report is not contained in dashboard.";
      log.error(reason);
      throw new OptimizeRuntimeException(reason);
    }

    return result;
  }

  public AuthorizedReportEvaluationResult evaluateReport(String reportId) {
    try {
      return reportEvaluationHandler.evaluateSavedReport(reportId);
    } catch (ReportEvaluationException e) {
      throw e;
    } catch (Exception e) {
      String reason = "Cannot evaluate shared report [" + reportId + "]. The report probably does not exist.";
      throw new OptimizeRuntimeException(reason);
    }
  }

  private AuthorizedReportEvaluationResult constructReportShare(ReportShareDto share) {
    return evaluateReport(share.getReportId());
  }

  public Optional<DashboardDefinitionDto> evaluateDashboard(String shareId) {
    Optional<DashboardShareDto> sharedDashboard = sharingReader.findDashboardShare(shareId);

    return sharedDashboard
      .map(this::constructDashboard)
      .orElseThrow(() -> new OptimizeRuntimeException("dashboard share [" + shareId + "] does not exist or is of " +
                                                        "unsupported type"));
  }

  private Optional<DashboardDefinitionDto> constructDashboard(DashboardShareDto share) {
    DashboardDefinitionDto result = dashboardService.getDashboardDefinitionAsService(share.getDashboardId());
    return Optional.of(result);
  }

  private void deleteShareForReport(String reportId) {
    findShareForReport(reportId)
      .ifPresent(dto -> this.deleteReportShare(dto.getId()));
  }

  private void deleteShareForDashboard(String dashboardId) {
    findShareForDashboard(dashboardId)
      .ifPresent(dto -> this.deleteDashboardShare(dto.getId()));
  }

  public Optional<ReportShareDto> findShareForReport(String resourceId) {
    return sharingReader.findShareForReport(resourceId);
  }

  public Optional<DashboardShareDto> findShareForDashboard(String resourceId) {
    return sharingReader.findShareForDashboard(resourceId);
  }

  public ShareSearchResultDto checkShareStatus(ShareSearchDto searchRequest) {
    ShareSearchResultDto result = new ShareSearchResultDto();
    if (searchRequest != null && searchRequest.getReports() != null && !searchRequest.getReports().isEmpty()) {
      Map<String, ReportShareDto> shareForReports = sharingReader.findShareForReports(searchRequest.getReports());

      for (String reportId : searchRequest.getReports()) {
        if (shareForReports.containsKey(reportId)) {
          result.getReports().put(reportId, true);
        } else {
          result.getReports().put(reportId, false);
        }

      }
    }

    if (searchRequest != null && searchRequest.getDashboards() != null && !searchRequest.getDashboards().isEmpty()) {
      Map<String, DashboardShareDto> shareForReports =
        sharingReader.findShareForDashboards(searchRequest.getDashboards());

      for (String dashboardId : searchRequest.getDashboards()) {
        if (shareForReports.containsKey(dashboardId)) {
          result.getDashboards().put(dashboardId, true);
        } else {
          result.getDashboards().put(dashboardId, false);
        }
      }
    }

    return result;
  }

}
