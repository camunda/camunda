/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.rest.AuthorizedReportDefinitionDto;
import org.camunda.optimize.dto.optimize.rest.AuthorizedReportEvaluationResult;
import org.camunda.optimize.dto.optimize.rest.ConflictResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedEvaluationResultDto;
import org.camunda.optimize.rest.mapper.ReportEvaluationResultMapper;
import org.camunda.optimize.rest.providers.Secured;
import org.camunda.optimize.service.report.ReportService;
import org.camunda.optimize.service.security.SessionService;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.springframework.stereotype.Component;

import javax.validation.constraints.NotNull;
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
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriInfo;
import java.util.List;
import java.util.Optional;

import static org.camunda.optimize.rest.queryparam.QueryParamUtil.normalizeNullStringValue;

@AllArgsConstructor
@Secured
@Path("/report")
@Component
public class ReportRestService {

  private final ReportService reportService;
  private final SessionService sessionService;

  /**
   * Creates a new single process report.
   *
   * @return the id of the report
   */
  @POST
  @Path("/process/single/")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public IdDto createNewSingleProcessReport(@Context final ContainerRequestContext requestContext,
                                            SingleProcessReportDefinitionDto singleProcessReportDefinitionDto) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return reportService.createNewSingleProcessReport(
      userId,
      Optional.ofNullable(singleProcessReportDefinitionDto).orElseGet(SingleProcessReportDefinitionDto::new));
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
  public IdDto createNewSingleDecisionReport(@Context final ContainerRequestContext requestContext,
                                             SingleDecisionReportDefinitionDto singleDecisionReportDefinitionDto) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return reportService.createNewSingleDecisionReport(
      userId,
      Optional.ofNullable(singleDecisionReportDefinitionDto).orElseGet(SingleDecisionReportDefinitionDto::new));
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
  public IdDto createNewCombinedProcessReport(@Context final ContainerRequestContext requestContext,
                                              CombinedReportDefinitionDto combinedReportDefinitionDto) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return reportService.createNewCombinedProcessReport(
      userId,
      Optional.ofNullable(combinedReportDefinitionDto).orElseGet(CombinedReportDefinitionDto::new));
  }

  @POST
  @Path("/{id}/copy")
  @Produces(MediaType.APPLICATION_JSON)
  public IdDto copyReport(@Context UriInfo uriInfo,
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
  public List<AuthorizedReportDefinitionDto> getAuthorizedPrivateReports(@Context UriInfo uriInfo,
                                                                         @Context ContainerRequestContext requestContext) {
    MultivaluedMap<String, String> queryParameters = uriInfo.getQueryParameters();

    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return reportService.findAndFilterPrivateReports(userId, queryParameters);
  }

  /**
   * Retrieve the report to the specified id.
   */
  @GET
  @Path("/{id}")
  @Produces(MediaType.APPLICATION_JSON)
  public AuthorizedReportDefinitionDto getReport(@Context ContainerRequestContext requestContext,
                                                 @PathParam("id") String reportId) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return reportService.getReportDefinition(reportId, userId);
  }

  /**
   * Retrieves the report definition to the given report id and then
   * evaluate this report and return the result.
   *
   * @param reportId the id of the report
   * @return A report definition that is also containing the actual result of the report evaluation.
   */
  @GET
  @Path("/{id}/evaluate")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public AuthorizedEvaluationResultDto evaluateReportById(@Context ContainerRequestContext requestContext,
                                                          @PathParam("id") String reportId) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    final AuthorizedReportEvaluationResult reportEvaluationResult = reportService.evaluateSavedReport(userId, reportId);
    return ReportEvaluationResultMapper.mapToEvaluationResultDto(reportEvaluationResult);
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
  public AuthorizedEvaluationResultDto evaluateProvidedReport(@Context ContainerRequestContext requestContext,
                                                              @NotNull ReportDefinitionDto reportDefinitionDto) {

    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    final AuthorizedReportEvaluationResult reportEvaluationResult = reportService.evaluateReport(
      userId,
      reportDefinitionDto
    );
    return ReportEvaluationResultMapper.mapToEvaluationResultDto(reportEvaluationResult);
  }

  /**
   * Updates the given fields of a single process report to the given id.
   */
  @PUT
  @Path("/process/single/{id}")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public void updateSingleProcessReport(@Context ContainerRequestContext requestContext,
                                        @PathParam("id") String reportId,
                                        @QueryParam("force") boolean force,
                                        @NotNull SingleProcessReportDefinitionDto updatedReport) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
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
  public void updateSingleDecisionReport(@Context ContainerRequestContext requestContext,
                                         @PathParam("id") String reportId,
                                         @QueryParam("force") boolean force,
                                         @NotNull SingleDecisionReportDefinitionDto updatedReport) {
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
                                          @NotNull CombinedReportDefinitionDto updatedReport) {
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
    reportService.deleteReport(userId, reportId, force);
  }


}
