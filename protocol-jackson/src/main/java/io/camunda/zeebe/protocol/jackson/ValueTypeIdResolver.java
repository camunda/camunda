/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.protocol.jackson;

import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.fasterxml.jackson.databind.DatabindContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.jsontype.impl.TypeIdResolverBase;
import com.fasterxml.jackson.databind.type.TypeFactory;
import edu.umd.cs.findbugs.annotations.NonNull;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import java.util.Objects;

/**
 * A {@link com.fasterxml.jackson.databind.jsontype.TypeIdResolver} that maps a serialized {@link
 * RecordValue} value to a concrete implementation enum, e.g. {@link
 * io.camunda.zeebe.protocol.record.value.ImmutableVariableRecordValue}, based on the value type of
 * the record.
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
    final ValueType valueType = ValueType.valueOf(id);
    final TypeFactory typeFactory = context.getTypeFactory();
    return typeFactory.constructType(mapValueTypeToRecordValue(valueType));
  }

  @NonNull
  private Class<? extends RecordValue> mapValueTypeToRecordValue(
      @NonNull final ValueType valueType) {
    return ValueTypes.getTypeInfo(Objects.requireNonNull(valueType, "must specify a value type"))
        .getValueClass();
  }
}
