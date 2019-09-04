/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.log;

import static io.zeebe.test.util.TestUtil.waitUntil;
import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.logstreams.spi.LogStorage;
import io.zeebe.logstreams.util.LogStreamRule;
import java.io.IOException;
import java.nio.ByteBuffer;
import org.agrona.DirectBuffer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;

public class LogStreamTest {
  public static final int PARTITION_ID = 0;

  private final TemporaryFolder temporaryFolder = new TemporaryFolder();

  private final LogStreamRule logStreamRule =
      LogStreamRule.startByDefault(
          temporaryFolder,
          b -> {
            b.logStorageStubber(logStorage -> spy(logStorage));
          });

  @Rule public RuleChain ruleChain = RuleChain.outerRule(temporaryFolder).around(logStreamRule);

  private LogStream logStream;
  private LogStorage logStorageSpy;

  @Before
  public void setup() {
    logStream = logStreamRule.getLogStream();
    logStorageSpy = logStream.getLogStorage();
  }

  @Test
  public void shouldBuildLogStream() {
    // given

    // when

    // then
    assertThat(logStream.getPartitionId()).isEqualTo(PARTITION_ID);
    assertThat(logStream.getLogName()).isEqualTo("0");

    assertThat(logStream.getLogStorage()).isNotNull();
    assertThat(logStream.getLogStorage().isOpen()).isTrue();

    assertThat(logStream.getCommitPosition()).isEqualTo(-1L);

    assertThat(logStream.getLogStorageAppender()).isNotNull();
    assertThat(logStream.getWriteBuffer()).isNotNull();
  }

  @Test
  public void shouldCloseLogStorageAppender() {
    // given

    final Dispatcher writeBuffer = logStream.getWriteBuffer();

    // when
    logStream.closeAppender().join();

    // then
    assertThat(logStream.getLogStorageAppender()).isNull();
    assertThat(logStream.getWriteBuffer()).isNull();

    assertThat(writeBuffer.isClosed()).isTrue();
  }

  @Test
  public void shouldCloseLogStream() {
    // given
    final Dispatcher writeBuffer = logStream.getWriteBuffer();

    // when
    logStream.close();

    // then
    assertThat(logStream.getLogStorage().isClosed()).isTrue();
    assertThat(writeBuffer.isClosed()).isTrue();
  }

  @Test
  public void shouldSetCommitPosition() {
    // given

    // when
    logStream.append(123L, ByteBuffer.wrap("foo".getBytes()));

    // then
    assertThat(logStream.getCommitPosition()).isEqualTo(123L);
  }

  @Test
  public void shouldRetryOnAppend() throws Exception {
    // given
    doThrow(IOException.class).doCallRealMethod().when(logStorageSpy).append(any());

    // when
    logStream.append(123L, ByteBuffer.wrap("foo".getBytes()));

    // then
    assertThat(logStream.getCommitPosition()).isEqualTo(123L);
    verify(logStorageSpy, timeout(5_000).times(2)).append(any());
  }

  static long writeEvent(final LogStream logStream) {
    return writeEvent(logStream, wrapString("event"));
  }

  static long writeEvent(final LogStream logStream, DirectBuffer value) {
    final LogStreamWriterImpl writer = new LogStreamWriterImpl(logStream);

    long position = -1L;

    while (position < 0) {
      position = writer.value(value).tryWrite();
    }

    final long writtenEventPosition = position;
    waitUntil(() -> logStream.getCommitPosition() >= writtenEventPosition);

    return position;
  }
}
