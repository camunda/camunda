/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest;

import static io.camunda.optimize.tomcat.OptimizeResourceConstants.REST_API_PATH;

import io.camunda.optimize.dto.optimize.rest.agentic.AgenticFilterOptionsDto;
import io.camunda.optimize.service.db.reader.AgenticFilterOptionsReader;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Choices for the agent and model filters of the agentic dashboard. Prototype: not scoped to the
 * user's authorized definitions and tenants yet.
 */
@RestController
@RequestMapping(REST_API_PATH + "/dashboard/agentic/filter-options")
public class AgenticFilterOptionsRestService {

  private final AgenticFilterOptionsReader reader;

  public AgenticFilterOptionsRestService(final AgenticFilterOptionsReader reader) {
    this.reader = reader;
  }

  @GetMapping
  public AgenticFilterOptionsDto getFilterOptions(
      @RequestParam(name = "processDefinitionKey", required = false)
          final String processDefinitionKey) {
    return reader.getFilterOptions(processDefinitionKey);
  }
}
