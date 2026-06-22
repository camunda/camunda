/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.coordinator.stream;

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
 * Replicates consumer-group metadata on the coordinator partition. On {@link #process} (leader) it
 * applies the metadata to {@link DbGroupMetadataState} and emits a committed event; on {@link
 * #replay} (follower/recovery) it re-applies the event — so every replica rebuilds identical
 * membership and a new leader can restore the registry after failover.
 */
public final class GroupMetadataProcessor implements RecordProcessor {

  private final DbGroupMetadataState groupMetadataState;

  public GroupMetadataProcessor(final DbGroupMetadataState groupMetadataState) {
    this.groupMetadataState = groupMetadataState;
  }

  @Override
  public void init(final RecordProcessorContext recordProcessorContext) {
    // State is injected; nothing to initialize.
  }

  @Override
  public boolean accepts(final ValueType valueType) {
    return valueType == EventBridgeRecordValues.GROUP_METADATA_VALUE_TYPE;
  }

  @Override
  public void replay(final TypedRecord record) {
    final var event = (GroupMetadataRecord) record.getValue();
    groupMetadataState.put(event.getGroupId(), event.getPayload());
  }

  @Override
  public ProcessingResult process(
      final TypedRecord record, final ProcessingResultBuilder processingResultBuilder) {
    final var command = (GroupMetadataRecord) record.getValue();
    groupMetadataState.put(command.getGroupId(), command.getPayload());

    final var event =
        new GroupMetadataRecord().setGroupId(command.getGroupId()).setPayload(command.getPayload());
    final var metadata =
        new RecordMetadata()
            .recordType(RecordType.EVENT)
            .valueType(EventBridgeRecordValues.GROUP_METADATA_VALUE_TYPE)
            .intent(CoordinatorIntent.GROUP_METADATA_COMMITTED);

    processingResultBuilder.appendRecord(record.getKey(), event, metadata);
    return processingResultBuilder.build();
  }

  @Override
  public ProcessingResult onProcessingError(
      final Throwable processingException,
      final TypedRecord record,
      final ProcessingResultBuilder processingResultBuilder) {
    return EmptyProcessingResult.INSTANCE;
  }
}
