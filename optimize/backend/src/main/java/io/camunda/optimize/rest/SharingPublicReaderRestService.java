/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest;

import static io.camunda.optimize.OptimizeTomcatConfig.EXTERNAL_SUB_PATH;
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
import static io.camunda.optimize.tomcat.OptimizeResourceConstants.REST_API_PATH;

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
import io.camunda.optimize.rest.exceptions.NotAuthorizedException;
import io.camunda.optimize.rest.providers.CacheRequest;
import io.camunda.optimize.service.SettingsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping(REST_API_PATH + EXTERNAL_SUB_PATH)
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

  @GetMapping(UIConfigurationRestService.UI_CONFIGURATION_PATH)
  public UIConfigurationResponseDto getUIConfiguration() {
    // UI Configuration is always open, regardless of whether sharing is enabled or not
    return uiConfigurationService.getUIConfiguration();
  }

  @GetMapping(LOCALIZATION_PATH)
  @CacheRequest
  public byte[] getLocalizationFile(
      @RequestParam(name = "localeCode", required = false) final String localeCode) {
    // Localization is always open, regardless of whether sharing is enabled or not
    return localizationRestService.getLocalizationFile(localeCode);
  }

  @PostMapping(SHARE_PATH + REPORT_SUB_PATH + "/{shareId}" + EVALUATE_SUB_PATH)
  public AuthorizedReportEvaluationResponseDto evaluateReport(
      @PathVariable("shareId") final String reportShareId,
      @Valid final PaginationRequestDto paginationRequestDto,
      final HttpServletRequest request) {
    return executeIfSharingEnabled(
        () ->
            protectedSharingRestService.evaluateReport(
                reportShareId, paginationRequestDto, request));
  }

  @PostMapping(
      SHARE_PATH
          + DASHBOARD_SUB_PATH
          + "/{shareId}"
          + REPORT_SUB_PATH
          + "/{reportId}"
          + EVALUATE_SUB_PATH)
  public AuthorizedReportEvaluationResponseDto evaluateReport(
      @PathVariable("shareId") final String dashboardShareId,
      @PathVariable("reportId") final String reportId,
      @RequestBody final AdditionalProcessReportEvaluationFilterDto reportEvaluationFilter,
      @Valid final PaginationRequestDto paginationRequestDto,
      final HttpServletRequest request) {
    return executeIfSharingEnabled(
        () ->
            protectedSharingRestService.evaluateReport(
                dashboardShareId, reportId, reportEvaluationFilter, paginationRequestDto, request));
  }

  @GetMapping(SHARE_PATH + DASHBOARD_SUB_PATH + "/{shareId}" + EVALUATE_SUB_PATH)
  public DashboardDefinitionRestDto evaluateDashboard(
      @PathVariable("shareId") final String dashboardShareId, final HttpServletRequest request) {
    return executeIfSharingEnabled(
        () -> protectedSharingRestService.evaluateDashboard(dashboardShareId, request));
  }

  @PostMapping(PROCESS_VARIABLES_PATH)
  public List<ProcessVariableNameResponseDto> getVariableNames(
      @Valid @RequestBody final ProcessVariableNameRequestDto variableRequestDtos,
      final HttpServletRequest request) {
    return executeIfSharingEnabled(
        () -> processVariableRestService.getVariableNames(variableRequestDtos, request));
  }

  @PostMapping(DECISION_VARIABLES_PATH + DECISION_INPUTS_NAMES_PATH)
  public List<DecisionVariableNameResponseDto> getInputVariableNames(
      @Valid @RequestBody final List<DecisionVariableNameRequestDto> variableRequestDto) {
    return executeIfSharingEnabled(
        () -> decisionVariableRestService.getInputVariableNames(variableRequestDto));
  }

  @PostMapping(DECISION_VARIABLES_PATH + DECISION_OUTPUTS_NAMES_PATH)
  public List<DecisionVariableNameResponseDto> getOutputVariableNames(
      @Valid @RequestBody final List<DecisionVariableNameRequestDto> variableRequestDto) {
    return executeIfSharingEnabled(
        () -> decisionVariableRestService.getOutputVariableNames(variableRequestDto));
  }

  @PostMapping(FlowNodeRestService.FLOW_NODE_PATH + FLOW_NODE_NAMES_SUB_PATH)
  @CacheRequest
  public FlowNodeNamesResponseDto getFlowNodeNames(
      @RequestBody final FlowNodeIdsToNamesRequestDto request) {
    return executeIfSharingEnabled(() -> flowNodeRestService.getFlowNodeNames(request));
  }

  @GetMapping(CANDIDATE_GROUP_RESOURCE_PATH)
  public List<GroupDto> getCandidateGroupsByIds(
      @RequestParam(name = "idIn", required = false) final String commaSeparatedIdn) {
    return executeIfSharingEnabled(
        () -> candidateGroupRestService.getCandidateGroupsByIds(commaSeparatedIdn));
  }

  @GetMapping(ASSIGNEE_RESOURCE_PATH)
  public List<UserDto> getAssigneesByIds(
      @RequestParam(name = "idIn", required = false) final String commaSeparatedIdn) {
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
