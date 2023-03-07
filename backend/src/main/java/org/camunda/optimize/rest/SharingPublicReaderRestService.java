/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest;

import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.GroupDto;
import org.camunda.optimize.dto.optimize.UserDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import org.camunda.optimize.dto.optimize.query.report.AdditionalProcessReportEvaluationFilterDto;
import org.camunda.optimize.dto.optimize.query.ui_configuration.UIConfigurationResponseDto;
import org.camunda.optimize.dto.optimize.query.variable.DecisionVariableNameRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.DecisionVariableNameResponseDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameResponseDto;
import org.camunda.optimize.dto.optimize.rest.FlowNodeIdsToNamesRequestDto;
import org.camunda.optimize.dto.optimize.rest.FlowNodeNamesResponseDto;
import org.camunda.optimize.dto.optimize.rest.pagination.PaginationRequestDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedReportEvaluationResponseDto;
import org.camunda.optimize.rest.providers.CacheRequest;
import org.camunda.optimize.service.SettingsService;
import org.springframework.stereotype.Component;

import javax.validation.Valid;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.function.Supplier;

import static org.camunda.optimize.OptimizeJettyServerCustomizer.EXTERNAL_SUB_PATH;
import static org.camunda.optimize.rest.AssigneeRestService.ASSIGNEE_RESOURCE_PATH;
import static org.camunda.optimize.rest.CandidateGroupRestService.CANDIDATE_GROUP_RESOURCE_PATH;
import static org.camunda.optimize.rest.DecisionVariablesRestService.DECISION_INPUTS_NAMES_PATH;
import static org.camunda.optimize.rest.DecisionVariablesRestService.DECISION_OUTPUTS_NAMES_PATH;
import static org.camunda.optimize.rest.DecisionVariablesRestService.DECISION_VARIABLES_PATH;
import static org.camunda.optimize.rest.FlowNodeRestService.FLOW_NODE_NAMES_SUB_PATH;
import static org.camunda.optimize.rest.LocalizationRestService.LOCALIZATION_PATH;
import static org.camunda.optimize.rest.ProcessVariableRestService.PROCESS_VARIABLES_PATH;
import static org.camunda.optimize.rest.SharingRestService.DASHBOARD_SUB_PATH;
import static org.camunda.optimize.rest.SharingRestService.EVALUATE_SUB_PATH;
import static org.camunda.optimize.rest.SharingRestService.REPORT_SUB_PATH;
import static org.camunda.optimize.rest.SharingRestService.SHARE_PATH;

@AllArgsConstructor
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
  public AuthorizedReportEvaluationResponseDto evaluateReport(@Context ContainerRequestContext requestContext,
                                                              @PathParam("shareId") String reportShareId,
                                                              @BeanParam @Valid final PaginationRequestDto paginationRequestDto) {
    return executeIfSharingEnabled(() -> protectedSharingRestService.evaluateReport(
      requestContext,
      reportShareId,
      paginationRequestDto
    ));
  }

  @POST
  @Path(SHARE_PATH + DASHBOARD_SUB_PATH + "/{shareId}" + REPORT_SUB_PATH + "/{reportId}" + EVALUATE_SUB_PATH)
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public AuthorizedReportEvaluationResponseDto evaluateReport(@Context ContainerRequestContext requestContext,
                                                              @PathParam("shareId") String dashboardShareId,
                                                              @PathParam("reportId") String reportId,
                                                              AdditionalProcessReportEvaluationFilterDto reportEvaluationFilter,
                                                              @BeanParam @Valid final PaginationRequestDto paginationRequestDto) {
    return executeIfSharingEnabled(() -> protectedSharingRestService.evaluateReport(
      requestContext, dashboardShareId, reportId, reportEvaluationFilter, paginationRequestDto
    ));
  }

  @GET
  @Path(SHARE_PATH + DASHBOARD_SUB_PATH + "/{shareId}" + EVALUATE_SUB_PATH)
  @Produces(MediaType.APPLICATION_JSON)
  public DashboardDefinitionRestDto evaluateDashboard(@Context ContainerRequestContext requestContext,
                                                      @PathParam("shareId") String dashboardShareId) {
    return executeIfSharingEnabled(() -> protectedSharingRestService.evaluateDashboard(requestContext, dashboardShareId));
  }

  @POST
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  @Path(PROCESS_VARIABLES_PATH)
  public List<ProcessVariableNameResponseDto> getVariableNames(
    @Context final ContainerRequestContext requestContext,
    @Valid final List<ProcessVariableNameRequestDto> variableRequestDtos) {
    return executeIfSharingEnabled(() -> processVariableRestService.getVariableNames(requestContext, variableRequestDtos));
  }

  @POST
  @Path(DECISION_VARIABLES_PATH + DECISION_INPUTS_NAMES_PATH)
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public List<DecisionVariableNameResponseDto> getInputVariableNames(
    @Valid final List<DecisionVariableNameRequestDto> variableRequestDto) {
    return executeIfSharingEnabled(() -> decisionVariableRestService.getInputVariableNames(variableRequestDto));
  }

  @POST
  @Path(DECISION_VARIABLES_PATH + DECISION_OUTPUTS_NAMES_PATH)
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public List<DecisionVariableNameResponseDto> getOutputVariableNames(
    @Valid final List<DecisionVariableNameRequestDto> variableRequestDto) {
    return executeIfSharingEnabled(() -> decisionVariableRestService.getOutputVariableNames(variableRequestDto));
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
  public List<GroupDto> getCandidateGroupsByIds(@QueryParam("idIn") final String commaSeparatedIdn) {
    return executeIfSharingEnabled(() -> candidateGroupRestService.getCandidateGroupsByIds(commaSeparatedIdn));
  }

  @GET
  @Path(ASSIGNEE_RESOURCE_PATH)
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public List<UserDto> getAssigneesByIds(@QueryParam("idIn") final String commaSeparatedIdn) {
    return executeIfSharingEnabled(() -> assigneeRestService.getAssigneesByIds(commaSeparatedIdn));
  }

  private <C> C executeIfSharingEnabled(Supplier<C> supplier) {
    return settingsService.getSettings().getSharingEnabled()
      .filter(isEnabled -> isEnabled)
      .map(isEnabled -> supplier.get())
      .orElseThrow(() -> new NotAuthorizedException(SHARING_DISABLED_MSG));
  }

}
