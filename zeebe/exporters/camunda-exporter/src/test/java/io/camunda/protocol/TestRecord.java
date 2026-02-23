/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.protocol;

import io.camunda.zeebe.protocol.record.Agent;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import java.util.Map;

public record TestRecord(long position, ValueType valueType) implements Record<TestValue> {

  @Override
  public long getPosition() {
    return position;
  }

  @Override
  public long getSourceRecordPosition() {
    return 0;
  }

  @Override
  public long getKey() {
    return 0;
  }

  @Override
  public long getTimestamp() {
    return -1;
  }

  @Override
  public Intent getIntent() {
    return null;
  }

  @Override
  public int getPartitionId() {
    return 0;
  }

  @Override
  public RecordType getRecordType() {
    return null;
  }

  @Override
  public RejectionType getRejectionType() {
    return null;
  }

  @Override
  public String getRejectionReason() {
    return null;
  }

  @Override
  public String getBrokerVersion() {
    return null;
  }

  @Override
  public Map<String, Object> getAuthorizations() {
    return Map.of();
  }

  @Override
  public Agent getAgent() {
    return null;
  }

  @Override
  public int getRecordVersion() {
    return 1;
  }

  @Override
  public ValueType getValueType() {
    return valueType;
  }

  @Override
  public TestValue getValue() {
    return null;
  }

  @Override
  public long getOperationReference() {
    return 0;
  }

  @Override
  public long getBatchOperationReference() {
    return 0;
  }

  @Override
  public Record<TestValue> copyOf() {
    return this;
  }

  @Override
  public String toJson() {
    return null;
  }
}
