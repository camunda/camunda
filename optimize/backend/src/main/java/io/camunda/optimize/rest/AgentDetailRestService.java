/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest;

import static io.camunda.optimize.tomcat.OptimizeResourceConstants.REST_API_PATH;

import io.camunda.optimize.dto.optimize.rest.agentic.AgentDetailDto;
import io.camunda.optimize.dto.optimize.rest.agentic.ProcessFailuresDto;
import io.camunda.optimize.rest.exceptions.NotFoundException;
import io.camunda.optimize.service.dashboard.AgentDetailService;
import java.time.OffsetDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The single agent view and the process failures of the agentic dashboard. Prototype: not scoped to
 * the user's authorized definitions and tenants yet.
 */
@RestController
@RequestMapping(REST_API_PATH + "/dashboard/agentic")
public class AgentDetailRestService {

  private final AgentDetailService agentDetailService;

  public AgentDetailRestService(final AgentDetailService agentDetailService) {
    this.agentDetailService = agentDetailService;
  }

  @GetMapping("/agents/{agentId}")
  public AgentDetailDto getAgent(
      @PathVariable("agentId") final String agentId,
      @RequestParam(name = "endedFrom", required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          final OffsetDateTime endedFrom) {
    return agentDetailService
        .getAgentDetail(agentId, endedFrom)
        .orElseThrow(() -> new NotFoundException("Unknown agent " + agentId));
  }

  @GetMapping("/failures")
  public ProcessFailuresDto getProcessFailures(
      @RequestParam(name = "processDefinitionKey") final String processDefinitionKey,
      @RequestParam(name = "endedFrom", required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          final OffsetDateTime endedFrom) {
    return agentDetailService.getProcessFailures(processDefinitionKey, endedFrom);
  }
}
