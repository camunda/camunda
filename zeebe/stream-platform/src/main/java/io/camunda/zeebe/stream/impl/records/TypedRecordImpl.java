/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.impl.records;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.logstreams.log.LoggedEvent;
import io.camunda.zeebe.protocol.impl.encoding.AuthInfo;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.Agent;
import io.camunda.zeebe.protocol.record.ChannelType;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.WrittenRecord;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.StringUtil;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public final class TypedRecordImpl implements TypedRecord, WrittenRecord {
  private final int partitionId;
  private @Nullable LoggedEvent rawEvent;
  private @Nullable RecordMetadata metadata;
  private @Nullable UnifiedRecordValue value;

  public TypedRecordImpl(final int partitionId) {
    this.partitionId = partitionId;
  }

  @SuppressWarnings("NullAway.Init")
  public void wrap(
      final LoggedEvent rawEvent, final RecordMetadata metadata, final UnifiedRecordValue value) {
    this.rawEvent = rawEvent;
    this.metadata = metadata;
    this.value = value;
  }

  @JsonIgnore
  public @Nullable RecordMetadata getMetadata() {
    return metadata;
  }

  @Override
  public long getPosition() {
    return requireNonNull(rawEvent).getPosition();
  }

  @Override
  public long getSourceRecordPosition() {
    return requireNonNull(rawEvent).getSourceEventPosition();
  }

  @Override
  public long getTimestamp() {
    return requireNonNull(rawEvent).getTimestamp();
  }

  @Override
  public Intent getIntent() {
    return requireNonNull(metadata).getIntent();
  }

  @Override
  public int getPartitionId() {
    return partitionId;
  }

  @Override
  public RecordType getRecordType() {
    return requireNonNull(metadata).getRecordType();
  }

  @Override
  public RejectionType getRejectionType() {
    return requireNonNull(metadata).getRejectionType();
  }

  @Override
  public String getRejectionReason() {
    return requireNonNull(metadata).getRejectionReason();
  }

  @Override
  public String getBrokerVersion() {
    return requireNonNull(metadata).getBrokerVersion().toString();
  }

  @Override
  public Map<String, Object> getAuthorizations() {
    return requireNonNull(metadata).getAuthorization().toDecodedMap();
  }

  @Override
  public Agent getAgent() {
    return requireNonNull(metadata).getAgent();
  }

  @Override
  public ChannelType getRequestChannelType() {
    return requireNonNull(metadata).getRequestChannelType();
  }

  @Override
  public String getRequestToolName() {
    return requireNonNull(metadata).getRequestToolName();
  }

  @Override
  public int getRecordVersion() {
    return requireNonNull(metadata).getRecordVersion();
  }

  @Override
  public ValueType getValueType() {
    return requireNonNull(metadata).getValueType();
  }

  @Override
  public long getOperationReference() {
    return requireNonNull(metadata).getOperationReference();
  }

  @Override
  public long getBatchOperationReference() {
    return requireNonNull(metadata).getBatchOperationReference();
  }

  @Override
  public Record copyOf() {
    return CopiedRecords.createCopiedRecord(getPartitionId(), requireNonNull(rawEvent));
  }

  @Override
  public long getKey() {
    return requireNonNull(rawEvent).getKey();
  }

  @Override
  public UnifiedRecordValue getValue() {
    return requireNonNull(value);
  }

  @Override
  public AuthInfo getAuthInfo() {
    return requireNonNull(metadata).getAuthorization();
  }

  @Override
  @JsonIgnore
  public int getRequestStreamId() {
    return requireNonNull(metadata).getRequestStreamId();
  }

  @Override
  @JsonIgnore
  public long getRequestId() {
    return requireNonNull(metadata).getRequestId();
  }

  @Override
  @JsonIgnore
  public int getLength() {
    return requireNonNull(metadata).getLength() + requireNonNull(value).getLength();
  }

  @Override
  @JsonIgnore
  public int getRawLength() {
    return requireNonNull(rawEvent).getLength();
  }

  @Override
  public String toJson() {
    return MsgPackConverter.convertJsonSerializableObjectToJson(this);
  }

  @Override
  public String toString() {
    return "TypedRecordImpl{"
        + "metadata="
        + metadata
        + ", value="
        + StringUtil.limitString(requireNonNull(value).toString(), 1024)
        + '}';
  }
}
