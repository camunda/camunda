/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.transport.stream.impl;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.transport.stream.api.ClientStreamConsumer;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.UUID;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

class AggregatedClientStreamTest {
  private static final ClientStreamConsumer CLIENT_STREAM_CONSUMER =
      p -> CompletableActorFuture.completed(null);

  private final UnsafeBuffer streamType = new UnsafeBuffer(BufferUtil.wrapString("foo"));
  private final TestSerializableData metadata = new TestSerializableData(1234);
  private final TestClientStreamMetrics metrics = new TestClientStreamMetrics();
  private final AggregatedClientStream<TestSerializableData> stream =
      new AggregatedClientStream<>(
          UUID.randomUUID(), new LogicalId<>(streamType, metadata), metrics);

  @Test
  void shouldReportStreamCountOnAdd() {
    // given

    // when
    addClient(getNextStreamId());
    addClient(getNextStreamId());

    // then
    assertThat(metrics.getAggregatedClientCountObservations()).containsExactly(1, 2);
  }

  @Test
  void shouldReportStreamCountOnRemove() {
    // given
    final var streamId = getNextStreamId();
    stream.addClient(
        new ClientStreamImpl<>(streamId, stream, streamType, metadata, CLIENT_STREAM_CONSUMER));

    // when
    stream.removeClient(streamId);

    // then
    assertThat(metrics.getAggregatedClientCountObservations()).containsExactly(1, 0);
  }

  private ClientStreamIdImpl getNextStreamId() {
    return new ClientStreamIdImpl(stream.streamId(), stream.nextLocalId());
  }

  private void addClient(final ClientStreamIdImpl streamId) {
    stream.addClient(
        new ClientStreamImpl<>(
            streamId,
            stream,
            streamType,
            metadata,
            AggregatedClientStreamTest.CLIENT_STREAM_CONSUMER));
  }
}
