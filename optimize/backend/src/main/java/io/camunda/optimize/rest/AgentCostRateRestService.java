/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest;

import static io.camunda.optimize.tomcat.OptimizeResourceConstants.REST_API_PATH;

import io.camunda.optimize.dto.optimize.AgentCostRateConfigDto;
import io.camunda.optimize.service.AgentCostRateService;
import io.camunda.optimize.service.security.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reads/writes the LLM cost-rate config (see {@link AgentCostRateService}). The Business Value
 * Dashboard in the Hub calls these endpoints with the forwarded user OIDC token; any authenticated
 * Optimize user may read or set the rates.
 */
@Validated
@RestController
@RequestMapping(REST_API_PATH + AgentCostRateRestService.AGENT_COST_RATES_PATH)
public class AgentCostRateRestService {

  public static final String AGENT_COST_RATES_PATH = "/agent-cost-rates";

  private final SessionService sessionService;
  private final AgentCostRateService agentCostRateService;

  public AgentCostRateRestService(
      final SessionService sessionService, final AgentCostRateService agentCostRateService) {
    this.sessionService = sessionService;
    this.agentCostRateService = agentCostRateService;
  }

  @GetMapping
  public AgentCostRateConfigDto getConfig() {
    return agentCostRateService.getConfig();
  }

  @PutMapping
  public void setConfig(
      @NotNull @RequestBody final AgentCostRateConfigDto config, final HttpServletRequest request) {
    // Ensures the caller is an authenticated Optimize user before persisting.
    sessionService.getRequestUserOrFailNotAuthorized(request);
    agentCostRateService.upsert(config);
  }
}
