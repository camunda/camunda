/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.logstreams.log;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.logstreams.storage.LogStorage;
import io.camunda.zeebe.logstreams.util.SyncLogStream;
import io.camunda.zeebe.util.sched.testing.ActorSchedulerRule;
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class LogStreamErrorTest {

  @Rule public ActorSchedulerRule actorSchedulerRule = new ActorSchedulerRule();
  final LogStorage mockLogStorage = mock(LogStorage.class);
  private SyncLogStream logStream;

  @Before
  public void setup() {
    logStream =
        SyncLogStream.builder()
            .withLogName("test-log")
            .withLogStorage(mockLogStorage)
            .withActorSchedulingService(actorSchedulerRule.get())
            .build();

    when(mockLogStorage.newReader()).thenThrow(new RuntimeException("reader cannot be created"));
  }

  @After
  public void after() {
    logStream.close();
  }

  @Test
  public void shouldCompleteFutureWhenCreateWriterFailed() {
    // given
    final var writerFuture = logStream.getAsyncLogStream().newLogStreamRecordWriter();

    // when
    Awaitility.await().until(writerFuture::isDone);

    // then
    assertThat(writerFuture.isCompletedExceptionally()).isTrue();
  }

  @Test
  public void shouldCompleteFutureWhenCreateBatchWriterFailed() {
    // given
    final var writerFuture = logStream.getAsyncLogStream().newLogStreamBatchWriter();

    // when
    Awaitility.await().until(writerFuture::isDone);

    // then
    assertThat(writerFuture.isCompletedExceptionally()).isTrue();
  }
}
