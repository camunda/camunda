/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.log;

import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.dispatcher.Dispatchers;
import io.zeebe.dispatcher.Subscription;
import io.zeebe.logstreams.impl.LogStorageAppender;
import io.zeebe.logstreams.spi.LogStorage;
import io.zeebe.logstreams.spi.LogStorage.AppendListener;
import io.zeebe.test.util.TestUtil;
import io.zeebe.util.ByteValue;
import io.zeebe.util.sched.testing.ActorSchedulerRule;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicLong;
import org.agrona.DirectBuffer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class LogStorageAppenderTest {
  private static final ByteValue MAX_FRAGMENT_SIZE = ByteValue.ofMegabytes(4);

  @Rule public ActorSchedulerRule schedulerRule = new ActorSchedulerRule();

  private LogStorage logStorageMock;
  private LogStorageAppender appender;
  private LogStreamWriterImpl writer;

  @Before
  public void setup() {
    logStorageMock = mock(LogStorage.class);
    doAnswer(
            invocationOnMock -> {
              final AppendListener l = invocationOnMock.getArgument(3);
              l.onWrite(1);
              return null;
            })
        .when(logStorageMock)
        .append(anyLong(), anyLong(), any(), any());

    final Dispatcher dispatcher =
        Dispatchers.create("dispatcher")
            .maxFragmentLength(MAX_FRAGMENT_SIZE)
            .initialPartitionId(1)
            .actorScheduler(schedulerRule.get())
            .build();
    final Subscription subscription = dispatcher.openSubscription("subscription");
    appender =
        new LogStorageAppender(
            "appender", 1, logStorageMock, subscription, (int) MAX_FRAGMENT_SIZE.toBytes());

    final LogStream logStream = mock(LogStream.class);
    when(logStream.getPartitionId()).thenReturn(1);
    when(logStream.getWriteBuffer()).thenReturn(dispatcher);
    writer = new LogStreamWriterImpl(logStream);
  }

  public long writeEvent(final String message) {
    final AtomicLong writePosition = new AtomicLong();
    final DirectBuffer value = wrapString(message);

    TestUtil.doRepeatedly(() -> writer.value(value).tryWrite())
        .until(
            position -> {
              if (position != null && position >= 0) {
                writePosition.set(position);
                return true;
              } else {
                return false;
              }
            },
            "Failed to write event with message {}",
            message);
    return writePosition.get();
  }

  @Test
  public void shouldAppend() {
    // given
    schedulerRule.submitActor(appender).join();

    // when
    writeEvent("msg");

    // then
    verify(logStorageMock, timeout(1000))
        .append(anyLong(), anyLong(), any(ByteBuffer.class), any());
  }

  @Test
  public void shouldRetryOnError() {
    // given
    doAnswer(
            invocationOnMock -> {
              final AppendListener l = invocationOnMock.getArgument(3);
              l.onWriteError(new RuntimeException("Expected"));
              return null;
            })
        .doAnswer(
            invocationOnMock -> {
              final AppendListener l = invocationOnMock.getArgument(3);
              l.onWrite(1);
              return null;
            })
        .when(logStorageMock)
        .append(anyLong(), anyLong(), any(), any());

    schedulerRule.submitActor(appender).join();

    // when
    writeEvent("msg");

    // then
    verify(logStorageMock, timeout(1000).times(2))
        .append(anyLong(), anyLong(), any(ByteBuffer.class), any());
  }
  //
  //  @Test
  //  public void shouldAppendNextEventsAfterRetrySuccess() {
  //    // given
  //    final CompletableFuture firstAttempt = new CompletableFuture();
  //    final CompletableFuture secondAttempt = new CompletableFuture();
  //    final CompletableFuture thirdAttempt = new CompletableFuture();
  //
  //    when(primitiveMock.asyncAppend(anyLong(), any(byte[].class), anyLong()))
  //        .thenReturn(firstAttempt)
  //        .thenReturn(secondAttempt)
  //        .thenReturn(thirdAttempt);
  //    schedulerRule.submitActor(appender).join();
  //
  //    // when
  //    writeEvent("retried-msg");
  //    firstAttempt.complete(-1L);
  //    verify(primitiveMock, timeout(1000).times(2)).asyncAppend(eq(1L), any(byte[].class),
  // anyLong());
  //    writeEvent("blocked-msg");
  //    secondAttempt.complete(-1L);
  //    verify(primitiveMock, timeout(1000).times(3)).asyncAppend(eq(1L), any(byte[].class),
  // anyLong());
  //    thirdAttempt.complete(1L);
  //
  //    // then
  //    verify(primitiveMock, timeout(1000).times(1)).asyncAppend(eq(2L), any(byte[].class),
  // anyLong());
  //  }
}
