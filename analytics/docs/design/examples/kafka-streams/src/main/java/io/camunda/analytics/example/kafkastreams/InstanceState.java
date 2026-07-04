/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.example.kafkastreams;

import java.util.HashMap;
import java.util.Map;

/**
 * The per-instance state maintained by Stage A (the base projection).
 *
 * <p>One of these lives in the {@code KeyValueStore<Long, InstanceState>} that Kafka Streams manages
 * for us, keyed by {@code instanceKey}. This is Model A: the running fold of every event we have seen
 * for a single process instance. When the instance completes (or its SLA timer fires) we derive a
 * {@link CompletionFact} from this state and then <b>evict</b> the entry so the store does not grow
 * without bound.
 *
 * <p>It is a mutable POJO on purpose: the processor reads it from the store, mutates it, and writes it
 * back. Kafka Streams handles the durability of that write (changelog topic + local RocksDB); we only
 * describe the shape and the fold.
 */
public class InstanceState {

  public enum Status {
    OPEN,
    COMPLETED,
    TERMINATED
  }

  private long startMs = -1L;
  private long endMs = -1L;
  private Status status = Status.OPEN;
  private boolean hadIncident = false;
  private String processId;
  private String tenantId;
  private final Map<String, String> vars = new HashMap<>();

  public String processId() {
    return processId;
  }

  public void setProcessId(final String processId) {
    this.processId = processId;
  }

  public String tenantId() {
    return tenantId;
  }

  public void setTenantId(final String tenantId) {
    this.tenantId = tenantId;
  }

  public long startMs() {
    return startMs;
  }

  public void setStartMs(final long startMs) {
    this.startMs = startMs;
  }

  public long endMs() {
    return endMs;
  }

  public void setEndMs(final long endMs) {
    this.endMs = endMs;
  }

  public Status status() {
    return status;
  }

  public void setStatus(final Status status) {
    this.status = status;
  }

  public boolean hadIncident() {
    return hadIncident;
  }

  public void setHadIncident(final boolean hadIncident) {
    this.hadIncident = hadIncident;
  }

  public Map<String, String> vars() {
    return vars;
  }

  public boolean isOpen() {
    return status == Status.OPEN;
  }

  public boolean isStarted() {
    return startMs >= 0;
  }
}
