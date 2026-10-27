/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.util.stream;

import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.CopiedRecord;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.stream.api.PostCommitTask;
import io.camunda.zeebe.stream.api.ProcessingResult;
import io.camunda.zeebe.stream.api.ProcessingResultBuilder;
import io.camunda.zeebe.util.Either;
import java.util.ArrayList;
import java.util.List;

/**
 * This processing result builder can be used in unit tests. It allows to capture the records and
 * the post commit tasks appended to it. Typically, used in combination with {@link
 * io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers} to tests behavior classes.
 *
 * @param <V> the type of the record value expected to be appended to this builder. This can be used
 *     to reduce the need for casting in tests if you know that only a specific value type is
 *     appended.
 */
public class FakeProcessingResultBuilder<V extends UnifiedRecordValue>
    implements ProcessingResultBuilder {

  private final List<Record<V>> followupRecords = new ArrayList<>();
  private final List<PostCommitTask> postCommitTasks = new ArrayList<>();

  /**
   * Flushes all post commit tasks appended to this builder. This is useful to simulate the
   * execution of the post commit tasks.
   */
  public void flushPostCommitTasks() {
    postCommitTasks.forEach(PostCommitTask::flush);
  }

  public List<Record<V>> getFollowupRecords() {
    return followupRecords;
  }

  public List<PostCommitTask> getPostCommitTasks() {
    return postCommitTasks;
  }

  @Override
  public Either<RuntimeException, ProcessingResultBuilder> appendRecordReturnEither(
      final long key, final RecordValue value, final RecordMetadata metadata) {
    final int partitionId = Protocol.decodePartitionId(key);
    final var copiedRecord = new CopiedRecord<>((V) value, metadata, key, partitionId, -1, -1, -1);
    followupRecords.add(copiedRecord.copyOf());
    return Either.right(this);
  }

  @Override
  public ProcessingResultBuilder withResponse(
      final RecordType type,
      final long key,
      final Intent intent,
      final UnpackedObject value,
      final ValueType valueType,
      final RejectionType rejectionType,
      final String rejectionReason,
      final long requestId,
      final int requestStreamId) {
    throw new UnsupportedOperationException("Responses are not yet supported by this fake");
  }

  @Override
  public ProcessingResultBuilder appendPostCommitTask(final PostCommitTask task) {
    postCommitTasks.add(task);
    return this;
  }

  @Override
  public ProcessingResultBuilder resetPostCommitTasks() {
    postCommitTasks.clear();
    return this;
  }

  @Override
  public ProcessingResult build() {
    throw new UnsupportedOperationException("Build is not yet supported by this fake");
  }

  @Override
  public boolean canWriteEventOfLength(final int eventLength) {
    return true;
  }
}
