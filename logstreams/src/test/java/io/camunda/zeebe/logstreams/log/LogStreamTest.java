/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.logstreams.log;

import static io.camunda.zeebe.test.util.TestUtil.waitUntil;
import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertNotNull;

import io.camunda.zeebe.logstreams.util.LogStreamRule;
import io.camunda.zeebe.logstreams.util.SynchronousLogStream;
import io.camunda.zeebe.test.util.TestUtil;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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
  public void shouldIncreasePositionOnRestart() {
    // given
    final LogStreamRecordWriter writer = logStream.newLogStreamRecordWriter();
    writer.value(wrapString("value")).tryWrite();
    writer.value(wrapString("value")).tryWrite();
    writer.value(wrapString("value")).tryWrite();
    final long positionBeforeClose = writer.value(wrapString("value")).tryWrite();
    TestUtil.waitUntil(() -> logStream.getLastWrittenPosition() >= positionBeforeClose);

    // when
    logStream.close();
    logStreamRule.createLogStream();
    final LogStreamRecordWriter newWriter = logStreamRule.getLogStream().newLogStreamRecordWriter();
    final long positionAfterReOpen = newWriter.value(wrapString("value")).tryWrite();

    // then
    assertThat(positionAfterReOpen).isGreaterThan(positionBeforeClose);
  }

  @Test
  public void shouldNotifyWhenNewRecordsAreAvailable() throws InterruptedException {
    // given
    final CountDownLatch latch = new CountDownLatch(1);
    logStream.getAsyncLogStream().registerRecordAvailableListener(() -> latch.countDown());

    // when
    writeEvent(logStream);

    // then
    assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
  }

  @Test
  public void shouldNotifyMultipleListenersWhenNewRecordsAreAvailable()
      throws InterruptedException {
    // given
    final CountDownLatch firstListener = new CountDownLatch(1);
    logStream.getAsyncLogStream().registerRecordAvailableListener(() -> firstListener.countDown());

    final CountDownLatch secondListener = new CountDownLatch(1);
    logStream.getAsyncLogStream().registerRecordAvailableListener(() -> secondListener.countDown());

    // when
    writeEvent(logStream);

    // then
    assertThat(firstListener.await(2, TimeUnit.SECONDS)).isTrue();
    assertThat(secondListener.await(2, TimeUnit.SECONDS)).isTrue();
  }

  static long writeEvent(final SynchronousLogStream logStream) {
    return writeEvent(logStream, wrapString("event"));
  }

  static long writeEvent(final SynchronousLogStream logStream, final DirectBuffer value) {
    final LogStreamRecordWriter writer = logStream.newLogStreamRecordWriter();

    long position = -1L;

    while (position < 0) {
      position = writer.value(value).tryWrite();
    }

    final long writtenEventPosition = position;
    waitUntil(() -> logStream.getLastWrittenPosition() >= writtenEventPosition);

    return position;
  }
}
