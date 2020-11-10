/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.IdResponseDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import org.camunda.optimize.dto.optimize.query.dashboard.ReportLocationDto;
import org.camunda.optimize.dto.optimize.query.report.AdditionalProcessReportEvaluationFilterDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.sharing.DashboardShareRestDto;
import org.camunda.optimize.dto.optimize.query.sharing.ReportShareRestDto;
import org.camunda.optimize.dto.optimize.query.sharing.ShareSearchRequestDto;
import org.camunda.optimize.dto.optimize.query.sharing.ShareSearchResultResponseDto;
import org.camunda.optimize.dto.optimize.rest.AuthorizedReportEvaluationResult;
import org.camunda.optimize.dto.optimize.rest.ConflictedItemDto;
import org.camunda.optimize.dto.optimize.rest.pagination.PaginationDto;
import org.camunda.optimize.service.dashboard.DashboardService;
import org.camunda.optimize.service.es.reader.SharingReader;
import org.camunda.optimize.service.es.report.PlainReportEvaluationHandler;
import org.camunda.optimize.service.es.report.ReportEvaluationInfo;
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
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

  public IdResponseDto createNewReportShareIfAbsent(ReportShareRestDto createSharingDto, String userId) {
    validateAndCheckAuthorization(createSharingDto, userId);
    Optional<ReportShareRestDto> existing =
      sharingReader.findShareForReport(createSharingDto.getReportId());

    return existing
      .map(share -> new IdResponseDto(share.getId()))
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
  public void handleReportUpdated(final String reportId, final ReportDefinitionDto updateDefinition) {
    //NOOP
  }

  @Override
  public void handleDashboardDeleted(final DashboardDefinitionRestDto definition) {
    deleteShareForDashboard(definition.getId());
  }

  @Override
  public void handleDashboardUpdated(final DashboardDefinitionRestDto updatedDashboard) {
    Optional<DashboardShareRestDto> dashboardShare = findShareForDashboard(updatedDashboard.getId());

    dashboardShare.ifPresent(share -> {
      share.setReportShares(updatedDashboard.getReports());
      sharingWriter.updateDashboardShare(share);
    });
  }

  private IdResponseDto createNewReportShare(ReportShareRestDto createSharingDto) {
    String result = sharingWriter.saveReportShare(createSharingDto).getId();
    return new IdResponseDto(result);
  }

  public IdResponseDto crateNewDashboardShare(DashboardShareRestDto createSharingDto, String userId) {
    validateAndCheckAuthorization(createSharingDto.getDashboardId(), userId);

    String result;
    Optional<DashboardShareRestDto> existing =
      sharingReader.findShareForDashboard(createSharingDto.getDashboardId());

    result = existing
      .map(DashboardShareRestDto::getId)
      .orElseGet(() -> {
        this.addReportInformation(createSharingDto, userId);
        return sharingWriter.saveDashboardShare(createSharingDto).getId();
      });

    return new IdResponseDto(result);
  }

  private void addReportInformation(DashboardShareRestDto createSharingDto, String userId) {
    DashboardDefinitionRestDto dashboardDefinition =
      dashboardService.getDashboardDefinition(createSharingDto.getDashboardId(), userId).getDefinitionDto();
    createSharingDto.setReportShares(dashboardDefinition.getReports());
  }

  public void validateAndCheckAuthorization(String dashboardId, String userId) {
    ValidationHelper.ensureNotEmpty("dashboardId", dashboardId);
    try {
      DashboardDefinitionRestDto dashboardDefinition =
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

  private void validateAndCheckAuthorization(ReportShareRestDto reportShare, String userId) {
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

  public AuthorizedReportEvaluationResult evaluateReportShare(final String shareId, final ZoneId timezone,
                                                              final PaginationDto paginationDto) {
    Optional<ReportShareRestDto> optionalReportShare = sharingReader.getReportShare(shareId);
    return optionalReportShare
      .map(share -> evaluateReport(ReportEvaluationInfo.builder(share.getReportId())
                                     .timezone(timezone)
                                     .pagination(paginationDto)
                                     .isSharedReport(true)
                                     .build()))
      .orElseThrow(
        () -> new OptimizeRuntimeException("share [" + shareId + "] does not exist or is of unsupported type"));
  }

  public AuthorizedReportEvaluationResult evaluateReportForSharedDashboard(final String dashboardShareId,
                                                                           final String reportId,
                                                                           final ZoneId timezone,
                                                                           final AdditionalProcessReportEvaluationFilterDto reportEvaluationFilter,
                                                                           final PaginationDto paginationDto) {
    final DashboardDefinitionRestDto dashboard = sharingReader.findDashboardShare(dashboardShareId)
      .map(share -> dashboardService.getDashboardDefinitionAsService(share.getDashboardId()))
      .orElseThrow(() -> new OptimizeRuntimeException(String.format(
        "Could not find dashboard share for id [%s]",
        dashboardShareId
      )));
    boolean dashboardContainsReport = dashboard.getReports()
      .stream()
      .anyMatch(r -> Objects.equals(r.getId(), reportId));
    if (!dashboardContainsReport) {
      String reason = "Cannot evaluate report [" + reportId +
        "] for shared dashboard id [" + dashboardShareId + "]. Given report is not contained in dashboard.";
      log.error(reason);
      throw new OptimizeRuntimeException(reason);
    }
    return evaluateReport(
      ReportEvaluationInfo.builder(reportId)
        .timezone(timezone)
        .additionalFilters(reportEvaluationFilter)
        .pagination(paginationDto)
        .isSharedReport(true)
        .build());
  }

  public AuthorizedReportEvaluationResult evaluateReport(ReportEvaluationInfo evaluationInfo) {
    try {
      return reportEvaluationHandler.evaluateReport(evaluationInfo);
    } catch (ReportEvaluationException e) {
      throw e;
    } catch (Exception e) {
      throw new OptimizeRuntimeException("Cannot evaluate shared report [" + evaluationInfo.getReportId() + "]. The " +
                                           "report probably does not exist.");
    }
  }

  public Optional<DashboardDefinitionRestDto> evaluateDashboard(String shareId) {
    Optional<DashboardShareRestDto> sharedDashboard = sharingReader.findDashboardShare(shareId);

    return sharedDashboard
      .map(this::constructDashboard)
      .orElseThrow(() -> new OptimizeRuntimeException("dashboard share [" + shareId + "] does not exist or is of " +
                                                        "unsupported type"));
  }

  private Optional<DashboardDefinitionRestDto> constructDashboard(DashboardShareRestDto share) {
    DashboardDefinitionRestDto result = dashboardService.getDashboardDefinitionAsService(share.getDashboardId());
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

  public Optional<ReportShareRestDto> findShareForReport(String resourceId) {
    return sharingReader.findShareForReport(resourceId);
  }

  public Optional<DashboardShareRestDto> findShareForDashboard(String resourceId) {
    return sharingReader.findShareForDashboard(resourceId);
  }

  public ShareSearchResultResponseDto checkShareStatus(ShareSearchRequestDto searchRequest) {
    ShareSearchResultResponseDto result = new ShareSearchResultResponseDto();
    if (searchRequest != null && searchRequest.getReports() != null && !searchRequest.getReports().isEmpty()) {
      Map<String, ReportShareRestDto> shareForReports = sharingReader.findShareForReports(searchRequest.getReports());
      for (String reportId : searchRequest.getReports()) {
        result.getReports().put(reportId, shareForReports.containsKey(reportId));
      }
    }

    if (searchRequest != null && searchRequest.getDashboards() != null && !searchRequest.getDashboards().isEmpty()) {
      Map<String, DashboardShareRestDto> shareForReports =
        sharingReader.findShareForDashboards(searchRequest.getDashboards());
      for (String dashboardId : searchRequest.getDashboards()) {
        result.getDashboards().put(dashboardId, shareForReports.containsKey(dashboardId));
      }
    }

    return result;
  }

}
