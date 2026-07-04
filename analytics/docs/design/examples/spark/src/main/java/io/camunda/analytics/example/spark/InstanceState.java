/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.example.spark;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * The per-instance projection (Model A), the value held inside {@code GroupState<InstanceState>}.
 *
 * <p>REFERENCE EXAMPLE — not built, not wired into anything.
 *
 * <p>This is the entire hand-written state model for one process instance. Spark owns its
 * persistence (the RocksDB / in-memory state store, checkpointed per micro-batch); we own its
 * <em>shape</em> and its transition rules (see {@link BaseProjectionState}).
 *
 * <p>JavaBean again, for {@code Encoders.bean(InstanceState.class)}. A {@code Map<String,String>}
 * property is supported by the bean encoder and stored as a {@code MapType} column inside the state
 * store row.
 */
public class InstanceState implements Serializable {

  public enum Status {
    ACTIVE,
    COMPLETED,
    TERMINATED
  }

  private long startMs;
  private long endMs;
  private Status status = Status.ACTIVE;
  private boolean hadIncident;
  private Map<String, String> vars = new HashMap<>();

  public InstanceState() {}

  public long getStartMs() {
    return startMs;
  }

  public void setStartMs(final long startMs) {
    this.startMs = startMs;
  }

  public long getEndMs() {
    return endMs;
  }

  public void setEndMs(final long endMs) {
    this.endMs = endMs;
  }

  public Status getStatus() {
    return status;
  }

  public void setStatus(final Status status) {
    this.status = status;
  }

  public boolean isHadIncident() {
    return hadIncident;
  }

  public void setHadIncident(final boolean hadIncident) {
    this.hadIncident = hadIncident;
  }

  public Map<String, String> getVars() {
    return vars;
  }

  public void setVars(final Map<String, String> vars) {
    this.vars = vars;
  }
}
