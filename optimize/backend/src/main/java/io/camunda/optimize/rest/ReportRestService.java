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
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Path("/report")
@Component
public class ReportRestService {

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

  @POST
  @Path("/process/single/")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public IdResponseDto createNewSingleProcessReport(
      @Context final ContainerRequestContext requestContext,
      @Valid final SingleProcessReportDefinitionRequestDto definition) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
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

  @POST
  @Path("/decision/single/")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public IdResponseDto createNewSingleDecisionReport(
      @Context final ContainerRequestContext requestContext,
      @Valid final SingleDecisionReportDefinitionRequestDto singleDecisionReportDefinitionDto) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return reportService.createNewSingleDecisionReport(
        userId,
        Optional.ofNullable(singleDecisionReportDefinitionDto)
            .orElseGet(SingleDecisionReportDefinitionRequestDto::new));
  }

  @POST
  @Path("/process/combined/")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public IdResponseDto createNewCombinedProcessReport(
      @Context final ContainerRequestContext requestContext,
      final CombinedReportDefinitionRequestDto combinedReportDefinitionDto) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return reportService.createNewCombinedProcessReport(
        userId,
        Optional.ofNullable(combinedReportDefinitionDto)
            .orElseGet(CombinedReportDefinitionRequestDto::new));
  }

  @POST
  @Path("/{id}/copy")
  @Produces(MediaType.APPLICATION_JSON)
  public IdResponseDto copyReport(
      @Context final ContainerRequestContext requestContext,
      @PathParam("id") final String id,
      @QueryParam("collectionId") String collectionId,
      @QueryParam("name") final String newReportName) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    if (collectionId == null) {
      return reportService.copyReport(id, userId, newReportName);
    } else {
      // 'null' or collectionId value provided
      collectionId = normalizeNullStringValue(collectionId);
      return reportService.copyAndMoveReport(id, userId, collectionId, newReportName);
    }
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public List<AuthorizedReportDefinitionResponseDto> getAuthorizedPrivateReports(
      @Context final ContainerRequestContext requestContext) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    final List<AuthorizedReportDefinitionResponseDto> reportDefinitions =
        reportService.findAndFilterPrivateReports(userId);
    reportDefinitions.forEach(
        authorizedReportDefinitionDto ->
            reportRestMapper.prepareLocalizedRestResponse(
                authorizedReportDefinitionDto,
                requestContext.getHeaderString(X_OPTIMIZE_CLIENT_LOCALE)));
    return reportDefinitions;
  }

  @GET
  @Path("/{id}")
  @Produces(MediaType.APPLICATION_JSON)
  public AuthorizedReportDefinitionResponseDto getReport(
      @Context final ContainerRequestContext requestContext,
      @PathParam("id") final String reportId) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    final AuthorizedReportDefinitionResponseDto reportDefinition =
        reportService.getReportDefinition(reportId, userId);
    reportRestMapper.prepareLocalizedRestResponse(
        reportDefinition, requestContext.getHeaderString(X_OPTIMIZE_CLIENT_LOCALE));
    return reportDefinition;
  }

  @POST
  @Path("/{id}/evaluate")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public AuthorizedReportEvaluationResponseDto evaluateReportByIdWithFilters(
      @Context final ContainerRequestContext requestContext,
      @PathParam("id") final String reportId,
      @BeanParam @Valid final PaginationRequestDto paginationRequestDto,
      final AdditionalProcessReportEvaluationFilterDto reportEvaluationFilter) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    final ZoneId timezone = extractTimezone(requestContext);
    final AuthorizedReportEvaluationResult reportEvaluationResult =
        reportEvaluationService.evaluateSavedReportWithAdditionalFilters(
            userId,
            timezone,
            reportId,
            reportEvaluationFilter,
            PaginationDto.fromPaginationRequest(paginationRequestDto));
    return reportRestMapper.mapToLocalizedEvaluationResponseDto(
        reportEvaluationResult, requestContext.getHeaderString(X_OPTIMIZE_CLIENT_LOCALE));
  }

  @POST
  @Path("/evaluate")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public AuthorizedReportEvaluationResponseDto evaluateProvidedReport(
      @Context final ContainerRequestContext requestContext,
      @Valid @NotNull final ReportDefinitionDto reportDefinitionDto,
      @BeanParam @Valid final PaginationRequestDto paginationRequestDto) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    if (reportDefinitionDto instanceof SingleProcessReportDefinitionRequestDto
        && ((SingleProcessReportDefinitionRequestDto) reportDefinitionDto)
            .getData()
            .isManagementReport()) {
      throw new OptimizeValidationException("Unsaved Management Reports cannot be evaluated");
    }
    final ZoneId timezone = extractTimezone(requestContext);
    final AuthorizedReportEvaluationResult reportEvaluationResult =
        reportEvaluationService.evaluateUnsavedReport(
            userId,
            timezone,
            reportDefinitionDto,
            PaginationDto.fromPaginationRequest(paginationRequestDto));
    return reportRestMapper.mapToLocalizedEvaluationResponseDto(
        reportEvaluationResult, requestContext.getHeaderString(X_OPTIMIZE_CLIENT_LOCALE));
  }

  @PUT
  @Path("/process/single/{id}")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public void updateSingleProcessReport(
      @Context final ContainerRequestContext requestContext,
      @PathParam("id") final String reportId,
      @QueryParam("force") final boolean force,
      @NotNull @Valid final SingleProcessReportDefinitionRequestDto updatedReport) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
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

  @PUT
  @Path("/decision/single/{id}")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public void updateSingleDecisionReport(
      @Context final ContainerRequestContext requestContext,
      @PathParam("id") final String reportId,
      @QueryParam("force") final boolean force,
      @NotNull @Valid final SingleDecisionReportDefinitionRequestDto updatedReport) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    updatedReport.setId(reportId);
    updatedReport.setLastModifier(userId);
    updatedReport.setLastModified(LocalDateUtil.getCurrentDateTime());
    reportService.updateSingleDecisionReport(reportId, updatedReport, userId, force);
  }

  @PUT
  @Path("/process/combined/{id}")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public void updateCombinedProcessReport(
      @Context final ContainerRequestContext requestContext,
      @PathParam("id") final String reportId,
      @QueryParam("force") final boolean force,
      @NotNull final CombinedReportDefinitionRequestDto updatedReport) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    updatedReport.setId(reportId);
    updatedReport.setLastModifier(userId);
    updatedReport.setLastModified(LocalDateUtil.getCurrentDateTime());
    reportService.updateCombinedProcessReport(userId, reportId, updatedReport);
  }

  @GET
  @Path("/{id}/delete-conflicts")
  @Produces(MediaType.APPLICATION_JSON)
  public ConflictResponseDto getDeleteConflicts(
      @Context final ContainerRequestContext requestContext,
      @PathParam("id") final String reportId) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return reportService.getReportDeleteConflictingItems(userId, reportId);
  }

  @DELETE
  @Path("/{id}")
  @Produces(MediaType.APPLICATION_JSON)
  public void deleteReport(
      @Context final ContainerRequestContext requestContext,
      @PathParam("id") final String reportId,
      @QueryParam("force") final boolean force) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    reportService.deleteReportAsUser(userId, reportId, force);
  }
}
