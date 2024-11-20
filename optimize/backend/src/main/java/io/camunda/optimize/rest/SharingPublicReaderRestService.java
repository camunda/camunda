/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest;

import static io.camunda.optimize.TomcatConfig.EXTERNAL_SUB_PATH;
import static io.camunda.optimize.rest.AssigneeRestService.ASSIGNEE_RESOURCE_PATH;
import static io.camunda.optimize.rest.CandidateGroupRestService.CANDIDATE_GROUP_RESOURCE_PATH;
import static io.camunda.optimize.rest.DecisionVariablesRestService.DECISION_INPUTS_NAMES_PATH;
import static io.camunda.optimize.rest.DecisionVariablesRestService.DECISION_OUTPUTS_NAMES_PATH;
import static io.camunda.optimize.rest.DecisionVariablesRestService.DECISION_VARIABLES_PATH;
import static io.camunda.optimize.rest.FlowNodeRestService.FLOW_NODE_NAMES_SUB_PATH;
import static io.camunda.optimize.rest.LocalizationRestService.LOCALIZATION_PATH;
import static io.camunda.optimize.rest.ProcessVariableRestService.PROCESS_VARIABLES_PATH;
import static io.camunda.optimize.rest.SharingRestService.DASHBOARD_SUB_PATH;
import static io.camunda.optimize.rest.SharingRestService.EVALUATE_SUB_PATH;
import static io.camunda.optimize.rest.SharingRestService.REPORT_SUB_PATH;
import static io.camunda.optimize.rest.SharingRestService.SHARE_PATH;

import io.camunda.optimize.dto.optimize.GroupDto;
import io.camunda.optimize.dto.optimize.UserDto;
import io.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import io.camunda.optimize.dto.optimize.query.report.AdditionalProcessReportEvaluationFilterDto;
import io.camunda.optimize.dto.optimize.query.ui_configuration.UIConfigurationResponseDto;
import io.camunda.optimize.dto.optimize.query.variable.DecisionVariableNameRequestDto;
import io.camunda.optimize.dto.optimize.query.variable.DecisionVariableNameResponseDto;
import io.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameRequestDto;
import io.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameResponseDto;
import io.camunda.optimize.dto.optimize.rest.FlowNodeIdsToNamesRequestDto;
import io.camunda.optimize.dto.optimize.rest.FlowNodeNamesResponseDto;
import io.camunda.optimize.dto.optimize.rest.pagination.PaginationRequestDto;
import io.camunda.optimize.dto.optimize.rest.report.AuthorizedReportEvaluationResponseDto;
import io.camunda.optimize.rest.providers.CacheRequest;
import io.camunda.optimize.service.SettingsService;
import jakarta.validation.Valid;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Path(EXTERNAL_SUB_PATH)
@Component
public class SharingPublicReaderRestService {

  private static final String SHARING_DISABLED_MSG = "Sharing has been disabled by configuration";

  private final SharingRestService protectedSharingRestService;
  private final LocalizationRestService localizationRestService;
  private final ProcessVariableRestService processVariableRestService;
  private final DecisionVariablesRestService decisionVariableRestService;
  private final FlowNodeRestService flowNodeRestService;
  private final CandidateGroupRestService candidateGroupRestService;
  private final UIConfigurationRestService uiConfigurationService;
  private final AssigneeRestService assigneeRestService;
  private final SettingsService settingsService;

  public SharingPublicReaderRestService(
      final SharingRestService protectedSharingRestService,
      final LocalizationRestService localizationRestService,
      final ProcessVariableRestService processVariableRestService,
      final DecisionVariablesRestService decisionVariableRestService,
      final FlowNodeRestService flowNodeRestService,
      final CandidateGroupRestService candidateGroupRestService,
      final UIConfigurationRestService uiConfigurationService,
      final AssigneeRestService assigneeRestService,
      final SettingsService settingsService) {
    this.protectedSharingRestService = protectedSharingRestService;
    this.localizationRestService = localizationRestService;
    this.processVariableRestService = processVariableRestService;
    this.decisionVariableRestService = decisionVariableRestService;
    this.flowNodeRestService = flowNodeRestService;
    this.candidateGroupRestService = candidateGroupRestService;
    this.uiConfigurationService = uiConfigurationService;
    this.assigneeRestService = assigneeRestService;
    this.settingsService = settingsService;
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path(UIConfigurationRestService.UI_CONFIGURATION_PATH)
  public UIConfigurationResponseDto getUIConfiguration() {
    // UI Configuration is always open, regardless of whether sharing is enabled or not
    return uiConfigurationService.getUIConfiguration();
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @CacheRequest
  @Path(LOCALIZATION_PATH)
  public byte[] getLocalizationFile(@QueryParam("localeCode") final String localeCode) {
    // Localization is always open, regardless of whether sharing is enabled or not
    return localizationRestService.getLocalizationFile(localeCode);
  }

  @POST
  @Path(SHARE_PATH + REPORT_SUB_PATH + "/{shareId}" + EVALUATE_SUB_PATH)
  @Produces(MediaType.APPLICATION_JSON)
  public AuthorizedReportEvaluationResponseDto evaluateReport(
      @Context final ContainerRequestContext requestContext,
      @PathParam("shareId") final String reportShareId,
      @BeanParam @Valid final PaginationRequestDto paginationRequestDto) {
    return executeIfSharingEnabled(
        () ->
            protectedSharingRestService.evaluateReport(
                requestContext, reportShareId, paginationRequestDto));
  }

  @POST
  @Path(
      SHARE_PATH
          + DASHBOARD_SUB_PATH
          + "/{shareId}"
          + REPORT_SUB_PATH
          + "/{reportId}"
          + EVALUATE_SUB_PATH)
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public AuthorizedReportEvaluationResponseDto evaluateReport(
      @Context final ContainerRequestContext requestContext,
      @PathParam("shareId") final String dashboardShareId,
      @PathParam("reportId") final String reportId,
      final AdditionalProcessReportEvaluationFilterDto reportEvaluationFilter,
      @BeanParam @Valid final PaginationRequestDto paginationRequestDto) {
    return executeIfSharingEnabled(
        () ->
            protectedSharingRestService.evaluateReport(
                requestContext,
                dashboardShareId,
                reportId,
                reportEvaluationFilter,
                paginationRequestDto));
  }

  @GET
  @Path(SHARE_PATH + DASHBOARD_SUB_PATH + "/{shareId}" + EVALUATE_SUB_PATH)
  @Produces(MediaType.APPLICATION_JSON)
  public DashboardDefinitionRestDto evaluateDashboard(
      @Context final ContainerRequestContext requestContext,
      @PathParam("shareId") final String dashboardShareId) {
    return executeIfSharingEnabled(
        () -> protectedSharingRestService.evaluateDashboard(requestContext, dashboardShareId));
  }

  @POST
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  @Path(PROCESS_VARIABLES_PATH)
  public List<ProcessVariableNameResponseDto> getVariableNames(
      @Context final ContainerRequestContext requestContext,
      @Valid final ProcessVariableNameRequestDto variableRequestDtos) {
    return executeIfSharingEnabled(
        () -> processVariableRestService.getVariableNames(requestContext, variableRequestDtos));
  }

  @POST
  @Path(DECISION_VARIABLES_PATH + DECISION_INPUTS_NAMES_PATH)
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public List<DecisionVariableNameResponseDto> getInputVariableNames(
      @Valid final List<DecisionVariableNameRequestDto> variableRequestDto) {
    return executeIfSharingEnabled(
        () -> decisionVariableRestService.getInputVariableNames(variableRequestDto));
  }

  @POST
  @Path(DECISION_VARIABLES_PATH + DECISION_OUTPUTS_NAMES_PATH)
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public List<DecisionVariableNameResponseDto> getOutputVariableNames(
      @Valid final List<DecisionVariableNameRequestDto> variableRequestDto) {
    return executeIfSharingEnabled(
        () -> decisionVariableRestService.getOutputVariableNames(variableRequestDto));
  }

  @POST
  @Path(FlowNodeRestService.FLOW_NODE_PATH + FLOW_NODE_NAMES_SUB_PATH)
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  @CacheRequest
  public FlowNodeNamesResponseDto getFlowNodeNames(final FlowNodeIdsToNamesRequestDto request) {
    return executeIfSharingEnabled(() -> flowNodeRestService.getFlowNodeNames(request));
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  @Path(CANDIDATE_GROUP_RESOURCE_PATH)
  public List<GroupDto> getCandidateGroupsByIds(
      @QueryParam("idIn") final String commaSeparatedIdn) {
    return executeIfSharingEnabled(
        () -> candidateGroupRestService.getCandidateGroupsByIds(commaSeparatedIdn));
  }

  @GET
  @Path(ASSIGNEE_RESOURCE_PATH)
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public List<UserDto> getAssigneesByIds(@QueryParam("idIn") final String commaSeparatedIdn) {
    return executeIfSharingEnabled(() -> assigneeRestService.getAssigneesByIds(commaSeparatedIdn));
  }

  private <C> C executeIfSharingEnabled(final Supplier<C> supplier) {
    return settingsService
        .getSettings()
        .getSharingEnabled()
        .filter(isEnabled -> isEnabled)
        .map(isEnabled -> supplier.get())
        .orElseThrow(() -> new NotAuthorizedException(SHARING_DISABLED_MSG));
  }
}
