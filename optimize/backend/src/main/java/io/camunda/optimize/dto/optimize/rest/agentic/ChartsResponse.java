/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.rest.agentic;

import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** Response for GET /api/agentic-control-plane/charts. */
@NullMarked
public record ChartsResponse(
    List<ToolFrequencyItem> toolFrequency,
    List<AvgTokensItem> avgTokensPerCall,
    @Nullable List<VersionIncidentItem> incidentRateByVersion // null at L0
    ) {

  public record ToolFrequencyItem(String toolName, long totalToolCalls) {}

  public record AvgTokensItem(
      String processDefinitionKey,
      @Nullable Double avgTokensPerCall, // null when totalModelCalls == 0; render as "—"
      long totalModelCalls) {}

  public record VersionIncidentItem(int version, double incidentRate, long runs) {}
}
