/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.logstreams.impl.log;

import static io.camunda.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.logstreams.storage.LogStorage;
import io.camunda.zeebe.logstreams.storage.LogStorageReader;
import io.camunda.zeebe.logstreams.util.TestEntry;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.testing.ActorSchedulerRule;
import io.camunda.zeebe.util.buffer.BufferWriter;
import io.camunda.zeebe.util.health.HealthStatus;
import java.util.concurrent.CountDownLatch;
import java.util.function.BiConsumer;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public final class LogStorageAppenderHealthTest {

  private static final int PARTITION_ID = 0;

  @Rule public final ActorSchedulerRule schedulerRule = new ActorSchedulerRule();

  private Sequencer sequencer;
  private ControllableLogStorage failingLogStorage;
  private LogStorageAppender appender;

  @Before
  public void setUp() {
    failingLogStorage = new ControllableLogStorage();
    sequencer = new Sequencer(0, 4 * 1024 * 1024, new SequencerMetrics(1));

    appender = new LogStorageAppender("appender", PARTITION_ID, failingLogStorage, sequencer);
  }

  @After
  public void tearDown() {
    sequencer.close();
    appender.close();
  }

  @Test
  public void shouldFailActorWhenWriteFails() {
    // given
    failingLogStorage.onNextAppend(
        (pos, listener) -> listener.onWriteError(new RuntimeException("foo")));

    // when
    sequencer.tryWrite(TestEntry.ofDefaults());
    schedulerRule.submitActor(appender).join();

    // then
    waitUntil(() -> appender.getHealthReport().isUnhealthy());
  }

  @Test
  public void shouldFailActorWhenCommitFails() {
    // given
    failingLogStorage.onNextAppend(
        (pos, listener) -> listener.onCommitError(pos, new RuntimeException("foo")));

    // when
    sequencer.tryWrite(TestEntry.ofDefaults());
    schedulerRule.submitActor(appender).join();

    // then
    waitUntil(() -> appender.getHealthReport().isUnhealthy());
  }

  @Test
  public void shouldBeHealthyWhenNoExceptions() throws InterruptedException {
    // given
    final CountDownLatch latch = new CountDownLatch(1);
    failingLogStorage.onNextAppend(
        (pos, listener) -> {
          listener.onWrite(pos);
          latch.countDown();
        });

    // when
    sequencer.tryWrite(TestEntry.ofDefaults());
    schedulerRule.submitActor(appender).join();

    // then
    latch.await();
    assertThat(latch.getCount()).isZero();
    assertThat(appender.getHealthReport().getStatus()).isEqualTo(HealthStatus.HEALTHY);
  }

  private class ControllableLogStorage extends Actor implements LogStorage {

    private BiConsumer<Long, AppendListener> onAppend = (pos, listener) -> listener.onWrite(pos);

    public ControllableLogStorage() {
      schedulerRule.submitActor(this).join();
    }

    void onNextAppend(final BiConsumer<Long, AppendListener> onAppend) {
      this.onAppend = onAppend;
    }

    @Override
    public LogStorageReader newReader() {
      return null;
    }

    @Override
    public void append(
        final long lowestPosition,
        final long highestPosition,
        final BufferWriter blockBuffer,
        final AppendListener listener) {
      actor.run(() -> onAppend.accept(highestPosition, listener));
    }

    @Override
    public void addCommitListener(final CommitListener listener) {
      throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void removeCommitListener(final CommitListener listener) {
      throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void close() {
      actor.close();
    }
  }
}
