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

/** Response for GET /api/agentic-control-plane/trends. */
@NullMarked
public record TrendsResponse(String interval, List<TrendPoint> trend) {

  public record TrendPoint(
      String date,
      long inputTokens,
      long outputTokens,
      long tokenP5,
      long tokenP50,
      long tokenP95,
      long durationP50Ms,
      long durationP95Ms) {}
}
