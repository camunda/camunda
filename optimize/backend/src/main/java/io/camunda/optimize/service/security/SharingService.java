/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.security;

import io.camunda.optimize.dto.optimize.query.IdResponseDto;
import io.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import io.camunda.optimize.dto.optimize.query.report.AdditionalProcessReportEvaluationFilterDto;
import io.camunda.optimize.dto.optimize.query.report.AuthorizedReportEvaluationResult;
import io.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import io.camunda.optimize.dto.optimize.query.sharing.DashboardShareRestDto;
import io.camunda.optimize.dto.optimize.query.sharing.ReportShareRestDto;
import io.camunda.optimize.dto.optimize.query.sharing.ShareSearchRequestDto;
import io.camunda.optimize.dto.optimize.query.sharing.ShareSearchResultResponseDto;
import io.camunda.optimize.dto.optimize.rest.AuthorizedReportDefinitionResponseDto;
import io.camunda.optimize.dto.optimize.rest.ConflictedItemDto;
import io.camunda.optimize.dto.optimize.rest.pagination.PaginationDto;
import io.camunda.optimize.rest.exceptions.ForbiddenException;
import io.camunda.optimize.rest.exceptions.NotFoundException;
import io.camunda.optimize.service.dashboard.DashboardService;
import io.camunda.optimize.service.db.reader.SharingReader;
import io.camunda.optimize.service.db.report.PlainReportEvaluationHandler;
import io.camunda.optimize.service.db.report.ReportEvaluationInfo;
import io.camunda.optimize.service.db.writer.SharingWriter;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.exceptions.OptimizeValidationException;
import io.camunda.optimize.service.exceptions.evaluation.ReportEvaluationException;
import io.camunda.optimize.service.relations.DashboardReferencingService;
import io.camunda.optimize.service.relations.ReportReferencingService;
import io.camunda.optimize.service.report.ReportService;
import io.camunda.optimize.service.util.ValidationHelper;
import java.time.ZoneId;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

@Component
public class SharingService implements ReportReferencingService, DashboardReferencingService {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(SharingService.class);
  private final SharingWriter sharingWriter;
  private final SharingReader sharingReader;
  private final PlainReportEvaluationHandler reportEvaluationHandler;
  private final DashboardService dashboardService;
  private final ReportService reportService;

  public SharingService(
      final SharingWriter sharingWriter,
      final SharingReader sharingReader,
      final PlainReportEvaluationHandler reportEvaluationHandler,
      final DashboardService dashboardService,
      final ReportService reportService) {
    this.sharingWriter = sharingWriter;
    this.sharingReader = sharingReader;
    this.reportEvaluationHandler = reportEvaluationHandler;
    this.dashboardService = dashboardService;
    this.reportService = reportService;
  }

  public IdResponseDto createNewReportShareIfAbsent(
      final ReportShareRestDto createSharingDto, final String userId) {
    validateAndCheckAuthorization(createSharingDto, userId);
    final Optional<ReportShareRestDto> existing =
        sharingReader.findShareForReport(createSharingDto.getReportId());

    return existing
        .map(share -> new IdResponseDto(share.getId()))
        .orElseGet(() -> createNewReportShare(createSharingDto));
  }

  @Override
  public Set<ConflictedItemDto> getConflictedItemsForReportDelete(
      final ReportDefinitionDto reportDefinition) {
    // NOOP
    return Collections.emptySet();
  }

  @Override
  public void handleReportDeleted(final ReportDefinitionDto reportDefinition) {
    deleteShareForReport(reportDefinition.getId());
  }

  @Override
  public Set<ConflictedItemDto> getConflictedItemsForReportUpdate(
      final ReportDefinitionDto currentDefinition, final ReportDefinitionDto updateDefinition) {
    // NOOP
    return Collections.emptySet();
  }

  @Override
  public void handleReportUpdated(
      final String reportId, final ReportDefinitionDto updateDefinition) {
    // NOOP
  }

  @Override
  public void handleDashboardDeleted(final DashboardDefinitionRestDto definition) {
    deleteShareForDashboard(definition.getId());
  }

  @Override
  public void handleDashboardUpdated(final DashboardDefinitionRestDto updatedDashboard) {
    final Optional<DashboardShareRestDto> dashboardShare =
        findShareForDashboard(updatedDashboard.getId());

    dashboardShare.ifPresent(
        share -> {
          share.setTileShares(updatedDashboard.getTiles());
          sharingWriter.updateDashboardShare(share);
        });
  }

  private IdResponseDto createNewReportShare(final ReportShareRestDto createSharingDto) {
    final String result = sharingWriter.saveReportShare(createSharingDto).getId();
    return new IdResponseDto(result);
  }

  public IdResponseDto createNewDashboardShare(
      final DashboardShareRestDto createSharingDto, final String userId) {
    validateAndCheckAuthorization(createSharingDto.getDashboardId(), userId);

    final String result;
    final Optional<DashboardShareRestDto> existing =
        sharingReader.findShareForDashboard(createSharingDto.getDashboardId());

    result =
        existing
            .map(DashboardShareRestDto::getId)
            .orElseGet(
                () -> {
                  addReportInformation(createSharingDto, userId);
                  return sharingWriter.saveDashboardShare(createSharingDto).getId();
                });

    return new IdResponseDto(result);
  }

  private void addReportInformation(
      final DashboardShareRestDto createSharingDto, final String userId) {
    final DashboardDefinitionRestDto dashboardDefinition =
        dashboardService
            .getDashboardDefinition(createSharingDto.getDashboardId(), userId)
            .getDefinitionDto();
    createSharingDto.setTileShares(dashboardDefinition.getTiles());
  }

  public void validateAndCheckAuthorization(final String dashboardId, final String userId) {
    ValidationHelper.ensureNotEmpty("dashboardId", dashboardId);
    try {
      final DashboardDefinitionRestDto dashboardDefinition =
          dashboardService.getDashboardDefinition(dashboardId, userId).getDefinitionDto();
      if (dashboardDefinition.isManagementDashboard()
          || dashboardDefinition.isInstantPreviewDashboard()) {
        throw new OptimizeValidationException(
            "Management Dashboards or Instant preview dashboards cannot be shared");
      }

      final Set<String> authorizedReportIdsOnDashboard =
          reportService.filterAuthorizedReportIds(userId, dashboardDefinition.getTileIds());
      final Set<String> unauthorizedReportIds = new HashSet<>(dashboardDefinition.getTileIds());
      unauthorizedReportIds.removeAll(authorizedReportIdsOnDashboard);

      if (!unauthorizedReportIds.isEmpty()) {
        final String errorMessage =
            "User ["
                + userId
                + "] is not authorized to share dashboard ["
                + dashboardDefinition.getName()
                + "] because they are not authorized to see contained report(s) ["
                + unauthorizedReportIds
                + "]";
        throw new ForbiddenException(errorMessage);
      }
    } catch (final OptimizeValidationException exception) {
      throw exception;
    } catch (final OptimizeRuntimeException | NotFoundException e) {
      final String errorMessage =
          "Could not retrieve dashboard [" + dashboardId + "]. It probably does not exist.";
      throw new OptimizeRuntimeException(errorMessage, e);
    }
  }

  private void validateAndCheckAuthorization(
      final ReportShareRestDto reportShare, final String userId) {
    ValidationHelper.ensureNotEmpty(ReportShareRestDto.Fields.reportId, reportShare.getReportId());
    try {
      final AuthorizedReportDefinitionResponseDto reportDefinition =
          reportService.getReportDefinition(reportShare.getReportId(), userId);
      if (reportDefinition.getDefinitionDto() instanceof SingleProcessReportDefinitionRequestDto) {
        final ProcessReportDataDto reportData =
            ((SingleProcessReportDefinitionRequestDto) reportDefinition.getDefinitionDto())
                .getData();
        if (reportData.isManagementReport() || reportData.isInstantPreviewReport()) {
          throw new OptimizeValidationException(
              "Management Reports and Instant preview dashboard Reports cannot be shared");
        }
      }
    } catch (final OptimizeValidationException exception) {
      throw exception;
    } catch (final OptimizeRuntimeException | NotFoundException e) {
      final String errorMessage =
          "Could not retrieve report ["
              + reportShare.getReportId()
              + "]. It probably does not "
              + "exist.";
      throw new OptimizeRuntimeException(errorMessage, e);
    }
  }

  public void deleteReportShare(final String shareId) {
    sharingWriter.deleteReportShare(shareId);
  }

  public void deleteDashboardShare(final String shareId) {
    sharingWriter.deleteDashboardShare(shareId);
  }

  public AuthorizedReportEvaluationResult evaluateReportShare(
      final String shareId, final ZoneId timezone, final PaginationDto paginationDto) {
    final Optional<ReportShareRestDto> optionalReportShare = sharingReader.getReportShare(shareId);
    return optionalReportShare
        .map(
            share ->
                evaluateReport(
                    ReportEvaluationInfo.builder(share.getReportId())
                        .timezone(timezone)
                        .pagination(paginationDto)
                        .isSharedReport(true)
                        .build()))
        .orElseThrow(
            () ->
                new OptimizeRuntimeException(
                    "share [" + shareId + "] does not exist or is of unsupported type"));
  }

  public AuthorizedReportEvaluationResult evaluateReportForSharedDashboard(
      final String dashboardShareId,
      final String reportId,
      final ZoneId timezone,
      final AdditionalProcessReportEvaluationFilterDto reportEvaluationFilter,
      final PaginationDto paginationDto) {
    final DashboardDefinitionRestDto dashboard =
        sharingReader
            .findDashboardShare(dashboardShareId)
            .map(share -> dashboardService.getDashboardDefinitionAsService(share.getDashboardId()))
            .orElseThrow(
                () ->
                    new OptimizeRuntimeException(
                        String.format(
                            "Could not find dashboard share for id [%s]", dashboardShareId)));
    final boolean dashboardContainsReport =
        dashboard.getTiles().stream().anyMatch(r -> Objects.equals(r.getId(), reportId));
    if (!dashboardContainsReport) {
      final String reason =
          "Cannot evaluate report ["
              + reportId
              + "] for shared dashboard id ["
              + dashboardShareId
              + "]. Given report is not contained in dashboard.";
      LOG.error(reason);
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

  public AuthorizedReportEvaluationResult evaluateReport(
      final ReportEvaluationInfo evaluationInfo) {
    try {
      return reportEvaluationHandler.evaluateReport(evaluationInfo);
    } catch (final ReportEvaluationException e) {
      throw e;
    } catch (final Exception e) {
      throw new OptimizeRuntimeException(
          "Cannot evaluate shared report [" + evaluationInfo.getReportId() + "].", e);
    }
  }

  public Optional<DashboardDefinitionRestDto> evaluateDashboard(final String shareId) {
    final Optional<DashboardShareRestDto> sharedDashboard =
        sharingReader.findDashboardShare(shareId);

    return sharedDashboard
        .map(this::constructDashboard)
        .orElseThrow(
            () ->
                new OptimizeRuntimeException(
                    "dashboard share ["
                        + shareId
                        + "] does not exist or is of "
                        + "unsupported type"));
  }

  private Optional<DashboardDefinitionRestDto> constructDashboard(
      final DashboardShareRestDto share) {
    final DashboardDefinitionRestDto result =
        dashboardService.getDashboardDefinitionAsService(share.getDashboardId());
    return Optional.of(result);
  }

  private void deleteShareForReport(final String reportId) {
    findShareForReport(reportId).ifPresent(dto -> deleteReportShare(dto.getId()));
  }

  private void deleteShareForDashboard(final String dashboardId) {
    findShareForDashboard(dashboardId).ifPresent(dto -> deleteDashboardShare(dto.getId()));
  }

  public Optional<ReportShareRestDto> findShareForReport(final String resourceId) {
    return sharingReader.findShareForReport(resourceId);
  }

  public Optional<DashboardShareRestDto> findShareForDashboard(final String resourceId) {
    return sharingReader.findShareForDashboard(resourceId);
  }

  public ShareSearchResultResponseDto checkShareStatus(final ShareSearchRequestDto searchRequest) {
    final ShareSearchResultResponseDto result = new ShareSearchResultResponseDto();
    if (searchRequest != null
        && searchRequest.getReports() != null
        && !searchRequest.getReports().isEmpty()) {
      final Map<String, ReportShareRestDto> shareForReports =
          sharingReader.findShareForReports(searchRequest.getReports());
      for (final String reportId : searchRequest.getReports()) {
        result.getReports().put(reportId, shareForReports.containsKey(reportId));
      }
    }

    if (searchRequest != null
        && searchRequest.getDashboards() != null
        && !searchRequest.getDashboards().isEmpty()) {
      final Map<String, DashboardShareRestDto> shareForReports =
          sharingReader.findShareForDashboards(searchRequest.getDashboards());
      for (final String dashboardId : searchRequest.getDashboards()) {
        result.getDashboards().put(dashboardId, shareForReports.containsKey(dashboardId));
      }
    }

    return result;
  }
}
