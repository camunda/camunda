/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.coordinator.stream;

import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.stream.api.EmptyProcessingResult;
import io.camunda.zeebe.stream.api.ProcessingResult;
import io.camunda.zeebe.stream.api.ProcessingResultBuilder;
import io.camunda.zeebe.stream.api.RecordProcessor;
import io.camunda.zeebe.stream.api.RecordProcessorContext;
import io.camunda.zeebe.stream.api.records.TypedRecord;

/**
 * Maintains the replicated topic registry on the coordinator partition. On {@link #process}
 * (leader) it applies the mutation to {@link DbTopicState} and emits a committed event; on {@link
 * #replay} (follower/recovery) it re-applies it — so every replica holds the same topic set and a
 * new leader restores it after failover. Register vs delete is read from the record's own {@code
 * op} field (see {@link TopicRecord}), not the record intent.
 */
public final class TopicProcessor implements RecordProcessor {

  private final DbTopicState topicState;

  public TopicProcessor(final DbTopicState topicState) {
    this.topicState = topicState;
  }

  @Override
  public void init(final RecordProcessorContext recordProcessorContext) {
    // State is injected; nothing to initialize.
  }

  @Override
  public boolean accepts(final ValueType valueType) {
    return valueType == EventBridgeRecordValues.TOPIC_VALUE_TYPE;
  }

  @Override
  public void replay(final TypedRecord record) {
    apply((TopicRecord) record.getValue());
  }

  @Override
  public ProcessingResult process(
      final TypedRecord record, final ProcessingResultBuilder processingResultBuilder) {
    final var command = (TopicRecord) record.getValue();
    apply(command);

    final var event =
        new TopicRecord()
            .setName(command.getName())
            .setOp(command.getOp())
            .setPartitionCount(command.getPartitionCount())
            .setReplicationFactor(command.getReplicationFactor())
            .setStatus(TopicMetadata.TopicStatus.valueOf(command.getStatus()));
    final var metadata =
        new RecordMetadata()
            .recordType(RecordType.EVENT)
            .valueType(EventBridgeRecordValues.TOPIC_VALUE_TYPE)
            .intent(
                command.isDelete()
                    ? CoordinatorIntent.TOPIC_DELETED
                    : CoordinatorIntent.TOPIC_REGISTERED);

    processingResultBuilder.appendRecord(record.getKey(), event, metadata);
    return processingResultBuilder.build();
  }

  private void apply(final TopicRecord record) {
    if (record.isDelete()) {
      topicState.delete(record.getName());
    } else {
      topicState.put(record.getName(), record.toMetadata());
    }
  }

  @Override
  public ProcessingResult onProcessingError(
      final Throwable processingException,
      final TypedRecord record,
      final ProcessingResultBuilder processingResultBuilder) {
    return EmptyProcessingResult.INSTANCE;
  }
}
