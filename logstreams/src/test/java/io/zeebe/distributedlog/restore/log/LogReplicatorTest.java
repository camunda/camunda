/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.distributedlog.restore.log;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.zeebe.distributedlog.restore.impl.ControllableRestoreClient;
import io.zeebe.distributedlog.restore.log.impl.DefaultLogReplicationResponse;
import io.zeebe.distributedlog.restore.log.impl.RecordingLogReplicationAppender;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadLocalRandom;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.Test;

public class LogReplicatorTest {
  private final ControllableRestoreClient client = new ControllableRestoreClient();
  private final RecordingLogReplicationAppender appender = new RecordingLogReplicationAppender();
  private final Executor executor = Runnable::run;
  private final MemberId server = MemberId.anonymous();

  private final LogReplicator replicator = new LogReplicator(appender, client, executor);

  @Before
  public void setUp() {
    client.reset();
    appender.reset();
  }

  @Test
  public void shouldAppendEventsOnResponse() {
    // given
    final LogReplicationResponse response = newResponse(false, 1);

    // when
    replicator.replicate(server, -1, -1);
    client.completeLogReplication(-1, response);

    // then
    assertThat(appender.getInvocations())
        .hasSize(1)
        .first()
        .extracting("commitPosition", "serializedEvents")
        .contains(response.getToPosition(), response.getSerializedEvents());
  }

  @Test
  public void shouldReplicateAgain() {
    // given
    final LogReplicationResponse response = newResponse(true, 2);

    // when
    final CompletableFuture<Long> result =
        replicator.replicate(server, -1, response.getToPosition() + 1);
    client.completeLogReplication(-1, response);

    // then
    assertThat(result).isNotCompleted();
    assertThat(client.getLogReplicationRequests()).hasSize(2);
    assertThat(client.getLogReplicationRequests().get(response.getToPosition()))
        .isNotNull()
        .isNotCompleted();
  }

  @Test
  public void shouldExcludeFromPositionWhenReplicateAgain() {
    // given
    final LogReplicationResponse response = newResponse(true, 2);

    // when
    final CompletableFuture<Long> result =
        replicator.replicate(server, -1, response.getToPosition() + 1, true);
    client.completeLogReplication(-1, response);

    // then
    assertThat(client.getLogReplicationRequests()).hasSize(2);
    assertThat(client.getRequestLog().get(0).includeFromPosition()).isTrue();
    assertThat(client.getRequestLog().get(1).includeFromPosition()).isFalse();
  }

  @Test
  public void shouldNotReplicateAgainIfNoMoreAvailable() {
    // given
    final LogReplicationResponse response = newResponse(false, 2);

    // when
    final CompletableFuture<Long> result =
        replicator.replicate(server, -1, response.getToPosition() + 1);
    client.completeLogReplication(-1, response);

    // then
    assertThat(result).isCompletedWithValue(response.getToPosition());
    assertThat(appender.getInvocations()).hasSize(1);
  }

  @Test
  public void shouldNotReplicateAgainIfPositionReached() {
    // given
    // should complete the operation even if the server advertises more events are available if we
    // already reached our target position
    final LogReplicationResponse[] response =
        new LogReplicationResponse[] {newResponse(true, 2), newResponse(true, 4)};

    // when
    final CompletableFuture<Long> result =
        replicator.replicate(server, -1, response[1].getToPosition());
    client.completeLogReplication(-1, response[0]);

    // then
    assertThat(result).isNotCompleted();

    // when
    client.completeLogReplication(response[0].getToPosition(), response[1]);

    // then
    assertThat(result).isCompletedWithValue(response[1].getToPosition());
  }

  @Test
  public void shouldReplicateAgainIfMoreAvailable() {
    // given
    final LogReplicationResponse response = newResponse(false, 2);

    // when
    final CompletableFuture<Long> result =
        replicator.replicate(server, -1, response.getToPosition() + 1);
    client.completeLogReplication(-1, response);

    // then
    assertThat(result).isCompletedWithValue(response.getToPosition());
    assertThat(appender.getInvocations()).hasSize(1);
  }

  @Test
  public void shouldCompleteExceptionallyOnError() {
    // given
    final IllegalStateException error = new IllegalStateException("fail");

    // when
    final CompletableFuture<Long> result = replicator.replicate(server, -1, -1);
    client.completeLogReplication(-1, error);

    // then
    assertThat(appender.getInvocations()).isEmpty();
    assertThat(result)
        .isCompletedExceptionally()
        .hasFailedWithThrowableThat()
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("fail");
  }

  @Test
  public void shouldCompleteExceptionallyIfRequestIsInvalid() {
    // given
    final LogReplicationResponse response = newResponse(false, -1, new byte[0]);

    // when
    final CompletableFuture<Long> result = replicator.replicate(server, -1, -1);
    client.completeLogReplication(-1, response);

    // then
    assertThat(appender.getInvocations()).isEmpty();
    assertThat(result)
        .isCompletedExceptionally()
        .hasFailedWithThrowableThat()
        .isInstanceOf(InvalidLogReplicationResponse.class);
  }

  @Test
  public void shouldCompleteExceptionallyIfAppenderReturnsNegativeResult() {
    // given
    final LogReplicator replicator = new LogReplicator((p, b) -> -1, client, executor);
    final LogReplicationResponse response = newResponse(false);

    // when
    final CompletableFuture<Long> result = replicator.replicate(server, -1, -1);
    client.completeLogReplication(-1, response);

    // then
    assertThat(result)
        .isCompletedExceptionally()
        .hasFailedWithThrowableThat()
        .isInstanceOf(FailedAppendException.class);
  }

  @Test
  public void shouldCompleteExceptionallyIfAppenderThrowsAnException() {
    // given
    final RuntimeException error = new RuntimeException();
    final LogReplicator replicator =
        new LogReplicator(
            (p, b) -> {
              throw error;
            },
            client,
            executor);
    final LogReplicationResponse response = newResponse(false);

    // when
    final CompletableFuture<Long> result = replicator.replicate(server, -1, -1);
    client.completeLogReplication(-1, response);

    // then
    assertThat(result).isCompletedExceptionally().hasFailedWithThrowableThat().isEqualTo(error);
  }

  private LogReplicationResponse newResponse(boolean moreAvailable) {
    final long toPosition = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
    return newResponse(moreAvailable, toPosition);
  }

  private LogReplicationResponse newResponse(boolean moreAvailable, long toPosition) {
    final byte[] serializedEvents = new byte[1024];
    ThreadLocalRandom.current().nextBytes(serializedEvents);
    return newResponse(moreAvailable, toPosition, serializedEvents);
  }

  private LogReplicationResponse newResponse(
      boolean moreAvailable, long toPosition, byte[] serializedEvents) {
    final DefaultLogReplicationResponse response = new DefaultLogReplicationResponse();

    response.setMoreAvailable(moreAvailable);
    response.setSerializedEvents(new UnsafeBuffer(serializedEvents), 0, serializedEvents.length);
    response.setToPosition(toPosition);

    return response;
  }
}
