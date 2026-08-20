/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.impl;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.logstreams.log.WriteContext;
import io.camunda.zeebe.logstreams.util.ListLogStorage;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.stream.util.RecordToWrite;
import io.camunda.zeebe.stream.util.Records;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(StreamPlatformExtension.class)
final class StreamProcessorInconsistentPositionTest {

  @SuppressWarnings("unused") // injected by the extension
  private StreamPlatform streamPlatform;

  @Test
  void shouldNotStartOnInconsistentLog() {
    // given
    final var listLogStorage = new ListLogStorage();
    try (final var firstLogCtx = streamPlatform.createLogContext(listLogStorage, 1)) {
      try (final var secondLogCtx = streamPlatform.createLogContext(listLogStorage, 2)) {
        final var firstWriter = firstLogCtx.setupWriter();
        final var secondWriter = secondLogCtx.setupWriter();

        // We write two record batches with different logstreams.
        // The logstreams are backed with the same logstorage, which means records are written to
        // the same backend. Both logstream will open a dispatcher with start at position one.

        firstWriter.tryWrite(
            WriteContext.internal(),
            List.of(
                RecordToWrite.command()
                    .processInstance(
                        ProcessInstanceIntent.ACTIVATE_ELEMENT, Records.processInstance(1)),
                RecordToWrite.command()
                    .processInstance(
                        ProcessInstanceIntent.ACTIVATE_ELEMENT, Records.processInstance(1))));
        secondWriter.tryWrite(
            WriteContext.internal(),
            List.of(
                RecordToWrite.command()
                    .processInstance(
                        ProcessInstanceIntent.ACTIVATE_ELEMENT, Records.processInstance(1)),
                RecordToWrite.command()
                    .processInstance(
                        ProcessInstanceIntent.ACTIVATE_ELEMENT, Records.processInstance(1))));
        // After writing we have at the logstorage: [1, 2, 1, 2], which should be detected

        // when
        streamPlatform.setLogContext(secondLogCtx);
        final var streamProcessor = streamPlatform.startStreamProcessorNotAwaitOpening();

        // then
        Awaitility.await("We expect that the opening fails")
            .untilAsserted(() -> assertThat(streamProcessor.isFailed()).isTrue());
      }
    }
  }
}
