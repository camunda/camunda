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
import io.camunda.zeebe.protocol.record.ValueType;
import java.lang.reflect.Type;

/**
 * Abstract {@link com.fasterxml.jackson.databind.jsontype.TypeIdResolver} which can resolve types
 * from the given {@link ValueType}.
 */
abstract class AbstractValueTypeIdResolver extends TypeIdResolverBase {
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
    return typeFactory.constructType(mapFromValueType(valueType));
  }

  protected abstract Type mapFromValueType(final ValueType valueType);
}
