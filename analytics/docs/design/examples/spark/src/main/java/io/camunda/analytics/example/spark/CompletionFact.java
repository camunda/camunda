/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.example.spark;

import java.io.Serializable;
import java.sql.Timestamp;

/**
 * The fact derived by Stage A when an instance reaches a terminal state (or breaches its SLA).
 *
 * <p>REFERENCE EXAMPLE — not built, not wired into anything.
 *
 * <p>This is the output element type {@code R} of the {@code flatMapGroupsWithState} in {@link
 * BaseProjectionState}. Emitting it is the "derive" step; the {@code groupBy} in {@link
 * AnalyticsPipeline} then re-partitions the stream of these facts by {@code processId} — that
 * {@code groupBy} exchange is the shuffle between Stage A and Stage B/C.
 *
 * <p>{@link #startWindow} is {@link #startWindowMs} materialised as a {@code TimestampType} so
 * Stage B can attach a watermark and open 1-minute tumbling windows over it directly.
 */
public class CompletionFact implements Serializable {

  private String processId;
  private String tenantId;

  /** {@code startMs} floored to the minute (epoch millis). */
  private long startWindowMs;

  /** {@link #startWindowMs} as a Spark TimestampType — the event-time column for Stage B. */
  private Timestamp startWindow;

  private long durationMs;
  private boolean hadIncident;

  /** True when this fact was emitted by the SLA timeout path rather than a real COMPLETED/TERMINATED. */
  private boolean slaBreach;

  // Carried through from the terminal source event so downstream stages keep an origin coordinate.
  private int sourcePartition;
  private long sourceOffset;

  public CompletionFact() {}

  public String getProcessId() {
    return processId;
  }

  public void setProcessId(final String processId) {
    this.processId = processId;
  }

  public String getTenantId() {
    return tenantId;
  }

  public void setTenantId(final String tenantId) {
    this.tenantId = tenantId;
  }

  public long getStartWindowMs() {
    return startWindowMs;
  }

  public void setStartWindowMs(final long startWindowMs) {
    this.startWindowMs = startWindowMs;
  }

  public Timestamp getStartWindow() {
    return startWindow;
  }

  public void setStartWindow(final Timestamp startWindow) {
    this.startWindow = startWindow;
  }

  public long getDurationMs() {
    return durationMs;
  }

  public void setDurationMs(final long durationMs) {
    this.durationMs = durationMs;
  }

  public boolean isHadIncident() {
    return hadIncident;
  }

  public void setHadIncident(final boolean hadIncident) {
    this.hadIncident = hadIncident;
  }

  public boolean isSlaBreach() {
    return slaBreach;
  }

  public void setSlaBreach(final boolean slaBreach) {
    this.slaBreach = slaBreach;
  }

  public int getSourcePartition() {
    return sourcePartition;
  }

  public void setSourcePartition(final int sourcePartition) {
    this.sourcePartition = sourcePartition;
  }

  public long getSourceOffset() {
    return sourceOffset;
  }

  public void setSourceOffset(final long sourceOffset) {
    this.sourceOffset = sourceOffset;
  }
}
