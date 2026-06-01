/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.waitstate;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.WaitStateRelated;
import java.util.Map;

/**
 * Represents a single waiting-state entry exported from the engine.
 *
 * <p>A waiting state captures a process element that is currently paused, awaiting an external
 * signal to continue (a job worker, message correlation, user task completion, timer firing, etc.).
 * The {@link #details} map carries wait-state-specific attributes (e.g. job type, message name, due
 * date) and is serialized by each backend exporter according to its own schema.
 *
 * <p>Instances are built via {@link #of(Record)} and then enriched by a {@link
 * WaitStateTransformer} which sets the remaining fields.
 */
public class WaitStateEntry {

  private long rootProcessInstanceKey;
  private long processInstanceKey;
  private long elementInstanceKey;
  private String elementId;
  private BpmnElementType elementType;
  private WaitStateType waitStateType;
  private Map<String, Object> details;
  private String tenantId;
  private long partitionId;

  public long getRootProcessInstanceKey() {
    return rootProcessInstanceKey;
  }

  public WaitStateEntry setRootProcessInstanceKey(final long rootProcessInstanceKey) {
    this.rootProcessInstanceKey = rootProcessInstanceKey;
    return this;
  }

  public long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public WaitStateEntry setProcessInstanceKey(final long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
    return this;
  }

  public long getElementInstanceKey() {
    return elementInstanceKey;
  }

  public WaitStateEntry setElementInstanceKey(final long elementInstanceKey) {
    this.elementInstanceKey = elementInstanceKey;
    return this;
  }

  public String getElementId() {
    return elementId;
  }

  public WaitStateEntry setElementId(final String elementId) {
    this.elementId = elementId;
    return this;
  }

  public BpmnElementType getElementType() {
    return elementType;
  }

  public WaitStateEntry setElementType(final BpmnElementType elementType) {
    this.elementType = elementType;
    return this;
  }

  public WaitStateType getWaitStateType() {
    return waitStateType;
  }

  public WaitStateEntry setWaitStateType(final WaitStateType waitStateType) {
    this.waitStateType = waitStateType;
    return this;
  }

  public Map<String, Object> getDetails() {
    return details;
  }

  public WaitStateEntry setDetails(final Map<String, Object> details) {
    this.details = details;
    return this;
  }

  public String getTenantId() {
    return tenantId;
  }

  public WaitStateEntry setTenantId(final String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  public long getPartitionId() {
    return partitionId;
  }

  public WaitStateEntry setPartitionId(final long partitionId) {
    this.partitionId = partitionId;
    return this;
  }

  /**
   * Builds a partial {@link WaitStateEntry} from a record whose value implements {@link
   * WaitStateRelated}.
   *
   * <p>The identity fields ({@code rootProcessInstanceKey}, {@code processInstanceKey}, {@code
   * elementInstanceKey}, {@code elementId}, {@code tenantId}, {@code partitionId}) are extracted
   * from the record. {@code elementType}, {@code waitStateType}, and {@code details} are left unset
   * — the {@link WaitStateTransformer} is responsible for supplying those fields.
   */
  public static <R extends RecordValue & WaitStateRelated> WaitStateEntry of(
      final Record<R> record) {
    final R value = record.getValue();
    return new WaitStateEntry()
        .setRootProcessInstanceKey(value.getRootProcessInstanceKey())
        .setProcessInstanceKey(value.getProcessInstanceKey())
        .setElementInstanceKey(value.getElementInstanceKey())
        .setElementId(value.getElementId())
        .setTenantId(value.getTenantId())
        .setPartitionId(record.getPartitionId());
  }

  public enum WaitStateType {
    JOB,
    MESSAGE,
    USER_TASK,
    TIMER,
    SIGNAL,
    INCIDENT,
    CALL_ACTIVITY
  }
}
