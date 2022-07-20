/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.streamprocessor;

import static io.camunda.zeebe.engine.processing.streamprocessor.TypedEventRegistry.EVENT_REGISTRY;

import io.camunda.zeebe.engine.api.PostCommitTask;
import io.camunda.zeebe.engine.api.ProcessingResult;
import io.camunda.zeebe.engine.api.ProcessingResultBuilder;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BinaryOperator;

/** Implementation of {@code ProcessingResultBuilder} that writes all data into a buffer */
final class BufferedProcessingResultBuilder implements ProcessingResultBuilder {

  private final Map<Class<? extends UnpackedObject>, ValueType> typeRegistry;

  private final BufferedStreamWriter bufferedStreamWriter;
  private final List<PostCommitTask> postCommitTasks = new ArrayList<>();
  private final int sourceIndex;

  BufferedProcessingResultBuilder(
      final BinaryOperator<Integer> capacityCalculator,
      final int maxEventLength,
      final int sourceIndex) {
    bufferedStreamWriter = new BufferedStreamWriter(capacityCalculator, maxEventLength);
    this.sourceIndex = sourceIndex;

    typeRegistry = new HashMap<>();
    EVENT_REGISTRY.forEach((e, c) -> typeRegistry.put(c, e));
  }

  @Override
  public ProcessingResultBuilder appendRecord(
      final long key,
      final RecordType type,
      final Intent intent,
      final RejectionType rejectionType,
      final String rejectionReason,
      final RecordValue value) {

    final ValueType valueType = initValueType(value);
    final var valueWriter = initValueWriter(value);

    /* TODO this is probably wrong. There is sourceIndex (int) and there is
    sourceEventPosition (long), so confusing the two is definitely wrong.
    This needs to be investigated further as setSourceIndex(...) only seems to called
    in a test; so maybe defaulting it to -1 is the way to go; but then there is also
    sourceEventPosition which the batch writer writes into the log at some point, but
    this class doesn't yet
    */

    bufferedStreamWriter.appendRecord(
        key, sourceIndex, type, intent, rejectionType, rejectionReason, valueType, valueWriter);
    return this;
  }

  @Override
  public ProcessingResultBuilder withResponse(
      final long eventKey,
      final Intent eventState,
      final UnpackedObject eventValue,
      final ValueType valueType,
      final long requestId,
      final int requestStreamId) {
    throw new RuntimeException("Not yet implemented");
  }

  @Override
  public ProcessingResultBuilder appendPostCommitTask(final PostCommitTask r) {
    postCommitTasks.add(r);
    return this;
  }

  @Override
  public ProcessingResultBuilder reset() {
    bufferedStreamWriter.reset();
    postCommitTasks.clear();
    return this;
  }

  @Override
  public ProcessingResultBuilder resetPostCommitTasks() {
    postCommitTasks.clear();
    return this;
  }

  @Override
  public ProcessingResult build() {
    throw new RuntimeException("Not yet implemented");
  }

  @Override
  public boolean canWriteEventOfLength(final int eventLength) {
    return bufferedStreamWriter.canWriteAdditionalEvent(eventLength);
  }

  @Override
  public int getMaxEventLength() {
    return bufferedStreamWriter.getMaxEventLength();
  }

  private ValueType initValueType(final RecordValue value) {
    final ValueType valueType = typeRegistry.get(value.getClass());
    if (valueType == null) {
      // usually happens when the record is not registered at the TypedStreamEnvironment
      throw new IllegalArgumentException(
          "Missing value type mapping for record: " + value.getClass());
    }
    return valueType;
  }

  private BufferWriter initValueWriter(final RecordValue value) {
    // TODO evaluate whether the interface should be changed to UnifiedRecordValue or <T extends
    // RecordValue & BufferWriter> BufferWriter initValueWriter(final T value) {}
    // validation
    if (!(value instanceof BufferWriter)) {
      throw new IllegalArgumentException(
          String.format("The record value %s is not a BufferWriter", value));
    }

    return (BufferWriter) value;
  }
}
