/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record;

import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Because of how java static initializers work, this needs to be a separate class: the static block
 * uses {@link MsgpackRecordValue#fromValueType}, which must not run while {@link
 * MsgpackRecordValue} is still initializing.
 */
final class ClassToValueType {
  static final Map<Class<? extends RecordValue>, ValueType> MAP = new HashMap<>();

  static {
    Arrays.stream(ValueType.values())
        .forEach(
            v -> {
              final var record = MsgpackRecordValue.fromValueType(v);
              if (record != null) {
                MAP.put(record.getClass(), v);
              }
            });
  }
}
