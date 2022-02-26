/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.protocol.jackson;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import java.util.Objects;

/**
 * A {@link com.fasterxml.jackson.databind.jsontype.TypeIdResolver} that maps a serialized {@link
 * Intent} value to a concrete implementation enum, e.g. {@link
 * io.camunda.zeebe.protocol.record.intent.VariableIntent}, based on the value type of the record.
 */
final class IntentTypeIdResolver extends AbstractValueTypeIdResolver {
  @Override
  @NonNull
  protected Class<? extends Intent> mapFromValueType(@NonNull final ValueType valueType) {
    final ValueTypeInfo<?> typeInfo =
        ValueTypes.getTypeInfoOrNull(
            Objects.requireNonNull(valueType, "must specify a value type"));
    if (typeInfo == null) {
      return Intent.UNKNOWN.getClass();
    }

    return typeInfo.getIntentClass();
  }
}
