/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest;

import static io.camunda.optimize.tomcat.OptimizeResourceConstants.REST_API_PATH;

import io.camunda.optimize.dto.optimize.query.agentic.AgentQueryParams;
import io.camunda.optimize.dto.optimize.query.definition.DefinitionKeyResponseDto;
import io.camunda.optimize.dto.optimize.rest.agentic.ChartsResponse;
import io.camunda.optimize.dto.optimize.rest.agentic.ProcessBreakdownResponse;
import io.camunda.optimize.dto.optimize.rest.agentic.SummaryResponse;
import io.camunda.optimize.dto.optimize.rest.agentic.TrendsResponse;
import io.camunda.optimize.rest.exceptions.BadRequestException;
import io.camunda.optimize.service.agentic.AgenticControlPlaneService;
import io.camunda.optimize.service.security.SessionService;
import io.camunda.optimize.service.tenant.TenantService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(REST_API_PATH + "/agentic-control-plane")
@NullMarked
public class AgenticControlPlaneRestService {

  private final AgenticControlPlaneService service;
  private final SessionService sessionService;
  private final TenantService tenantService;

  public AgenticControlPlaneRestService(
      final AgenticControlPlaneService service,
      final SessionService sessionService,
      final TenantService tenantService) {
    this.service = service;
    this.sessionService = sessionService;
    this.tenantService = tenantService;
  }

  @GetMapping("/summary")
  public SummaryResponse getSummary(
      @RequestParam(required = false) @Nullable final String processDefinitionKey,
      @RequestParam final String startDateFrom,
      @RequestParam final String startDateTo,
      final HttpServletRequest request) {
    return service.getSummary(
        buildParams(processDefinitionKey, startDateFrom, startDateTo, request));
  }

  @GetMapping("/process-breakdown")
  public ProcessBreakdownResponse getProcessBreakdown(
      @RequestParam(required = false) @Nullable final String processDefinitionKey,
      @RequestParam final String startDateFrom,
      @RequestParam final String startDateTo,
      final HttpServletRequest request) {
    if (processDefinitionKey != null) {
      throw new BadRequestException(
          "/process-breakdown is L0 only — processDefinitionKey must not be provided");
    }
    return service.getProcessBreakdown(buildParams(null, startDateFrom, startDateTo, request));
  }

  @GetMapping("/trends")
  public TrendsResponse getTrends(
      @RequestParam(required = false) @Nullable final String processDefinitionKey,
      @RequestParam final String startDateFrom,
      @RequestParam final String startDateTo,
      final HttpServletRequest request) {
    return service.getTrends(
        buildParams(processDefinitionKey, startDateFrom, startDateTo, request));
  }

  @GetMapping("/charts")
  public ChartsResponse getCharts(
      @RequestParam(required = false) @Nullable final String processDefinitionKey,
      @RequestParam final String startDateFrom,
      @RequestParam final String startDateTo,
      final HttpServletRequest request) {
    return service.getCharts(
        buildParams(processDefinitionKey, startDateFrom, startDateTo, request));
  }

  @GetMapping("/process-definitions")
  public List<DefinitionKeyResponseDto> getProcessDefinitions(
      @RequestParam final String startDateFrom,
      @RequestParam final String startDateTo,
      final HttpServletRequest request) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(request);
    final List<String> tenantIds = tenantService.getTenantIdsForUser(userId);
    final AgentQueryParams params =
        new AgentQueryParams(
            tenantIds, null, null, parseInstant(startDateFrom), parseInstant(startDateTo));
    return service.getProcessDefinitionsWithAgentRuns(params, userId);
  }

  private AgentQueryParams buildParams(
      @Nullable final String processDefinitionKey,
      final String startDateFrom,
      final String startDateTo,
      final HttpServletRequest request) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(request);
    final List<String> tenantIds = tenantService.getTenantIdsForUser(userId);
    return new AgentQueryParams(
        tenantIds,
        processDefinitionKey,
        null,
        parseInstant(startDateFrom),
        parseInstant(startDateTo));
  }

  private Instant parseInstant(final String value) {
    try {
      return Instant.parse(value);
    } catch (final DateTimeParseException e) {
      throw new BadRequestException(
          "Date parameter must be ISO-8601 instant format (e.g. 2024-01-01T00:00:00Z), got: "
              + value);
    }
  }
}
