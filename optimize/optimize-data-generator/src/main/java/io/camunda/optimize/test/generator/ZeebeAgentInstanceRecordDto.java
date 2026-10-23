/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.test.generator;

import java.util.Objects;

/**
 * Fixture envelope DTO for {@code AGENT_INSTANCE} Zeebe records.
 *
 * <p>Unlike the other record DTOs in this project, this class does NOT extend {@link
 * io.camunda.optimize.dto.zeebe.ZeebeRecordDto} because the corresponding SBE {@code
 * ValueType.AGENT_INSTANCE} enum constant does not exist yet. Instead, {@code valueType} is stored
 * as a plain {@code String} so that generated JSON documents contain the correct literal {@code
 * "AGENT_INSTANCE"} value that future Optimize importers will expect.
 *
 * <p>Replace with a proper {@code ZeebeRecordDto<ZeebeAgentInstanceDataDto, AgentInstanceIntent>}
 * subclass once the Zeebe protocol is updated.
 */
public class ZeebeAgentInstanceRecordDto {

  private long position;
  private Long sequence;
  private long key;
  private long timestamp;
  private int partitionId;

  /** Serializes as the literal string {@code "EVENT"} in the record JSON. */
  private final String recordType = "EVENT";

  /**
   * Serializes as the literal string {@code "AGENT_INSTANCE"} in the record JSON. Hardcoded because
   * the SBE {@code ValueType} enum does not yet include this value.
   */
  private final String valueType = "AGENT_INSTANCE";

  /** Intent name: {@code "CREATED"}, {@code "UPDATED"}, or {@code "COMPLETED"}. */
  private String intent;

  private String brokerVersion;

  private ZeebeAgentInstanceDataDto value;

  public ZeebeAgentInstanceRecordDto() {}

  // ── Getters / setters ─────────────────────────────────────────────────────

  public long getPosition() {
    return position;
  }

  public void setPosition(final long position) {
    this.position = position;
  }

  public Long getSequence() {
    return sequence;
  }

  public void setSequence(final Long sequence) {
    this.sequence = sequence;
  }

  public long getKey() {
    return key;
  }

  public void setKey(final long key) {
    this.key = key;
  }

  public long getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(final long timestamp) {
    this.timestamp = timestamp;
  }

  public int getPartitionId() {
    return partitionId;
  }

  public void setPartitionId(final int partitionId) {
    this.partitionId = partitionId;
  }

  public String getRecordType() {
    return recordType;
  }

  public String getValueType() {
    return valueType;
  }

  public String getIntent() {
    return intent;
  }

  public void setIntent(final String intent) {
    this.intent = intent;
  }

  public String getBrokerVersion() {
    return brokerVersion;
  }

  public void setBrokerVersion(final String brokerVersion) {
    this.brokerVersion = brokerVersion;
  }

  public ZeebeAgentInstanceDataDto getValue() {
    return value;
  }

  public void setValue(final ZeebeAgentInstanceDataDto value) {
    this.value = value;
  }

  // ── equals / hashCode / toString ─────────────────────────────────────────

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ZeebeAgentInstanceRecordDto that = (ZeebeAgentInstanceRecordDto) o;
    return position == that.position
        && key == that.key
        && timestamp == that.timestamp
        && partitionId == that.partitionId
        && Objects.equals(sequence, that.sequence)
        && Objects.equals(intent, that.intent)
        && Objects.equals(brokerVersion, that.brokerVersion)
        && Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(position, sequence, key, timestamp, partitionId, intent, brokerVersion);
  }

  @Override
  public String toString() {
    return "ZeebeAgentInstanceRecordDto("
        + "position="
        + position
        + ", key="
        + key
        + ", timestamp="
        + timestamp
        + ", partitionId="
        + partitionId
        + ", valueType='"
        + valueType
        + "', intent='"
        + intent
        + "', value="
        + value
        + ')';
  }

  @SuppressWarnings("checkstyle:ConstantName")
  public static final class Fields {

    public static final String position = "position";
    public static final String sequence = "sequence";
    public static final String key = "key";
    public static final String timestamp = "timestamp";
    public static final String partitionId = "partitionId";
    public static final String recordType = "recordType";
    public static final String valueType = "valueType";
    public static final String intent = "intent";
    public static final String brokerVersion = "brokerVersion";
    public static final String value = "value";
  }
}
