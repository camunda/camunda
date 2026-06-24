/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.clustermetadata.stream;

import io.camunda.eventbridge.stream.TypedRecordProcessor;
import io.camunda.eventbridge.stream.Writers;
import io.camunda.zeebe.stream.api.records.TypedRecord;

/**
 * Handles the {@code DELETE_TOPIC} command: turns it into a {@code TOPIC_DELETED} follow-up event,
 * which {@link TopicDeletedApplier} applies to the replicated registry. Holds no state.
 */
final class TopicDeleteProcessor implements TypedRecordProcessor<TopicRecord> {

  private final Writers writers;

  TopicDeleteProcessor(final Writers writers) {
    this.writers = writers;
  }

  @Override
  public void processRecord(final TypedRecord<TopicRecord> command) {
    final var cmd = command.getValue();
    final var event = new TopicRecord().setName(cmd.getName()).setOp(cmd.getOp());
    writers.state().appendFollowUpEvent(command.getKey(), MetadataIntent.TOPIC_DELETED, event);
  }
}
