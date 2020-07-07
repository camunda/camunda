/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.impl.log;

import static io.zeebe.test.util.TestUtil.waitUntil;
import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.dispatcher.Dispatchers;
import io.zeebe.logstreams.spi.LogStorage;
import io.zeebe.logstreams.spi.LogStorageReader;
import io.zeebe.util.ByteValue;
import io.zeebe.util.health.HealthStatus;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.testing.ActorSchedulerRule;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.function.BiConsumer;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public final class LogStorageAppenderHealthTest {

  private static final int MAX_FRAGMENT_SIZE = 1024;
  private static final int PARTITION_ID = 0;

  @Rule public final ActorSchedulerRule schedulerRule = new ActorSchedulerRule();

  private Dispatcher dispatcher;
  private ControllableLogStorage failingLogStorage;
  private LogStorageAppender appender;
  private LogStreamWriterImpl writer;

  @Before
  public void setUp() {
    failingLogStorage = new ControllableLogStorage();

    dispatcher =
        Dispatchers.create("0")
            .actorScheduler(schedulerRule.get())
            .bufferSize((int) ByteValue.ofMegabytes(100 * MAX_FRAGMENT_SIZE))
            .maxFragmentLength(MAX_FRAGMENT_SIZE)
            .build();
    final var subscription = dispatcher.openSubscription("log");

    appender =
        new LogStorageAppender(
            "appender", PARTITION_ID, failingLogStorage, subscription, MAX_FRAGMENT_SIZE);
    writer = new LogStreamWriterImpl(PARTITION_ID, dispatcher);
  }

  @After
  public void tearDown() {
    appender.close();
    dispatcher.close();
  }

  @Test
  public void shouldFailActorWhenWriteFails() {
    // given
    failingLogStorage.onNextAppend(
        (pos, listener) -> listener.onWriteError(new RuntimeException("foo")));

    // when
    writer.value(wrapString("value")).tryWrite();
    schedulerRule.submitActor(appender).join();

    // then
    waitUntil(() -> appender.getHealthStatus() == HealthStatus.UNHEALTHY);
  }

  @Test
  public void shouldFailActorWhenCommitFails() {
    // given
    failingLogStorage.onNextAppend(
        (pos, listener) -> listener.onCommitError(pos, new RuntimeException("foo")));

    // when
    writer.value(wrapString("value")).tryWrite();
    schedulerRule.submitActor(appender).join();

    // then
    waitUntil(() -> appender.getHealthStatus() == HealthStatus.UNHEALTHY);
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
    writer.value(wrapString("value")).tryWrite();
    schedulerRule.submitActor(appender).join();

    // then
    latch.await();
    assertThat(latch.getCount()).isEqualTo(0);
    assertThat(appender.getHealthStatus()).isEqualTo(HealthStatus.HEALTHY);
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
        final ByteBuffer blockBuffer,
        final AppendListener listener) {
      actor.run(() -> onAppend.accept(highestPosition, listener));
    }

    @Override
    public void open() throws IOException {}

    @Override
    public boolean isOpen() {
      return false;
    }

    @Override
    public boolean isClosed() {
      return false;
    }

    @Override
    public void flush() throws Exception {}

    @Override
    public void close() {
      actor.close();
    }
  }
}
