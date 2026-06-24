/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.logstreams.log;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.logstreams.util.LogStreamRule;
import io.camunda.zeebe.logstreams.util.TestEntry;
import io.camunda.zeebe.logstreams.util.TestLogStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public final class LogStreamTest {
  public static final int PARTITION_ID = 0;

  private final LogStreamRule logStreamRule = LogStreamRule.startByDefault();

  @Rule public RuleChain ruleChain = RuleChain.outerRule(logStreamRule);

  private TestLogStream logStream;

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
    assertThat(logStream.getLogName()).isEqualTo("logStream-0");

    assertThat(logStream.newLogStreamReader()).isNotNull();
    assertThat(logStream.newLogStreamWriter()).isNotNull();
  }

  @Test
  public void shouldCloseLogStream() {
    // given

    // when
    logStream.close();

    // then
    assertThatThrownBy(() -> logStream.newLogStreamWriter()).hasMessage("logStream-0 is closed");
  }

  @Test
  public void shouldIncreasePositionOnRestart() {
    // given
    final var writer = logStream.newBlockingLogStreamWriter();
    writer.tryWrite(WriteContext.internal(), TestEntry.ofDefaults());
    writer.tryWrite(WriteContext.internal(), TestEntry.ofDefaults());
    writer.tryWrite(WriteContext.internal(), TestEntry.ofDefaults());
    final long positionBeforeClose =
        writer.tryWrite(WriteContext.internal(), TestEntry.ofDefaults()).get();
    Awaitility.await("until everything is written")
        .until(logStream::getLastWrittenPosition, position -> position >= positionBeforeClose);

    // when
    logStream.close();
    logStreamRule.createLogStream();
    final var newWriter = logStreamRule.getLogStream().newLogStreamWriter();
    final long positionAfterReOpen =
        newWriter.tryWrite(WriteContext.internal(), TestEntry.ofDefaults()).get();

    // then
    assertThat(positionAfterReOpen).isGreaterThan(positionBeforeClose);
  }

  @Test
  public void shouldNotifyWhenNewRecordsAreAvailable() throws InterruptedException {
    // given
    final CountDownLatch latch = new CountDownLatch(1);
    logStream.registerRecordAvailableListener(latch::countDown);

    // when
    logStreamRule.getLogStreamWriter().tryWrite(WriteContext.internal(), TestEntry.ofDefaults());

    // then
    assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
  }

  @Test
  public void shouldNotifyMultipleListenersWhenNewRecordsAreAvailable()
      throws InterruptedException {
    // given
    final CountDownLatch firstListener = new CountDownLatch(1);
    logStream.registerRecordAvailableListener(firstListener::countDown);

    final CountDownLatch secondListener = new CountDownLatch(1);
    logStream.registerRecordAvailableListener(secondListener::countDown);

    // when
    logStreamRule.getLogStreamWriter().tryWrite(WriteContext.internal(), TestEntry.ofDefaults());

    // then
    assertThat(firstListener.await(2, TimeUnit.SECONDS)).isTrue();
    assertThat(secondListener.await(2, TimeUnit.SECONDS)).isTrue();
  }
}
