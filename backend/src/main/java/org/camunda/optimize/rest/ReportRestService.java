/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest;

import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.query.IdResponseDto;
import org.camunda.optimize.dto.optimize.query.report.AdditionalProcessReportEvaluationFilterDto;
import org.camunda.optimize.dto.optimize.query.report.AuthorizedReportEvaluationResult;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.rest.AuthorizedReportDefinitionResponseDto;
import org.camunda.optimize.dto.optimize.rest.ConflictResponseDto;
import org.camunda.optimize.dto.optimize.rest.pagination.PaginationDto;
import org.camunda.optimize.dto.optimize.rest.pagination.PaginationRequestDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedReportEvaluationResponseDto;
import org.camunda.optimize.rest.mapper.ReportRestMapper;
import org.camunda.optimize.service.exceptions.OptimizeValidationException;
import org.camunda.optimize.service.report.ReportEvaluationService;
import org.camunda.optimize.service.report.ReportService;
import org.camunda.optimize.service.security.SessionService;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.springframework.stereotype.Component;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.camunda.optimize.rest.queryparam.QueryParamUtil.normalizeNullStringValue;
import static org.camunda.optimize.rest.util.TimeZoneUtil.extractTimezone;

@AllArgsConstructor
@Path("/report")
@Component
public class ReportRestService {

  private final ReportService reportService;
  private final ReportEvaluationService reportEvaluationService;
  private final SessionService sessionService;
  private final ReportRestMapper reportRestMapper;

  /**
   * Creates a new single process report.
   *
   * @return the id of the report
   */
  @POST
  @Path("/process/single/")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public IdResponseDto createNewSingleProcessReport(@Context final ContainerRequestContext requestContext,
                                                    @Valid final SingleProcessReportDefinitionRequestDto definition) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    if (definition != null && definition.getData() != null &&
      (definition.getData().isManagementReport() || definition.getData().isInstantPreviewReport())) {
      throw new OptimizeValidationException("Management or Instant Preview Reports cannot be created manually");
    }
    return reportService.createNewSingleProcessReport(
      userId,
      Optional.ofNullable(definition).orElseGet(SingleProcessReportDefinitionRequestDto::new)
    );
  }

  /**
   * Creates a new single decision report.
   *
   * @return the id of the report
   */
  @POST
  @Path("/decision/single/")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public IdResponseDto createNewSingleDecisionReport(@Context final ContainerRequestContext requestContext,
                                                     @Valid final SingleDecisionReportDefinitionRequestDto singleDecisionReportDefinitionDto) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return reportService.createNewSingleDecisionReport(
      userId,
      Optional.ofNullable(singleDecisionReportDefinitionDto).orElseGet(SingleDecisionReportDefinitionRequestDto::new)
    );
  }

  /**
   * Creates a new combined process report.
   *
   * @return the id of the report
   */
  @POST
  @Path("/process/combined/")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public IdResponseDto createNewCombinedProcessReport(@Context final ContainerRequestContext requestContext,
                                                      CombinedReportDefinitionRequestDto combinedReportDefinitionDto) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return reportService.createNewCombinedProcessReport(
      userId,
      Optional.ofNullable(combinedReportDefinitionDto).orElseGet(CombinedReportDefinitionRequestDto::new)
    );
  }

  @POST
  @Path("/{id}/copy")
  @Produces(MediaType.APPLICATION_JSON)
  public IdResponseDto copyReport(@Context UriInfo uriInfo,
                                  @Context ContainerRequestContext requestContext,
                                  @PathParam("id") String id,
                                  @QueryParam("collectionId") String collectionId,
                                  @QueryParam("name") String newReportName) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    if (collectionId == null) {
      return reportService.copyReport(id, userId, newReportName);
    } else {
      // 'null' or collectionId value provided
      collectionId = normalizeNullStringValue(collectionId);
      return reportService.copyAndMoveReport(id, userId, collectionId, newReportName);
    }
  }

  /**
   * Get a list of all private reports for current user
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public List<AuthorizedReportDefinitionResponseDto> getAuthorizedPrivateReports(@Context UriInfo uriInfo,
                                                                                 @Context ContainerRequestContext requestContext) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    List<AuthorizedReportDefinitionResponseDto> reportDefinitions =
      reportService.findAndFilterPrivateReports(userId);
    reportDefinitions
      .forEach(reportRestMapper::prepareRestResponse);
    return reportDefinitions;
  }

  /**
   * Retrieve the report to the specified id.
   */
  @GET
  @Path("/{id}")
  @Produces(MediaType.APPLICATION_JSON)
  public AuthorizedReportDefinitionResponseDto getReport(@Context ContainerRequestContext requestContext,
                                                         @PathParam("id") String reportId) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    AuthorizedReportDefinitionResponseDto reportDefinition = reportService.getReportDefinition(reportId, userId);
    reportRestMapper.prepareRestResponse(reportDefinition);
    return reportDefinition;
  }

  /**
   * Retrieves the report definition to the given report id and then
   * evaluate this report using the supplied filters and return the result.
   */
  @POST
  @Path("/{id}/evaluate")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public AuthorizedReportEvaluationResponseDto evaluateReportByIdWithFilters(@Context ContainerRequestContext requestContext,
                                                                             @PathParam("id") String reportId,
                                                                             @BeanParam @Valid final PaginationRequestDto paginationRequestDto,
                                                                             AdditionalProcessReportEvaluationFilterDto reportEvaluationFilter) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    final ZoneId timezone = extractTimezone(requestContext);
    final AuthorizedReportEvaluationResult reportEvaluationResult =
      reportEvaluationService.evaluateSavedReportWithAdditionalFilters(
        userId,
        timezone,
        reportId,
        reportEvaluationFilter,
        PaginationDto.fromPaginationRequest(paginationRequestDto)
      );
    return reportRestMapper.mapToEvaluationResultDto(reportEvaluationResult);
  }

  /**
   * Evaluates the given report and returns the result.
   *
   * @return A report definition that is also containing the actual result of the report evaluation.
   */
  @POST
  @Path("/evaluate")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public AuthorizedReportEvaluationResponseDto evaluateProvidedReport(@Context ContainerRequestContext requestContext,
                                                                      @Valid @NotNull ReportDefinitionDto reportDefinitionDto,
                                                                      @BeanParam @Valid final PaginationRequestDto paginationRequestDto) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    if (reportDefinitionDto instanceof SingleProcessReportDefinitionRequestDto
      && ((SingleProcessReportDefinitionRequestDto) reportDefinitionDto).getData().isManagementReport()) {
      throw new OptimizeValidationException("Unsaved Management Reports cannot be evaluated");
    }
    final ZoneId timezone = extractTimezone(requestContext);
    final AuthorizedReportEvaluationResult reportEvaluationResult =
      reportEvaluationService.evaluateUnsavedReport(
        userId,
        timezone,
        reportDefinitionDto,
        PaginationDto.fromPaginationRequest(paginationRequestDto)
      );
    return reportRestMapper.mapToEvaluationResultDto(reportEvaluationResult);
  }

  /**
   * Updates the given fields of a single process report to the given id.
   */
  @PUT
  @Path("/process/single/{id}")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public void updateSingleProcessReport(@Context final ContainerRequestContext requestContext,
                                        @PathParam("id") final String reportId,
                                        @QueryParam("force") final boolean force,
                                        @NotNull @Valid final SingleProcessReportDefinitionRequestDto updatedReport) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    final @Valid ProcessReportDataDto reportData = updatedReport.getData();
    if (reportData != null && (reportData.isManagementReport() || reportData.isInstantPreviewReport())) {
      throw new OptimizeValidationException("Existing Reports cannot be set as Management/Instant Preview Reports");
    }
    updatedReport.setId(reportId);
    updatedReport.setLastModifier(userId);
    updatedReport.setLastModified(LocalDateUtil.getCurrentDateTime());
    reportService.updateSingleProcessReport(reportId, updatedReport, userId, force);
  }

  /**
   * Updates the given fields of a single decision report to the given id.
   */
  @PUT
  @Path("/decision/single/{id}")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public void updateSingleDecisionReport(@Context final ContainerRequestContext requestContext,
                                         @PathParam("id") final String reportId,
                                         @QueryParam("force") final boolean force,
                                         @NotNull @Valid final SingleDecisionReportDefinitionRequestDto updatedReport) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    updatedReport.setId(reportId);
    updatedReport.setLastModifier(userId);
    updatedReport.setLastModified(LocalDateUtil.getCurrentDateTime());
    reportService.updateSingleDecisionReport(reportId, updatedReport, userId, force);
  }

  /**
   * Updates the given fields of a combined process report to the given id.
   */
  @PUT
  @Path("/process/combined/{id}")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public void updateCombinedProcessReport(@Context ContainerRequestContext requestContext,
                                          @PathParam("id") String reportId,
                                          @QueryParam("force") boolean force,
                                          @NotNull CombinedReportDefinitionRequestDto updatedReport) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    updatedReport.setId(reportId);
    updatedReport.setLastModifier(userId);
    updatedReport.setLastModified(LocalDateUtil.getCurrentDateTime());
    reportService.updateCombinedProcessReport(userId, reportId, updatedReport);
  }

  /**
   * Retrieve the conflicting items that would occur on performing a delete.
   */
  @GET
  @Path("/{id}/delete-conflicts")
  @Produces(MediaType.APPLICATION_JSON)
  public ConflictResponseDto getDeleteConflicts(@Context ContainerRequestContext requestContext,
                                                @PathParam("id") String reportId) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return reportService.getReportDeleteConflictingItems(userId, reportId);
  }

  /**
   * Delete the report to the specified id.
   */
  @DELETE
  @Path("/{id}")
  @Produces(MediaType.APPLICATION_JSON)
  public void deleteReport(@Context ContainerRequestContext requestContext,
                           @PathParam("id") String reportId,
                           @QueryParam("force") boolean force) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    reportService.deleteReportAsUser(userId, reportId, force);
  }

}
