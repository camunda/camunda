/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest;

import static io.camunda.optimize.tomcat.OptimizeResourceConstants.REST_API_PATH;

import io.camunda.optimize.dto.optimize.rest.agentic.AgentInventoryDto;
import io.camunda.optimize.service.dashboard.AgentInventoryService;
import java.time.OffsetDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent inventory of the agentic dashboard overview. Prototype: not scoped to the user's authorized
 * definitions and tenants yet.
 */
@RestController
@RequestMapping(REST_API_PATH + "/dashboard/agentic/agent-inventory")
public class AgentInventoryRestService {

  private final AgentInventoryService agentInventoryService;

  public AgentInventoryRestService(final AgentInventoryService agentInventoryService) {
    this.agentInventoryService = agentInventoryService;
  }

  @GetMapping
  public AgentInventoryDto getAgentInventory(
      @RequestParam(name = "endedFrom", required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          final OffsetDateTime endedFrom) {
    return agentInventoryService.getAgentInventory(endedFrom);
  }
}
