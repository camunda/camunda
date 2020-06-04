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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertNotNull;

import io.zeebe.logstreams.util.LogStreamRule;
import io.zeebe.logstreams.util.SynchronousLogStream;
import io.zeebe.test.util.TestUtil;
import io.zeebe.util.sched.future.ActorFuture;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.agrona.DirectBuffer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;

public final class LogStreamTest {
  public static final int PARTITION_ID = 0;

  private final TemporaryFolder temporaryFolder = new TemporaryFolder();

  private final LogStreamRule logStreamRule = LogStreamRule.startByDefault(temporaryFolder);

  @Rule public RuleChain ruleChain = RuleChain.outerRule(temporaryFolder).around(logStreamRule);

  private SynchronousLogStream logStream;

  @Before
  public void setup() {
    logStream = logStreamRule.getLogStream();
  }

  @Test
  public void shouldBuildLogStream() {
    // given

    // when

    // then
    assertThat(logStream.getPartitionId()).isEqualTo(PARTITION_ID);
    assertThat(logStream.getLogName()).isEqualTo("0");
    assertThat(logStream.getCommitPosition()).isEqualTo(-1L);

    assertThat(logStream.newLogStreamReader()).isNotNull();
    assertThat(logStream.newLogStreamBatchWriter()).isNotNull();
    assertThat(logStream.newLogStreamRecordWriter()).isNotNull();
  }

  @Test
  public void shouldCreateNewLogStreamRecordWriter() {
    // given
    final LogStreamRecordWriter logStreamRecordWriter = logStream.newLogStreamRecordWriter();

    // when
    final var otherWriter = logStream.newLogStreamRecordWriter();

    // then
    assertNotNull(logStreamRecordWriter);
    assertThat(otherWriter).isNotNull().isNotEqualTo(logStreamRecordWriter);
  }

  @Test
  public void shouldCreateNewLogStreamBatchWriter() {
    // given
    final var logStreamBatchWriter = logStream.newLogStreamBatchWriter();

    // when
    final var otherWriter = logStream.newLogStreamBatchWriter();

    // then
    assertNotNull(logStreamBatchWriter);
    assertThat(otherWriter).isNotNull().isNotEqualTo(logStreamBatchWriter);
  }

  @Test
  public void shouldCloseLogStream() {
    // given

    // when
    logStream.close();

    // then
    assertThatThrownBy(() -> logStream.newLogStreamRecordWriter()).hasMessage("Actor is closed");
    assertThatThrownBy(() -> logStream.newLogStreamBatchWriter()).hasMessage("Actor is closed");
  }

  @Test
  public void shouldIncreasePositionOnRestart()
      throws InterruptedException, ExecutionException, TimeoutException {
    // given
    final LogStreamRecordWriter writer = logStream.newLogStreamRecordWriter();
    writer.value(wrapString("value")).tryWrite();
    writer.value(wrapString("value")).tryWrite();
    writer.value(wrapString("value")).tryWrite();

    Optional<ActorFuture<Long>> optFuture = writer.value(wrapString("value")).tryWrite();
    assertThat(optFuture).isPresent();
    final long positionBeforeClose = optFuture.get().get(5, TimeUnit.SECONDS);
    TestUtil.waitUntil(() -> logStream.getCommitPosition() >= positionBeforeClose);

    // when
    logStream.close();
    logStreamRule.createLogStream();
    final LogStreamRecordWriter newWriter = logStreamRule.getLogStream().newLogStreamRecordWriter();

    optFuture = newWriter.value(wrapString("value")).tryWrite();
    assertThat(optFuture).isPresent();
    final long positionAfterReOpen = optFuture.get().get(5, TimeUnit.SECONDS);

    // then
    assertThat(positionAfterReOpen).isGreaterThan(positionBeforeClose);
  }

  static long writeEvent(final SynchronousLogStream logStream) {
    return writeEvent(logStream, wrapString("event"));
  }

  static long writeEvent(final SynchronousLogStream logStream, final DirectBuffer value) {
    final LogStreamRecordWriter writer = logStream.newLogStreamRecordWriter();

    long position = -1L;

    while (position < 0) {
      final Optional<ActorFuture<Long>> optFuture = writer.value(value).tryWrite();
      if (optFuture.isPresent()) {
        try {
          position = optFuture.get().get(5, TimeUnit.SECONDS);
        } catch (final Exception e) {
          e.printStackTrace();
        }
      }
    }

    final long writtenEventPosition = position;
    waitUntil(() -> logStream.getCommitPosition() >= writtenEventPosition);

    return position;
  }
}
