/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.rest.ConflictResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.EvaluationResultDto;
import org.camunda.optimize.rest.providers.Secured;
import org.camunda.optimize.service.es.report.result.ReportEvaluationResult;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.report.ReportService;
import org.camunda.optimize.service.security.SessionService;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.springframework.beans.factory.annotation.Autowired;
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


@Secured
@Path("/report")
@Component
public class ReportRestService {

  private final ReportService reportService;
  private final SessionService sessionService;

  @Autowired
  public ReportRestService(ReportService reportService,
                           SessionService sessionService) {
    this.reportService = reportService;
    this.sessionService = sessionService;
  }


  /**
   * Creates an empty new single process report.
   *
   * @return the id of the report
   */
  @POST
  @Path("/process/single/")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public IdDto createNewSingleProcessReport(@Context ContainerRequestContext requestContext) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return reportService.createNewSingleProcessReport(userId);
  }

  /**
   * Creates an empty new single decision report.
   *
   * @return the id of the report
   */
  @POST
  @Path("/decision/single/")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public IdDto createNewSingleDecisionReport(@Context ContainerRequestContext requestContext) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return reportService.createNewSingleDecisionReport(userId);
  }

  /**
   * Creates an empty new combined process report.
   *
   * @return the id of the report
   */
  @POST
  @Path("/process/combined/")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public IdDto createNewCombinedProcessReport(@Context ContainerRequestContext requestContext) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return reportService.createNewCombinedProcessReport(userId);
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
                                        @NotNull SingleProcessReportDefinitionDto updatedReport) throws
                                                                                                 OptimizeException {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    updatedReport.setId(reportId);
    updatedReport.setLastModifier(userId);
    updatedReport.setLastModified(LocalDateUtil.getCurrentDateTime());
    reportService.updateSingleProcessReportWithAuthorizationCheck(reportId, updatedReport, userId, force);
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
                                         @NotNull SingleDecisionReportDefinitionDto updatedReport) throws
                                                                                                   OptimizeException {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    updatedReport.setId(reportId);
    updatedReport.setLastModifier(userId);
    updatedReport.setLastModified(LocalDateUtil.getCurrentDateTime());
    reportService.updateSingleDecisionReportWithAuthorizationCheck(reportId, updatedReport, userId, force);
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
    reportService.updateCombinedProcessReportWithAuthorizationCheck(reportId, updatedReport);
  }

  /**
   * Get a list of all available reports.
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public List<ReportDefinitionDto> getStoredReports(@Context UriInfo uriInfo,
                                                    @Context ContainerRequestContext requestContext) {
    MultivaluedMap<String, String> queryParameters = uriInfo.getQueryParameters();

    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return reportService.findAndFilterReports(userId, queryParameters);
  }

  /**
   * Retrieve the report to the specified id.
   */
  @GET
  @Path("/{id}")
  @Produces(MediaType.APPLICATION_JSON)
  public ReportDefinitionDto getReport(@Context ContainerRequestContext requestContext,
                                       @PathParam("id") String reportId) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return reportService.getReportWithAuthorizationCheck(reportId, userId);
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
    return reportService.getReportDeleteConflictingItemsWithAuthorizationCheck(userId, reportId);
  }

  /**
   * Delete the report to the specified id.
   */
  @DELETE
  @Path("/{id}")
  @Produces(MediaType.APPLICATION_JSON)
  public void deleteReport(@Context ContainerRequestContext requestContext,
                           @PathParam("id") String reportId,
                           @QueryParam("force") boolean force) throws OptimizeException {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    reportService.deleteReportWithAuthorizationCheck(userId, reportId, force);
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
  public EvaluationResultDto<?, ?> evaluateReportById(@Context ContainerRequestContext requestContext,
                                                      @PathParam("id") String reportId) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    final ReportEvaluationResult<?, ?> reportEvaluationResult = reportService.evaluateSavedReport(userId, reportId);
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
  public EvaluationResultDto<?, ?> evaluateProvidedReport(@Context ContainerRequestContext requestContext,
                                                          @NotNull ReportDefinitionDto reportDefinitionDto) {

    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    final ReportEvaluationResult<?, ?> reportEvaluationResult = reportService.evaluateReport(
      userId,
      reportDefinitionDto
    );
    return ReportEvaluationResultMapper.mapToEvaluationResultDto(reportEvaluationResult);
  }


}
