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
 * The raw input record flowing off the event bridge.
 *
 * <p>REFERENCE EXAMPLE — not built, not wired into anything.
 *
 * <p>This is deliberately a mutable JavaBean (public no-arg constructor + getters/setters). Spark's
 * {@link org.apache.spark.sql.Encoders#bean(Class)} reflects over JavaBean properties to build the
 * serializer/deserializer for a typed {@code Dataset<SourceEvent>}. That is the idiomatic way to
 * carry a POJO through the typed Dataset API; records / immutable classes are not (yet) supported by
 * the bean encoder, so we accept the ceremony.
 *
 * <p>Note the {@link #eventTime} field: Structured Streaming needs an actual {@code TimestampType}
 * column to attach a watermark to (see {@link AnalyticsPipeline}). We mirror {@link #timestampMs}
 * into a {@link Timestamp} at ingestion rather than casting in SQL, so the typed Dataset keeps a
 * first-class event-time column that both the watermark and the {@code EventTimeTimeout} can read.
 */
public class SourceEvent implements Serializable {

  // Java enums are serialized as StringType by the bean encoder. This relies on the
  // JavaTypeInference enum support introduced in Spark 3.5 (the version pinned in pom.xml); on
  // older Spark you would model this field as a String instead.
  public enum Type {
    ACTIVATED,
    COMPLETED,
    TERMINATED,
    VARIABLE,
    INCIDENT
  }

  private Type type;
  private long instanceKey;
  private String processId;
  private String tenantId;
  private long timestampMs;

  /** Event-time as a Spark TimestampType. Mirrors {@link #timestampMs}; used for the watermark. */
  private Timestamp eventTime;

  // Nullable — only populated for the event types that carry them.
  private String elementId;
  private String varName;
  private String varValue;

  // Origin coordinate on the source log — the basis for the forward-only gate and, in a real
  // build, for origin-dedup / exactly-once bookkeeping.
  private int sourcePartition;
  private long sourceOffset;

  public SourceEvent() {}

  public Type getType() {
    return type;
  }

  public void setType(final Type type) {
    this.type = type;
  }

  public long getInstanceKey() {
    return instanceKey;
  }

  public void setInstanceKey(final long instanceKey) {
    this.instanceKey = instanceKey;
  }

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

  public long getTimestampMs() {
    return timestampMs;
  }

  public void setTimestampMs(final long timestampMs) {
    this.timestampMs = timestampMs;
  }

  public Timestamp getEventTime() {
    return eventTime;
  }

  public void setEventTime(final Timestamp eventTime) {
    this.eventTime = eventTime;
  }

  public String getElementId() {
    return elementId;
  }

  public void setElementId(final String elementId) {
    this.elementId = elementId;
  }

  public String getVarName() {
    return varName;
  }

  public void setVarName(final String varName) {
    this.varName = varName;
  }

  public String getVarValue() {
    return varValue;
  }

  public void setVarValue(final String varValue) {
    this.varValue = varValue;
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
