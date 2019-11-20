/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams;

import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.dispatcher.Dispatchers;
import io.zeebe.dispatcher.Subscription;
import io.zeebe.distributedlog.impl.DistributedLogstreamPartition;
import io.zeebe.logstreams.impl.LogStorageAppender;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LogStreamWriterImpl;
import io.zeebe.protocol.impl.record.RecordMetadata;
import io.zeebe.test.util.TestUtil;
import io.zeebe.util.ByteValue;
import io.zeebe.util.exception.UncheckedExecutionException;
import io.zeebe.util.sched.testing.ActorSchedulerRule;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import org.agrona.DirectBuffer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

public class LogStorageAppenderTest {
  private static ByteValue maxFragmentSize = ByteValue.ofMegabytes(4);

  @Rule public ActorSchedulerRule schedulerRule = new ActorSchedulerRule();

  private DistributedLogstreamPartition primitiveMock;
  private LogStorageAppender appender;
  private LogStreamWriterImpl writer;

  @Before
  public void setup() {
    primitiveMock = mock(DistributedLogstreamPartition.class);

    when(primitiveMock.getLastAppendIndex()).thenReturn(CompletableFuture.completedFuture(0L));
    when(primitiveMock.asyncAppend(anyLong(), any(byte[].class), anyLong()))
        .thenReturn(CompletableFuture.completedFuture(1L));

    final Dispatcher dispatcher =
        Dispatchers.create("dispatcher")
            .maxFragmentLength(maxFragmentSize)
            .initialPartitionId(1)
            .actorScheduler(schedulerRule.get())
            .build();
    final Subscription subscription = dispatcher.openSubscription("subscription");
    appender =
        new LogStorageAppender(
            "appender", primitiveMock, subscription, (int) maxFragmentSize.toBytes());

    final LogStream logStream = mock(LogStream.class);
    when(logStream.getPartitionId()).thenReturn(1);
    when(logStream.getWriteBuffer()).thenReturn(dispatcher);
    writer = new LogStreamWriterImpl(logStream);
  }

  public long writeEvent(final String message) {
    final AtomicLong writePosition = new AtomicLong();
    final RecordMetadata metadata = new RecordMetadata();
    final DirectBuffer value = wrapString(message);

    TestUtil.doRepeatedly(
            () -> writer.key(-1).metadataWriter(metadata.reset()).value(value).tryWrite())
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
    verify(primitiveMock, timeout(1000)).asyncAppend(eq(1L), any(byte[].class), anyLong());
  }

  @Test
  public void shouldAppendMultipleIncrementIndex() {
    // given
    schedulerRule.submitActor(appender).join();
    writeEvent("msg");

    // when
    writeEvent("msg");

    // then
    final InOrder inOrder = Mockito.inOrder(primitiveMock);
    inOrder.verify(primitiveMock, timeout(1000)).asyncAppend(eq(1L), any(byte[].class), anyLong());
    inOrder.verify(primitiveMock, timeout(1000)).asyncAppend(eq(2L), any(byte[].class), anyLong());
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void shouldStartWithIndex() {
    // given
    when(primitiveMock.getLastAppendIndex()).thenReturn(CompletableFuture.completedFuture(12L));
    schedulerRule.submitActor(appender).join();

    // when
    writeEvent("msg");

    // then
    verify(primitiveMock, timeout(1000)).asyncAppend(eq(13L), any(byte[].class), anyLong());
  }

  @Test
  public void shouldFailToStartOnRequestLastIndexError() {
    // given
    when(primitiveMock.getLastAppendIndex())
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("failed")));

    // when - expect exception
    assertThatThrownBy(() -> schedulerRule.submitActor(appender).join())
        .isInstanceOf(ExecutionException.class)
        .hasCauseInstanceOf(UncheckedExecutionException.class);
  }

  @Test
  public void shouldRetryOnError() {
    // given
    when(primitiveMock.asyncAppend(anyLong(), any(byte[].class), anyLong()))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Failed to append!")))
        .thenReturn(CompletableFuture.completedFuture(1L));
    schedulerRule.submitActor(appender).join();

    // when
    writeEvent("msg");

    // then
    verify(primitiveMock, timeout(1000).times(2)).asyncAppend(eq(1L), any(byte[].class), anyLong());
  }

  @Test
  public void shouldRetryOnFailedAppend() {
    // given
    when(primitiveMock.asyncAppend(anyLong(), any(byte[].class), anyLong()))
        .thenReturn(CompletableFuture.completedFuture(-1L))
        .thenReturn(CompletableFuture.completedFuture(1L));
    schedulerRule.submitActor(appender).join();

    // when
    writeEvent("msg");

    // then
    verify(primitiveMock, timeout(1000).times(2)).asyncAppend(eq(1L), any(byte[].class), anyLong());
  }

  @Test
  public void shouldRetryOnBothErrorTypes() {
    // given
    when(primitiveMock.asyncAppend(anyLong(), any(byte[].class), anyLong()))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Failed to append!")))
        .thenReturn(CompletableFuture.completedFuture(-1L))
        .thenReturn(CompletableFuture.completedFuture(1L));

    schedulerRule.submitActor(appender).join();

    // when
    writeEvent("msg");

    // then
    verify(primitiveMock, timeout(1000).times(3)).asyncAppend(eq(1L), any(byte[].class), anyLong());
  }
}
