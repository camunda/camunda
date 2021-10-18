/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.streamprocessor;

import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.KeyGenerator;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import java.util.ArrayList;
import java.util.List;

public final class TypedRecordProcessors {

  private final RecordProcessorMap recordProcessorMap = new RecordProcessorMap();
  private final List<StreamProcessorLifecycleAware> lifecycleListeners = new ArrayList<>();
  private final KeyGenerator keyGenerator;
  private final Writers writers;

  private TypedRecordProcessors(final KeyGenerator keyGenerator, final Writers writers) {
    this.keyGenerator = keyGenerator;
    this.writers = writers;
  }

  public static TypedRecordProcessors processors(
      final KeyGenerator keyGenerator, final Writers writers) {
    return new TypedRecordProcessors(keyGenerator, writers);
  }

  public TypedRecordProcessors onCommand(
      final ValueType valueType, final Intent intent, final TypedRecordProcessor<?> processor) {
    recordProcessorMap.put(RecordType.COMMAND, valueType, intent.value(), processor);
    return this;
  }

  public <T extends UnifiedRecordValue> TypedRecordProcessors onCommand(
      final ValueType valueType, final Intent intent, final CommandProcessor<T> commandProcessor) {
    final var processor = new CommandProcessorImpl<>(commandProcessor, keyGenerator, writers);
    return onCommand(valueType, intent, processor);
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
