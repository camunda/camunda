/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.mcp.config;

import static io.camunda.zeebe.gateway.mcp.tool.ToolDescriptions.EVENTUAL_CONSISTENCY_NOTE;

import io.camunda.zeebe.gateway.mcp.model.IncidentSearchQuery;
import io.camunda.zeebe.gateway.mcp.tool.incident.IncidentTools;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Configures tool callbacks for tools with complex input request objects. */
@Configuration
public class McpToolConfiguration {

  @Bean
  public ToolCallback incidentSearchToolCallback(final IncidentTools incidentTools) {
    return FunctionToolCallback.builder("searchIncidents", incidentTools::searchIncidents)
        .description("Search for incidents based on given criteria. " + EVENTUAL_CONSISTENCY_NOTE)
        .inputType(IncidentSearchQuery.class)
        .build();
  }
}
