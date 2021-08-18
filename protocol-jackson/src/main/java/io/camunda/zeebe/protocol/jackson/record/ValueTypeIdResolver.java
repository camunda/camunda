/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.protocol.jackson.record;

import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.fasterxml.jackson.databind.DatabindContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.jsontype.impl.TypeIdResolverBase;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;

/**
 * A {@link com.fasterxml.jackson.databind.jsontype.TypeIdResolver} that maps a serialized {@link
 * RecordValue} value to a concrete implementation enum, e.g. {@link ImmutableVariableRecordValue},
 * based on the value type of the record.
 */
final class ValueTypeIdResolver extends TypeIdResolverBase {

  @Override
  public String idFromValue(final Object value) {
    return ((ValueType) value).name();
  }

  @Override
  public String idFromValueAndType(final Object value, final Class<?> suggestedType) {
    return idFromValue(value);
  }

  @Override
  public Id getMechanism() {
    return Id.CUSTOM;
  }

  @Override
  public JavaType typeFromId(final DatabindContext context, final String id) {
    final var valueType = ValueType.valueOf(id);
    final var typeFactory = context.getTypeFactory();
    return typeFactory.constructType(mapValueTypeToRecordValue(valueType));
  }

  private Class<? extends RecordValue> mapValueTypeToRecordValue(final ValueType valueType) {
    return ValueTypes.getTypeInfo(valueType).getValueClass();
  }
}
