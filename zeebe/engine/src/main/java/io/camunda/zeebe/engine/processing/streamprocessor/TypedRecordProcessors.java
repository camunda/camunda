/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.streamprocessor;

import io.camunda.zeebe.engine.processing.storageordinals.StorageOrdinalAware;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.value.StorageOrdinalRelated;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;

public final class TypedRecordProcessors {

  private static final EnumSet<ValueType> STORAGE_ORDINAL_VALUE_TYPES =
      UnifiedRecordValue.allRecords()
          .filter(StorageOrdinalRelated.class::isInstance)
          .map(UnifiedRecordValue::valueType)
          .collect(Collectors.toCollection(() -> EnumSet.noneOf(ValueType.class)));

  private final RecordProcessorMap recordProcessorMap = new RecordProcessorMap();
  private final List<StreamProcessorLifecycleAware> lifecycleListeners = new ArrayList<>();

  private TypedRecordProcessors() {}

  public static TypedRecordProcessors processors() {
    return new TypedRecordProcessors();
  }

  /**
   * Registers the processor for the given command. A processor for a value type whose record
   * carries a storage ordinal must be {@link StorageOrdinalAware}, so a forgotten hook fails at
   * engine start instead of routing rejections to the main index.
   */
  public TypedRecordProcessors onCommand(
      final ValueType valueType, final Intent intent, final TypedRecordProcessor<?> processor) {
    if (STORAGE_ORDINAL_VALUE_TYPES.contains(valueType)
        && !(processor instanceof StorageOrdinalAware)) {
      throw new IllegalStateException(
          """
          Expected processor '%s' for '%s' commands to implement StorageOrdinalAware, \
          because '%s' carries a storage ordinal\
          """
              .formatted(
                  processor.getClass().getName(),
                  valueType,
                  UnifiedRecordValue.fromValueType(valueType).getClass().getSimpleName()));
    }
    recordProcessorMap.put(RecordType.COMMAND, valueType, intent.value(), processor);
    return this;
  }

  public TypedRecordProcessors withListener(final StreamProcessorLifecycleAware listener) {
    lifecycleListeners.add(listener);
    return this;
  }

  public RecordProcessorMap getRecordProcessorMap() {
    return recordProcessorMap;
  }

  public List<StreamProcessorLifecycleAware> getLifecycleListeners() {
    return lifecycleListeners;
  }
}
