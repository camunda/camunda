/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest;

import static io.camunda.optimize.rest.constants.RestConstants.X_OPTIMIZE_CLIENT_LOCALE;
import static io.camunda.optimize.rest.queryparam.QueryParamUtil.normalizeNullStringValue;
import static io.camunda.optimize.rest.util.TimeZoneUtil.extractTimezone;
import static io.camunda.optimize.tomcat.OptimizeResourceConstants.REST_API_PATH;

import io.camunda.optimize.dto.optimize.query.IdResponseDto;
import io.camunda.optimize.dto.optimize.query.report.AdditionalProcessReportEvaluationFilterDto;
import io.camunda.optimize.dto.optimize.query.report.AuthorizedReportEvaluationResult;
import io.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionRequestDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionRequestDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import io.camunda.optimize.dto.optimize.rest.AuthorizedReportDefinitionResponseDto;
import io.camunda.optimize.dto.optimize.rest.ConflictResponseDto;
import io.camunda.optimize.dto.optimize.rest.pagination.PaginationDto;
import io.camunda.optimize.dto.optimize.rest.pagination.PaginationRequestDto;
import io.camunda.optimize.dto.optimize.rest.report.AuthorizedReportEvaluationResponseDto;
import io.camunda.optimize.rest.mapper.ReportRestMapper;
import io.camunda.optimize.service.exceptions.OptimizeValidationException;
import io.camunda.optimize.service.report.ReportEvaluationService;
import io.camunda.optimize.service.report.ReportService;
import io.camunda.optimize.service.security.SessionService;
import io.camunda.optimize.service.security.util.LocalDateUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping(REST_API_PATH + ReportRestService.REPORT_PATH)
public class ReportRestService {
  public static final String REPORT_PATH = "/report";
  private static final Logger LOG = LoggerFactory.getLogger(ReportRestService.class);
  private final ReportService reportService;
  private final ReportEvaluationService reportEvaluationService;
  private final SessionService sessionService;
  private final ReportRestMapper reportRestMapper;

  public ReportRestService(
      final ReportService reportService,
      final ReportEvaluationService reportEvaluationService,
      final SessionService sessionService,
      final ReportRestMapper reportRestMapper) {
    this.reportService = reportService;
    this.reportEvaluationService = reportEvaluationService;
    this.sessionService = sessionService;
    this.reportRestMapper = reportRestMapper;
  }

  @PostMapping("/process/single")
  public IdResponseDto createNewSingleProcessReport(
      @Valid @RequestBody final SingleProcessReportDefinitionRequestDto definition,
      final HttpServletRequest request) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(request);
    if (definition != null
        && definition.getData() != null
        && (definition.getData().isManagementReport()
            || definition.getData().isInstantPreviewReport())) {
      throw new OptimizeValidationException(
          "Management or Instant Preview Reports cannot be created manually");
    }
    return reportService.createNewSingleProcessReport(
        userId,
        Optional.ofNullable(definition).orElseGet(SingleProcessReportDefinitionRequestDto::new));
  }

  @PostMapping("/decision/single/")
  public IdResponseDto createNewSingleDecisionReport(
      @Valid @RequestBody
          final SingleDecisionReportDefinitionRequestDto singleDecisionReportDefinitionDto,
      final HttpServletRequest request) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(request);
    return reportService.createNewSingleDecisionReport(
        userId,
        Optional.ofNullable(singleDecisionReportDefinitionDto)
            .orElseGet(SingleDecisionReportDefinitionRequestDto::new));
  }

  @PostMapping("/process/combined/")
  public IdResponseDto createNewCombinedProcessReport(
      @RequestBody final CombinedReportDefinitionRequestDto combinedReportDefinitionDto,
      final HttpServletRequest request) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(request);
    return reportService.createNewCombinedProcessReport(
        userId,
        Optional.ofNullable(combinedReportDefinitionDto)
            .orElseGet(CombinedReportDefinitionRequestDto::new));
  }

  @PostMapping("/{id}/copy")
  public IdResponseDto copyReport(
      @PathVariable("id") final String id,
      @RequestParam(name = "collectionId", required = false) String collectionId,
      @RequestParam(name = "name", required = false) final String newReportName,
      final HttpServletRequest request) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(request);
    if (collectionId == null) {
      return reportService.copyReport(id, userId, newReportName);
    } else {
      // 'null' or collectionId value provided
      collectionId = normalizeNullStringValue(collectionId);
      return reportService.copyAndMoveReport(id, userId, collectionId, newReportName);
    }
  }

  @GetMapping
  public List<AuthorizedReportDefinitionResponseDto> getAuthorizedPrivateReports(
      final HttpServletRequest request) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(request);
    final List<AuthorizedReportDefinitionResponseDto> reportDefinitions =
        reportService.findAndFilterPrivateReports(userId);
    reportDefinitions.forEach(
        authorizedReportDefinitionDto ->
            reportRestMapper.prepareLocalizedRestResponse(
                authorizedReportDefinitionDto, request.getHeader(X_OPTIMIZE_CLIENT_LOCALE)));
    return reportDefinitions;
  }

  @GetMapping("/{id}")
  public AuthorizedReportDefinitionResponseDto getReport(
      @PathVariable("id") final String reportId, final HttpServletRequest request) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(request);
    final AuthorizedReportDefinitionResponseDto reportDefinition =
        reportService.getReportDefinition(reportId, userId);
    reportRestMapper.prepareLocalizedRestResponse(
        reportDefinition, request.getHeader(X_OPTIMIZE_CLIENT_LOCALE));
    return reportDefinition;
  }

  @PostMapping("/{id}/evaluate")
  public AuthorizedReportEvaluationResponseDto evaluateReportByIdWithFilters(
      @PathVariable("id") final String reportId,
      @Valid final PaginationRequestDto paginationRequestDto,
      @RequestBody final AdditionalProcessReportEvaluationFilterDto reportEvaluationFilter,
      final HttpServletRequest request) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(request);
    final ZoneId timezone = extractTimezone(request);
    final AuthorizedReportEvaluationResult reportEvaluationResult =
        reportEvaluationService.evaluateSavedReportWithAdditionalFilters(
            userId,
            timezone,
            reportId,
            reportEvaluationFilter,
            PaginationDto.fromPaginationRequest(paginationRequestDto));
    return reportRestMapper.mapToLocalizedEvaluationResponseDto(
        reportEvaluationResult, request.getHeader(X_OPTIMIZE_CLIENT_LOCALE));
  }

  @PostMapping("/evaluate")
  public AuthorizedReportEvaluationResponseDto evaluateProvidedReport(
      @Valid @RequestBody final ReportDefinitionDto reportDefinitionDto,
      @Valid final PaginationRequestDto paginationRequestDto,
      final HttpServletRequest request) {
    LOG.error("========STARTING EVALUATE");
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(request);
    if (reportDefinitionDto instanceof SingleProcessReportDefinitionRequestDto
        && ((SingleProcessReportDefinitionRequestDto) reportDefinitionDto)
            .getData()
            .isManagementReport()) {
      throw new OptimizeValidationException("Unsaved Management Reports cannot be evaluated");
    }
    final ZoneId timezone = extractTimezone(request);
    final AuthorizedReportEvaluationResult reportEvaluationResult =
        reportEvaluationService.evaluateUnsavedReport(
            userId,
            timezone,
            reportDefinitionDto,
            PaginationDto.fromPaginationRequest(paginationRequestDto));
    final AuthorizedReportEvaluationResponseDto returnVal = reportRestMapper.mapToLocalizedEvaluationResponseDto(
        reportEvaluationResult, request.getHeader(X_OPTIMIZE_CLIENT_LOCALE));
    LOG.error("========ENDING EVALUATE");
    return returnVal;
  }

  @PutMapping("/process/single/{id}")
  public void updateSingleProcessReport(
      @PathVariable("id") final String reportId,
      @RequestParam(name = "force", required = false) final boolean force,
      @RequestBody @Valid final SingleProcessReportDefinitionRequestDto updatedReport,
      final HttpServletRequest request) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(request);
    final @Valid ProcessReportDataDto reportData = updatedReport.getData();
    if (reportData != null
        && (reportData.isManagementReport() || reportData.isInstantPreviewReport())) {
      throw new OptimizeValidationException(
          "Existing Reports cannot be set as Management/Instant Preview Reports");
    }
    updatedReport.setId(reportId);
    updatedReport.setLastModifier(userId);
    updatedReport.setLastModified(LocalDateUtil.getCurrentDateTime());
    reportService.updateSingleProcessReport(reportId, updatedReport, userId, force);
  }

  @PutMapping("/decision/single/{id}")
  public void updateSingleDecisionReport(
      @PathVariable("id") final String reportId,
      @RequestParam(name = "force", required = false) final boolean force,
      @RequestBody @Valid final SingleDecisionReportDefinitionRequestDto updatedReport,
      final HttpServletRequest request) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(request);
    updatedReport.setId(reportId);
    updatedReport.setLastModifier(userId);
    updatedReport.setLastModified(LocalDateUtil.getCurrentDateTime());
    reportService.updateSingleDecisionReport(reportId, updatedReport, userId, force);
  }

  @PutMapping("/process/combined/{id}")
  public void updateCombinedProcessReport(
      @PathVariable("id") final String reportId,
      @RequestParam(name = "force", required = false) final boolean force,
      @RequestBody final CombinedReportDefinitionRequestDto updatedReport,
      final HttpServletRequest request) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(request);
    updatedReport.setId(reportId);
    updatedReport.setLastModifier(userId);
    updatedReport.setLastModified(LocalDateUtil.getCurrentDateTime());
    reportService.updateCombinedProcessReport(userId, reportId, updatedReport);
  }

  @GetMapping("/{id}/delete-conflicts")
  public ConflictResponseDto getDeleteConflicts(
      @PathVariable("id") final String reportId, final HttpServletRequest request) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(request);
    return reportService.getReportDeleteConflictingItems(userId, reportId);
  }

  @DeleteMapping("/{id}")
  public void deleteReport(
      @PathVariable("id") final String reportId,
      @RequestParam(name = "force", required = false) final boolean force,
      final HttpServletRequest request) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(request);
    reportService.deleteReportAsUser(userId, reportId, force);
  }
}
