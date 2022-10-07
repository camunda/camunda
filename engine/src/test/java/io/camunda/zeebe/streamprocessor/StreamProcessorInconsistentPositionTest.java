/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.streamprocessor;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.engine.util.Records;
import io.camunda.zeebe.logstreams.util.ListLogStorage;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(StreamPlatformExtension.class)
public final class StreamProcessorInconsistentPositionTest {

  private static final ProcessInstanceRecord PROCESS_INSTANCE_RECORD = Records.processInstance(1);

  @SuppressWarnings("unused") // injected by the extension
  private StreamPlatform streamPlatform;

  @Test
  public void shouldNotStartOnInconsistentLog() {
    // given
    final var listLogStorage = new ListLogStorage();
    try (final var firstLogCtx = streamPlatform.createLogStream(listLogStorage, 1)) {
      try (final var secondLogCtx = streamPlatform.createLogStream(listLogStorage, 2)) {
        final var firstBatchWriter = firstLogCtx.setupBatchWriter(RecordToWrite.command()
                .processInstance(ProcessInstanceIntent.ACTIVATE_ELEMENT, PROCESS_INSTANCE_RECORD),
            RecordToWrite.command()
                .processInstance(ProcessInstanceIntent.ACTIVATE_ELEMENT, PROCESS_INSTANCE_RECORD));
        final var secondBatchWriter = secondLogCtx.setupBatchWriter(RecordToWrite.command()
                .processInstance(ProcessInstanceIntent.ACTIVATE_ELEMENT, PROCESS_INSTANCE_RECORD),
            RecordToWrite.command()
                .processInstance(ProcessInstanceIntent.ACTIVATE_ELEMENT, PROCESS_INSTANCE_RECORD));

        // We write two record batches with different logstreams.
        // The logstreams are backed with the same logstorage, which means records are written to
        // the same backend. Both logstream will open a dispatcher with start at position one.

        streamPlatform.writeBatch(firstBatchWriter);
        streamPlatform.writeBatch(secondBatchWriter);
        // After writing we have at the logstorage: [1, 2, 1, 2], which should be detected

        // when
        final var streamProcessor = streamPlatform.startStreamProcessorNotAwaitOpening();

        // then
        Awaitility.await("We expect that the opening fails").untilAsserted(() -> assertThat(streamProcessor.isFailed()).isTrue());

      }
    }
  }
}
