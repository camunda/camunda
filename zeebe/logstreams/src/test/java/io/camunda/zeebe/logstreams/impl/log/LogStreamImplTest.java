/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.logstreams.impl.log;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.logstreams.util.ListLogStorage;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.InstantSource;
import org.junit.jupiter.api.Test;

final class LogStreamImplTest {

  private LogStreamImpl newLogStream() {
    return (LogStreamImpl)
        new LogStreamBuilderImpl()
            .withLogStorage(new ListLogStorage())
            .withClock(InstantSource.system())
            .withMeterRegistry(new SimpleMeterRegistry())
            .build();
  }

  @Test
  void shouldStopTrackingAReaderOnceItIsClosed() {
    // given
    final var logStream = newLogStream();
    final var reader = logStream.newLogStreamReader();
    assertThat(logStream.openReaderCount()).isOne();

    // when
    reader.close();

    // then
    assertThat(logStream.openReaderCount()).isZero();
  }

  @Test
  void shouldStopTrackingManyReadersIndependently() {
    // given - a caller that opens and closes one reader per poll, as the upgrade-readiness
    // endpoint's exporting-migration-status check used to before this was fixed
    final var logStream = newLogStream();

    // when
    for (int i = 0; i < 1_000; i++) {
      logStream.newLogStreamReader().close();
    }

    // then - none of them are still tracked
    assertThat(logStream.openReaderCount()).isZero();
  }

  @Test
  void shouldStillCloseAReaderThatWasNeverExplicitlyClosed() {
    // given - LogStream#close() must still close out every reader still open when it happens,
    // regardless of whether other readers already deregistered themselves in the meantime
    final var logStream = newLogStream();
    final var stillOpen = logStream.newLogStreamReader();
    logStream.newLogStreamReader().close();
    assertThat(logStream.openReaderCount()).isOne();

    // when
    logStream.close();

    // then - closing the still-open reader again is safe (already exercised by LogStream#close())
    stillOpen.close();
  }
}
