/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.examples.flink;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The per-instance projection accumulated in Stage A — "Model A" in our design.
 *
 * <p>This is the value held in the keyed {@code ValueState} of {@link BaseProjectionFunction}. It
 * lives (per instanceKey) in the RocksDB state backend, is checkpointed with the rest of the job,
 * and is {@code state.clear()}-ed the moment the instance reaches a terminal state (or breaches its
 * SLA). It is a mutable POJO on purpose: {@code ValueState} does a read-modify-write per event, and
 * Flink re-serializes it on {@code update()}.
 *
 * <p>Mutability + a nullary constructor keeps this on Flink's POJO serializer path.
 */
public final class InstanceState implements Serializable {

  /** Lifecycle status of the instance as seen by the projection so far. */
  public enum Status {
    OPEN,
    COMPLETED,
    TERMINATED
  }

  private String processId = "";
  private String tenantId = "";
  private long startMs;
  private long endMs;
  private Status status = Status.OPEN;
  private boolean hadIncident;
  private Map<String, String> vars = new LinkedHashMap<>();

  /** Required no-arg constructor for Flink POJO serialization. */
  public InstanceState() {}

  public String processId() {
    return processId;
  }

  public void processId(final String processId) {
    this.processId = processId;
  }

  public String tenantId() {
    return tenantId;
  }

  public void tenantId(final String tenantId) {
    this.tenantId = tenantId;
  }

  public long startMs() {
    return startMs;
  }

  public void startMs(final long startMs) {
    this.startMs = startMs;
  }

  public long endMs() {
    return endMs;
  }

  public void endMs(final long endMs) {
    this.endMs = endMs;
  }

  public Status status() {
    return status;
  }

  public void status(final Status status) {
    this.status = status;
  }

  public boolean hadIncident() {
    return hadIncident;
  }

  public void hadIncident(final boolean hadIncident) {
    this.hadIncident = hadIncident;
  }

  public Map<String, String> vars() {
    return vars;
  }

  public void putVar(final String name, final @Nullable String value) {
    vars.put(name, value == null ? "" : value);
  }

  public boolean isOpen() {
    return status == Status.OPEN;
  }
}
