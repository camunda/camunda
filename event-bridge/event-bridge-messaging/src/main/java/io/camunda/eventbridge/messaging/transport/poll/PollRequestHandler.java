/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.messaging.transport.poll;

import io.camunda.eventbridge.protocol.ErrorCode;
import io.camunda.eventbridge.protocol.EventBridgeBatchIterator;
import io.camunda.eventbridge.protocol.request.PollRequest;
import io.camunda.eventbridge.protocol.request.PollResponse;
import io.camunda.eventbridge.protocol.request.PollResponse.PollEvent;
import io.camunda.eventbridge.messaging.stream.EventStreamReader;
import io.camunda.eventbridge.transport.RequestHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Handles consumer poll requests for a single partition. Reads entries from the partition log
 * starting at the requested position and returns them inline as an SBE {@code PollResponse}
 * (copy-based, in contrast to the zero-copy fetch path).
 */
public final class PollRequestHandler implements RequestHandler {

  // Must match the topic the gateway BrokerClient sends to (default partition group).
  private static final String TOPIC_FORMAT = "default-poll-api-%d";
  private static final int DEFAULT_MAX_RECORDS = 1024;

  private final int partitionId;
  private final Supplier<EventStreamReader> readerFactory;

  public PollRequestHandler(
      final int partitionId, final Supplier<EventStreamReader> readerFactory) {
    this.partitionId = partitionId;
    this.readerFactory = readerFactory;
  }

  @Override
  public CompletableFuture<byte[]> handle(final byte[] requestBytes) {
    final PollRequest request = new PollRequest();
    try {
      request.wrap(new UnsafeBuffer(requestBytes), 0, requestBytes.length);
    } catch (final RuntimeException e) {
      return CompletableFuture.failedFuture(e);
    }

    final long fromPosition = request.getFromPosition();
    final int maxRecords =
        request.getMaxRecords() <= 0 ? DEFAULT_MAX_RECORDS : request.getMaxRecords();

    final List<PollEvent> events = new ArrayList<>();
    long nextPosition = fromPosition;

    try (final var reader = readerFactory.get()) {
      reader.seek(fromPosition <= 0 ? Long.MIN_VALUE : fromPosition);
      final var batchIterator = new EventBridgeBatchIterator();

      // The reader yields whole batches, starting with the one that *contains* fromPosition —
      // batches are never split. seek()/next() leave the current batch loaded, so the loop is
      // read-current-then-advance. The boundary batch is returned in full (it may include entries
      // before fromPosition); skipping already-processed entries within it is the consumer's job,
      // which also keeps maxRecords correct when it splits a multi-entry batch.
      outer:
      while (reader.hasNext()) {
        batchIterator.wrap(reader.batchBuffer(), reader.batchOffset(), reader.batchTotalSize());

        while (batchIterator.hasNext()) {
          final var entry = batchIterator.next();
          events.add(new PollEvent(entry.getPosition(), entry.getValueCopy()));
          nextPosition = entry.getPosition() + 1;
          if (events.size() >= maxRecords) {
            break outer;
          }
        }

        reader.next();
      }
    } catch (final RuntimeException e) {
      return CompletableFuture.failedFuture(e);
    }

    final var response =
        new PollResponse()
            .errorCode(ErrorCode.NONE)
            .nextPosition(events.isEmpty() ? fromPosition : nextPosition)
            .events(events)
            .errorMessage("");

    return CompletableFuture.completedFuture(encode(response));
  }

  private static byte[] encode(final PollResponse response) {
    final var buffer = new ExpandableArrayBuffer();
    final int length = response.write(buffer, 0);
    final var out = new byte[length];
    buffer.getBytes(0, out, 0, length);
    return out;
  }

  public static String topicName(final int partitionId) {
    return String.format(TOPIC_FORMAT, partitionId);
  }
}
