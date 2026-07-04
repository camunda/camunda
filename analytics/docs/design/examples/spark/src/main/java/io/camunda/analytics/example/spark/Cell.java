/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.example.spark;

import java.io.Serializable;

/**
 * The serving-side output row: one aggregated cell per (processId, 1-minute window).
 *
 * <p>REFERENCE EXAMPLE — not built, not wired into anything.
 *
 * <p>This is the shape of a row in the Stage C result table. In this example the aggregation is
 * expressed declaratively ({@code groupBy(...).agg(count, avg)}), so Spark actually produces a
 * {@code Row} with columns {@code window/processId/count/avgDurationMs}; {@link Cell} documents the
 * intended serving schema and is the type we would map that Row into before writing to a real sink.
 */
public class Cell implements Serializable {

  private String processId;
  private long windowStartMs;
  private long count;
  private double avgDurationMs;

  public Cell() {}

  public Cell(
      final String processId,
      final long windowStartMs,
      final long count,
      final double avgDurationMs) {
    this.processId = processId;
    this.windowStartMs = windowStartMs;
    this.count = count;
    this.avgDurationMs = avgDurationMs;
  }

  public String getProcessId() {
    return processId;
  }

  public void setProcessId(final String processId) {
    this.processId = processId;
  }

  public long getWindowStartMs() {
    return windowStartMs;
  }

  public void setWindowStartMs(final long windowStartMs) {
    this.windowStartMs = windowStartMs;
  }

  public long getCount() {
    return count;
  }

  public void setCount(final long count) {
    this.count = count;
  }

  public double getAvgDurationMs() {
    return avgDurationMs;
  }

  public void setAvgDurationMs(final double avgDurationMs) {
    this.avgDurationMs = avgDurationMs;
  }
}
