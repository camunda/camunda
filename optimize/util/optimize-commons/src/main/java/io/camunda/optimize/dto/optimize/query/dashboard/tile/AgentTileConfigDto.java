/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.dashboard.tile;

/**
 * Tile configuration for Agentic Control Plane dashboard tiles. Drives frontend delta badge
 * rendering (s-tile-execution-kpis-delta).
 */
public class AgentTileConfigDto {

  /** When true the frontend fires a comparison-period evaluate call to compute the delta badge. */
  private boolean comparisonPeriod;

  /**
   * Direction in which a delta is considered positive/good. Either {@code "up"} (higher is better,
   * e.g. total executions) or {@code "down"} (lower is better, e.g. duration, incident rate).
   */
  private String deltaGoodDirection;

  public AgentTileConfigDto(final boolean comparisonPeriod, final String deltaGoodDirection) {
    this.comparisonPeriod = comparisonPeriod;
    this.deltaGoodDirection = deltaGoodDirection;
  }

  public AgentTileConfigDto() {}

  public boolean isComparisonPeriod() {
    return comparisonPeriod;
  }

  public void setComparisonPeriod(final boolean comparisonPeriod) {
    this.comparisonPeriod = comparisonPeriod;
  }

  public String getDeltaGoodDirection() {
    return deltaGoodDirection;
  }

  public void setDeltaGoodDirection(final String deltaGoodDirection) {
    this.deltaGoodDirection = deltaGoodDirection;
  }
}
