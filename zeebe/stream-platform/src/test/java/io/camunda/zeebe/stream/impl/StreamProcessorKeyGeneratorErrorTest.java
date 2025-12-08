/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.impl;

import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ACTIVATE_ELEMENT;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.stream.impl.state.DbKeyGenerator;
import io.camunda.zeebe.stream.util.RecordToWrite;
import io.camunda.zeebe.stream.util.Records;
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

final class StreamProcessorKeyGeneratorErrorTest {

  @RegisterExtension
  private final StreamPlatformExtension streamPlatformExtension = new StreamPlatformExtension();

  @SuppressWarnings("unused") // injected by the extension
  private StreamPlatform streamPlatform;

  private StreamProcessor streamProcessor;

  @Test
  void shouldShutdownIfKeyGenerationFailed() {
    // given
    streamProcessor = streamPlatform.startStreamProcessorNotAwaitOpening();
    final var mockedRecordProcessor = streamPlatform.getDefaultMockedRecordProcessor();

    final var zeebeDb = streamPlatform.getZeebeDb();
    final DbKeyGenerator dbKeyGenerator = new DbKeyGenerator(1, zeebeDb, zeebeDb.createContext());
    // set the key generator to a value that will cause an overflow on next key generation
    dbKeyGenerator.setKeyIfHigher(Protocol.encodePartitionId(1, (long) Math.pow(2, 51) - 1));
    when(mockedRecordProcessor.process(any(), any()))
        .thenAnswer(
            invocation -> {
              dbKeyGenerator.nextKey();
              return null;
            });

    // when - processing fails due to key generation error
    streamPlatform.writeBatch(
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)));

    // then
    Awaitility.await("wait to become unhealthy")
        .until(() -> streamProcessor.getHealthReport().isUnhealthy());

    Assertions.assertThat(streamProcessor.isFailed()).isTrue();
  }

  @AfterEach
  void cleanup() {
    streamPlatformExtension.afterEach();
  }
}
