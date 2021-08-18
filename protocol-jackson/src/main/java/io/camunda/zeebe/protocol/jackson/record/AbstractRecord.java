/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.protocol.jackson.record;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.As;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver;
import io.camunda.zeebe.protocol.jackson.record.RecordBuilder.ImmutableRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import org.immutables.value.Value;

@Value.Immutable
@ZeebeStyle
@JsonDeserialize(as = ImmutableRecord.class)
public abstract class AbstractRecord<T extends RecordValue>
    implements Record<T>, DefaultJsonSerializable {
  @Value.Default
  @JsonTypeInfo(use = Id.CUSTOM, include = As.EXTERNAL_PROPERTY, property = "valueType")
  @JsonTypeIdResolver(IntentTypeIdResolver.class)
  @Override
  public Intent getIntent() {
    return Intent.UNKNOWN;
  }

  @Value.Default
  @Override
  public RecordType getRecordType() {
    return RecordType.NULL_VAL;
  }

  @Value.Default
  @Override
  public RejectionType getRejectionType() {
    return RejectionType.NULL_VAL;
  }

  @JsonTypeInfo(use = Id.CUSTOM, include = As.EXTERNAL_PROPERTY, property = "valueType")
  @JsonTypeIdResolver(ValueTypeIdResolver.class)
  @Override
  public abstract T getValue();

  /** @return itself as the object is immutable and can be used as is */
  @SuppressWarnings({"MethodDoesntCallSuperMethod", "squid:S2975", "squid:S1182"})
  @Override
  public Record<T> clone() {
    return this;
  }
}
