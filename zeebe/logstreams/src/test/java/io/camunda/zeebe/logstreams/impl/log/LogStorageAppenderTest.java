/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.logstreams.impl.log;

import static io.camunda.zeebe.logstreams.util.TestEntry.TestEntryAssert.assertThatEntry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import io.camunda.zeebe.logstreams.impl.LogStreamMetrics;
import io.camunda.zeebe.logstreams.impl.flowcontrol.FlowControl;
import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.logstreams.log.LogStreamReader;
import io.camunda.zeebe.logstreams.log.WriteContext;
import io.camunda.zeebe.logstreams.storage.LogStorage.AppendListener;
import io.camunda.zeebe.logstreams.util.ListLogStorage;
import io.camunda.zeebe.logstreams.util.TestEntry;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.ByteBuffer;
import java.time.InstantSource;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.agrona.CloseHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.CONCURRENT)
final class LogStorageAppenderTest {

  private static final int PARTITION_ID = 0;
  private static final long INITIAL_POSITION = 2L;

  private final ActorScheduler scheduler =
      ActorScheduler.newActorScheduler()
          .setCpuBoundActorThreadCount(1)
          .setIoBoundActorThreadCount(1)
          .build();

  private final ListLogStorage logStorage = spy(new ListLogStorage());

  private Sequencer sequencer;
  private LogStreamReader reader;

  @BeforeEach
  void beforeEach() {
    final var meterRegistry = new SimpleMeterRegistry();
    final var logStreamMetrics = new LogStreamMetrics(meterRegistry);
    scheduler.start();
    sequencer =
        new Sequencer(
            logStorage,
            INITIAL_POSITION,
            4 * 1024 * 1024,
            InstantSource.system(),
            new SequencerMetrics(meterRegistry),
            new FlowControl(logStreamMetrics));
    reader = new LogStreamReaderImpl(logStorage.newReader());
  }

  @AfterEach
  public void tearDown() {
    CloseHelper.quietCloseAll(sequencer);
  }

  @Test
  void shouldAppendSingleEvent() throws InterruptedException {
    // given
    final var latch = new CountDownLatch(1);
    final var entry = TestEntry.ofDefaults();
    // when
    logStorage.setPositionListener(i -> latch.countDown());
    final var position = sequencer.tryWrite(WriteContext.internal(), entry).get();

    // then
    assertThat(latch.await(5, TimeUnit.SECONDS)).as("value was written within 5 seconds").isTrue();
    assertThat(reader.seek(position)).isTrue();
    assertThat(reader.hasNext()).isTrue();
    assertThatEntry(entry).matchesLoggedEvent(reader.next());
  }

  @Test
  void shouldAppendMultipleEvents() throws InterruptedException {
    // given
    final List<LogAppendEntry> entries =
        IntStream.range(0, 2).mapToObj(i -> TestEntry.ofDefaults()).collect(Collectors.toList());
    final var latch = new CountDownLatch(1);

    // when
    logStorage.setPositionListener(i -> latch.countDown());
    final var highestPosition = sequencer.tryWrite(WriteContext.internal(), entries).get();
    final var lowestPosition = highestPosition - 1;

    // then
    assertThat(latch.await(5, TimeUnit.SECONDS)).as("value was written within 5 seconds").isTrue();

    // make sure we read the correct lowest/highest positions
    verify(logStorage, timeout(1000).times(1))
        .append(
            eq(lowestPosition),
            eq(highestPosition),
            any(ByteBuffer.class),
            any(AppendListener.class));

    // ensure events were written properly
    assertThat(reader.seek(lowestPosition)).isTrue();
    for (final var entry : entries) {
      assertThat(reader.hasNext()).isTrue();
      assertThatEntry(entry).matchesLoggedEvent(reader.next());
    }
  }
}
