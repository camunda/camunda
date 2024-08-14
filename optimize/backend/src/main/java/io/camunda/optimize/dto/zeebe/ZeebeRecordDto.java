/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.zeebe;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@EqualsAndHashCode
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
public abstract class ZeebeRecordDto<VALUE extends RecordValue, INTENT extends Intent>
    implements Record<VALUE> {

  private long position;
  // sequence field was introduced with 8.2.0, it will not be present in records of prior versions
  private Long sequence;
  private long sourceRecordPosition;
  private long key;
  private long timestamp;
  private int partitionId;
  private RecordType recordType;
  private RejectionType rejectionType;
  private String rejectionReason;
  private String brokerVersion;
  private ValueType valueType;
  private VALUE value;
  private INTENT intent;
  private Map<String, Object> authorizations;
  private long operationReference;

  @Override
  public String toJson() {
    throw new UnsupportedOperationException("Operation not supported");
  }

  @Override
  public int getRecordVersion() {
    throw new UnsupportedOperationException("Operation not supported");
  }

  @Override
  public Record<VALUE> copyOf() {
    throw new UnsupportedOperationException("Operation not supported");
  }

  public OffsetDateTime getDateForTimestamp() {
    return OffsetDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());
  }

  public static final class Fields {

    public static final String position = "position";
    public static final String sequence = "sequence";
    public static final String sourceRecordPosition = "sourceRecordPosition";
    public static final String key = "key";
    public static final String timestamp = "timestamp";
    public static final String partitionId = "partitionId";
    public static final String recordType = "recordType";
    public static final String rejectionType = "rejectionType";
    public static final String rejectionReason = "rejectionReason";
    public static final String brokerVersion = "brokerVersion";
    public static final String valueType = "valueType";
    public static final String value = "value";
    public static final String intent = "intent";
    public static final String authorizations = "authorizations";
    public static final String operationReference = "operationReference";
  }
}
