/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.jackson;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.As;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver;
import io.camunda.zeebe.protocol.record.Agent;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.intent.Intent;

/**
 * This annotation mixin is used by Jackson during deserialization of {@link Record} objects. It's
 * necessary to be able to resolve the intent and the value to their correct concrete class based on
 * the value type of the record.
 *
 * <p>NOTE: the type represented by {@code T} can be abstract or concrete, it doesn't matter much
 * here.
 *
 * @param <T> the record value type
 */
@SuppressWarnings("unused")
abstract class RecordMixin<T extends RecordValue> {
  @JsonTypeInfo(use = Id.CUSTOM, include = As.EXTERNAL_PROPERTY, property = "valueType")
  @JsonTypeIdResolver(IntentTypeIdResolver.class)
  private Intent intent;

  @JsonTypeInfo(use = Id.CUSTOM, include = As.EXTERNAL_PROPERTY, property = "valueType")
  @JsonTypeIdResolver(RecordValueTypeIdResolver.class)
  private T value;

  @JsonTypeInfo(use = Id.CUSTOM, include = As.EXTERNAL_PROPERTY, property = "valueType")
  @JsonTypeIdResolver(RecordValueTypeIdResolver.class)
  private T commandValue;

  @JsonTypeInfo(use = Id.CUSTOM, include = As.EXTERNAL_PROPERTY, property = "valueType")
  @JsonTypeIdResolver(RecordValueTypeIdResolver.class)
  private T recordValue;

  @JsonSetter(nulls = Nulls.SKIP)
  private Agent agent;
}
