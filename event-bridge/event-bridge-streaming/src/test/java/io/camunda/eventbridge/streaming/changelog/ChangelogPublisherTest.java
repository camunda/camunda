/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.changelog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.eventbridge.client.EventBridgeClient;
import io.camunda.eventbridge.client.EventBridgeClient.BatchPublisher;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

/**
 * A cut's changelog output: exactly the frozen delta's puts/tombstones, then the offset marker
 * strictly last (streaming ADR 0009 Decisions 1/2) — including the always-write-marker choice for
 * an otherwise empty cut.
 */
final class ChangelogPublisherTest {

  private static final String TOPIC = "analytics-stage2-changelog";
  private static final int PARTITION = 3;

  @Test
  void shouldPublishTheDeltaThenTheMarkerLast() {
    // given
    final EventBridgeClient client = mock(EventBridgeClient.class);
    final BatchPublisher batch = mock(BatchPublisher.class, RETURNS_SELF);
    when(client.newBatch()).thenReturn(batch);
    when(batch.publishToTopic(TOPIC, PARTITION))
        .thenReturn(CompletableFuture.completedFuture(List.of(10L, 13L)));
    final ChangelogPublisher publisher = new ChangelogPublisher(client, TOPIC, PARTITION);
    final byte[] putKey = {0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 5};
    final byte[] putValue = {7, 7};
    final byte[] tombstoneKey = {0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 6};

    // when
    final long position =
        publisher.publish(
            List.of(ChangelogRecord.put(putKey, putValue), ChangelogRecord.tombstone(tombstoneKey)),
            99L);

    // then — keyed, the delta in order, and the marker strictly last
    final InOrder order = Mockito.inOrder(batch);
    order.verify(batch).keyed();
    order.verify(batch).add(putKey, putValue);
    order.verify(batch).add(eq(tombstoneKey), eq(new byte[0]));
    order.verify(batch).add(eq(ChangelogMarker.KEY), any(byte[].class));
    order.verify(batch).publishToTopic(TOPIC, PARTITION);
    verify(batch, Mockito.times(3)).add(any(byte[].class), any(byte[].class));

    // and — the returned position is the batch's last position (the marker's assigned position)
    assertThat(position).isEqualTo(13L);
  }

  @Test
  void shouldAlwaysWriteTheMarkerEvenForAnEmptyCut() {
    // given — a cut that carried no state change (e.g. a filtered-only stretch) still advances
    // the source offset and must remain resumable from its own marker
    final EventBridgeClient client = mock(EventBridgeClient.class);
    final BatchPublisher batch = mock(BatchPublisher.class, RETURNS_SELF);
    when(client.newBatch()).thenReturn(batch);
    when(batch.publishToTopic(any(), anyInt()))
        .thenReturn(CompletableFuture.completedFuture(List.of(41L, 41L)));
    final ChangelogPublisher publisher = new ChangelogPublisher(client, TOPIC, PARTITION);

    // when
    final long position = publisher.publish(List.of(), 7L);

    // then — the marker alone is published, and the returned position is its assigned position
    verify(batch).add(eq(ChangelogMarker.KEY), any(byte[].class));
    verify(batch, Mockito.times(1)).add(any(byte[].class), any(byte[].class));
    assertThat(position).isEqualTo(41L);
  }

  @Test
  void shouldEncodeTheMarkerValueWithTheGivenSourceOffset() {
    // given
    final EventBridgeClient client = mock(EventBridgeClient.class);
    final BatchPublisher batch = mock(BatchPublisher.class, RETURNS_SELF);
    when(client.newBatch()).thenReturn(batch);
    when(batch.publishToTopic(any(), anyInt()))
        .thenReturn(CompletableFuture.completedFuture(List.of(1L, 1L)));
    final ChangelogPublisher publisher = new ChangelogPublisher(client, TOPIC, PARTITION);

    // when
    publisher.publish(List.of(), 12_345L);

    // then
    verify(batch).add(eq(ChangelogMarker.KEY), eq(ChangelogMarker.encodeValue(12_345L)));
  }
}
