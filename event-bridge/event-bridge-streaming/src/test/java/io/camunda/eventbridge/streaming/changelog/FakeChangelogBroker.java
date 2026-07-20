/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.changelog;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.eventbridge.batch.BatchBuilder;
import io.camunda.eventbridge.batch.BatchFormat;
import io.camunda.eventbridge.client.EventBridgeClient;
import io.camunda.eventbridge.client.EventBridgeClient.BatchPublisher;
import io.camunda.eventbridge.client.FetchResult;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * A single-partition, in-memory stand-in for a compacted changelog topic: a real {@link
 * EventBridgeClient} mock whose {@code newBatch()}/{@code fetchFromTopic} are backed by an
 * append-only buffer of real on-wire batches (built with the same {@link BatchBuilder}/{@link
 * BatchFormat} the production client and broker use), so a {@link ChangelogPublisher} publishing
 * against it and a {@link ChangelogApplier} fetching from it exercise the exact wire format — only
 * the transport (HTTP) is faked, not the framing. Mirrors the mocking style already used by {@code
 * ChangelogPublisherTest}, extended to also serve fetches from the same backing log.
 */
final class FakeChangelogBroker {

  private final ByteArrayOutputStream log = new ByteArrayOutputStream();
  private long entryCount;
  final EventBridgeClient client = mock(EventBridgeClient.class);
  // Every position a fetchFromTopic call requested, in call order — lets a multi-member test
  // assert an applier warmed once and never re-fetched from the changelog start (a torn-tail
  // recourse or a spurious cold restart would show up as a later, unexpected zero).
  private final List<Long> fetchPositionsRequested =
      Collections.synchronizedList(new ArrayList<>());

  FakeChangelogBroker() {
    when(client.newBatch())
        .thenAnswer(
            invocation -> {
              final BatchPublisher publisher = mock(BatchPublisher.class, RETURNS_SELF);
              final BatchBuilder builder = new BatchBuilder();
              when(publisher.add(any(byte[].class), any(byte[].class)))
                  .thenAnswer(
                      addInvocation -> {
                        builder.add(
                            (byte[]) addInvocation.getArgument(0),
                            (byte[]) addInvocation.getArgument(1));
                        return publisher;
                      });
              when(publisher.publishToTopic(any(), anyInt()))
                  .thenAnswer(
                      publishInvocation -> CompletableFuture.completedFuture(append(builder)));
              return publisher;
            });
    when(client.fetchFromTopic(any(), anyInt(), anyLong(), anyInt()))
        .thenAnswer(
            invocation -> {
              fetchPositionsRequested.add(invocation.getArgument(2, Long.class));
              return CompletableFuture.completedFuture(fetch());
            });
  }

  /** Every position a {@code fetchFromTopic} call requested so far, in call order. */
  List<Long> fetchPositionsRequested() {
    return List.copyOf(fetchPositionsRequested);
  }

  /** Appends {@code entries} (no marker) directly — used to simulate a torn tail. */
  synchronized List<Long> appendRaw(final List<byte[][]> entries) {
    final var builder = new BatchBuilder().keyed();
    entries.forEach(kv -> builder.add(kv[0], kv[1]));
    return append(builder);
  }

  private synchronized List<Long> append(final BatchBuilder builder) {
    final byte[] batch = builder.build();
    final long firstPosition = entryCount;
    BatchFormat.putLongLE(batch, BatchFormat.POSITION_OFFSET, firstPosition);
    log.writeBytes(batch);
    entryCount += builder.entryCount();
    return List.of(firstPosition, entryCount - 1);
  }

  private synchronized FetchResult fetch() {
    final byte[] data = log.toByteArray();
    // FetchResult's own header is big-endian (see its private readLong/readInt) — distinct from
    // BatchFormat's little-endian batch payload encoding; only the header uses this layout.
    final byte[] body = new byte[Long.BYTES * 3 + Integer.BYTES + data.length];
    putLongBE(body, 0, 0L); // firstBatchPosition — not consulted by BatchReader iteration
    putLongBE(body, 8, Math.max(0, entryCount - 1)); // lastBatchPosition — likewise unused
    putLongBE(body, 16, entryCount); // highWatermark
    putIntBE(body, 24, data.length);
    System.arraycopy(data, 0, body, 28, data.length);
    return FetchResult.parse(body);
  }

  private static void putLongBE(final byte[] out, final int offset, final long value) {
    putIntBE(out, offset, (int) (value >>> 32));
    putIntBE(out, offset + 4, (int) value);
  }

  private static void putIntBE(final byte[] out, final int offset, final int value) {
    out[offset] = (byte) (value >>> 24);
    out[offset + 1] = (byte) (value >>> 16);
    out[offset + 2] = (byte) (value >>> 8);
    out[offset + 3] = (byte) value;
  }
}
