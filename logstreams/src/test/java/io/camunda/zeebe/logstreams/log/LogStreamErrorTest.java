/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.logstreams.log;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import io.camunda.zeebe.logstreams.storage.LogStorage;
import io.camunda.zeebe.logstreams.util.SyncLogStream;
import io.camunda.zeebe.scheduler.testing.ActorSchedulerRule;
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class LogStreamErrorTest {

  @Parameterized.Parameter(0)
  public Throwable logStorageException;

  @Rule public ActorSchedulerRule actorSchedulerRule = new ActorSchedulerRule();
  final LogStorage mockLogStorage = mock(LogStorage.class);
  private SyncLogStream logStream;

  @Parameterized.Parameters
  public static Object[][] parameters() {
    return new Object[][] {
      {new RuntimeException("reader cannot be created")}, {new Error("reader cannot be created")}
    };
  }

  @Before
  public void setup() {
    doThrow(logStorageException).when(mockLogStorage).newReader();
    logStream =
        SyncLogStream.builder()
            .withLogName("test-log")
            .withLogStorage(mockLogStorage)
            .withActorSchedulingService(actorSchedulerRule.get())
            .build();
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
