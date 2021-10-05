/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.protocol.jackson.record;

import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.intent.Intent;

/**
 * Provides mapping info between a value type and various constructs. Can be extended in the future
 * to include mapping between a base value class (as defined in the protocol module) and a concrete
 * implementation class (as generated here) for safe copying.
 *
 * @param <T> the immutable implementation class, e.g. {@link
 *     io.camunda.zeebe.protocol.jackson.record.VariableRecordValueBuilder.ImmutableVariableRecordValue}
 */
final class ValueTypeInfo<T extends RecordValue> {
  private final Class<T> valueClass;

  private final Class<? extends Intent> intentClass;

  ValueTypeInfo(final Class<T> valueClass, final Class<? extends Intent> intentClass) {
    this.valueClass = valueClass;
    this.intentClass = intentClass;
  }

  Class<T> getValueClass() {
    return valueClass;
  }

  Class<? extends Intent> getIntentClass() {
    return intentClass;
  }
}
