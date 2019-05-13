/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.distributedlog.restore.log;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.zeebe.distributedlog.restore.log.impl.ControllableLogReplicationClient;
import io.zeebe.distributedlog.restore.log.impl.DefaultLogReplicationResponse;
import io.zeebe.distributedlog.restore.log.impl.RecordingLogReplicationAppender;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadLocalRandom;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.Test;

public class LogReplicatorTest {
  private final ControllableLogReplicationClient client = new ControllableLogReplicationClient();
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
    client.complete(-1, response);

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
    client.complete(-1, response);

    // then
    assertThat(result).isNotCompleted();
    assertThat(client.getRequests()).hasSize(2);
    assertThat(client.getRequests().get(response.getToPosition())).isNotNull().isNotCompleted();
  }

  @Test
  public void shouldNotReplicateAgainIfNoMoreAvailable() {
    // given
    final LogReplicationResponse response = newResponse(false, 2);

    // when
    final CompletableFuture<Long> result =
        replicator.replicate(server, -1, response.getToPosition() + 1);
    client.complete(-1, response);

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
    client.complete(-1, response[0]);

    // then
    assertThat(result).isNotCompleted();

    // when
    client.complete(response[0].getToPosition(), response[1]);

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
    client.complete(-1, response);

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
    client.complete(-1, error);

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
    client.complete(-1, response);

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
    client.complete(-1, response);

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
    client.complete(-1, response);

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
