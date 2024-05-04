/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.stream;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.gateway.impl.stream.StreamJobsHandler.AsyncJobStreamRemover;
import io.camunda.zeebe.protocol.impl.stream.job.JobActivationProperties;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.transport.stream.api.ClientStreamConsumer;
import io.camunda.zeebe.transport.stream.api.ClientStreamId;
import io.camunda.zeebe.transport.stream.api.ClientStreamer;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import org.agrona.DirectBuffer;
import org.junit.jupiter.api.Test;

final class AsyncJobStreamRemoverTest {
  private final TestJobStreamer jobStreamer = new TestJobStreamer();
  private final ClientStreamConsumer consumer = ignored -> CompletableActorFuture.completed(null);

  @Test
  void shouldRemoveStreamWhenIdIsSet() {
    // given - a stream observer which is cancelled before the stream is registered
    final var remover = new AsyncJobStreamRemover(jobStreamer, Runnable::run);
    remover.run();

    // when
    final var id = jobStreamer.add(BufferUtil.wrapString("foo"), null, consumer).join();
    remover.streamId(id);

    // then
    assertThat(jobStreamer.consumers).doesNotContainKey(id);
  }

  @Test
  void shouldRemoveStreamOnRun() {
    // given - a stream observer which is added before being cancelled
    final var remover = new AsyncJobStreamRemover(jobStreamer, Runnable::run);
    final var id = jobStreamer.add(BufferUtil.wrapString("foo"), null, consumer).join();
    remover.streamId(id);

    // when
    remover.run();

    // then
    assertThat(jobStreamer.consumers).doesNotContainKey(id);
  }

  private static final class TestJobStreamer implements ClientStreamer<JobActivationProperties> {
    private final Map<ClientStreamId, ClientStreamConsumer> consumers = new HashMap<>();

    @Override
    public ActorFuture<ClientStreamId> add(
        final DirectBuffer streamType,
        final JobActivationProperties metadata,
        final ClientStreamConsumer clientStreamConsumer) {
      final var id = new StreamId(streamType);
      consumers.put(id, clientStreamConsumer);
      return CompletableActorFuture.completed(id);
    }

    @Override
    public ActorFuture<Void> remove(final ClientStreamId streamId) {
      if (consumers.remove(streamId) == null) {
        return CompletableActorFuture.completedExceptionally(new NoSuchElementException());
      }

      return CompletableActorFuture.completed(null);
    }

    @Override
    public void close() {
      consumers.clear();
    }
  }

  private record StreamId(DirectBuffer type) implements ClientStreamId {}
}
