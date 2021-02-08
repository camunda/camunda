/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.streamprocessor;

import io.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.zeebe.engine.state.KeyGenerator;
import io.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.zeebe.protocol.record.RecordType;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.intent.Intent;
import java.util.ArrayList;
import java.util.List;

public final class TypedRecordProcessors {

  private final RecordProcessorMap recordProcessorMap = new RecordProcessorMap();
  private final List<StreamProcessorLifecycleAware> lifecycleListeners = new ArrayList<>();
  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final TypedCommandWriter commandWriter;
  private final TypedRejectionWriter rejectionWriter;

  private TypedRecordProcessors(
      final KeyGenerator keyGenerator,
      final StateWriter stateWriter,
      final TypedCommandWriter commandWriter,
      final TypedRejectionWriter rejectionWriter) {
    this.keyGenerator = keyGenerator;
    this.stateWriter = stateWriter;
    this.commandWriter = commandWriter;
    this.rejectionWriter = rejectionWriter;
  }

  public static TypedRecordProcessors processors(
      final KeyGenerator keyGenerator,
      final StateWriter stateWriter,
      final TypedCommandWriter commandWriter,
      final TypedRejectionWriter rejectionWriter) {
    return new TypedRecordProcessors(keyGenerator, stateWriter, commandWriter, rejectionWriter);
  }

  // TODO: could remove the ValueType argument as it follows from the intent
  public TypedRecordProcessors onEvent(
      final ValueType valueType, final Intent intent, final TypedRecordProcessor<?> processor) {
    return onRecord(RecordType.EVENT, valueType, intent, processor);
  }

  private TypedRecordProcessors onRecord(
      final RecordType recordType,
      final ValueType valueType,
      final Intent intent,
      final TypedRecordProcessor<?> processor) {
    recordProcessorMap.put(recordType, valueType, intent.value(), processor);

    return this;
  }

  public TypedRecordProcessors onCommand(
      final ValueType valueType, final Intent intent, final TypedRecordProcessor<?> processor) {
    return onRecord(RecordType.COMMAND, valueType, intent, processor);
  }

  public <T extends UnifiedRecordValue> TypedRecordProcessors onCommand(
      final ValueType valueType, final Intent intent, final CommandProcessor<T> commandProcessor) {
    final var processor =
        new CommandProcessorImpl<>(
            commandProcessor, keyGenerator, stateWriter, commandWriter, rejectionWriter);
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
