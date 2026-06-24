/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.stream;

import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.stream.api.ProcessingResultBuilder;
import java.util.function.Supplier;

/**
 * Writes follow-up <em>commands</em> from a processor — the event-bridge counterpart of the
 * engine's {@code TypedCommandWriter}. The command is staged on the current result builder, so the
 * platform writes it to the log and processes it next (and replays it on followers). This is how a
 * processor schedules further work, e.g. a {@code JOIN} appending a {@code REBALANCE} command. The
 * value supplies its own {@code valueType} (see each record's {@code valueType()} override).
 */
public final class CommandWriter {

  private final Supplier<ProcessingResultBuilder> resultBuilder;

  CommandWriter(final Supplier<ProcessingResultBuilder> resultBuilder) {
    this.resultBuilder = resultBuilder;
  }

  public void appendFollowUpCommand(
      final long key, final Intent intent, final UnifiedRecordValue value) {
    final var metadata = new RecordMetadata().recordType(RecordType.COMMAND).intent(intent);
    resultBuilder.get().appendRecord(key, value, metadata);
  }
}
