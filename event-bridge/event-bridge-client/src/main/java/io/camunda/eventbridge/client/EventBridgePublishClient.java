/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.eventbridge.protocol.EventBridgeBatch;
import io.camunda.eventbridge.protocol.EventBridgeBatchBuilder;
import io.camunda.eventbridge.protocol.EventBridgeBatchIterator;
import io.camunda.eventbridge.protocol.EventBridgeEntry;
import io.camunda.eventbridge.protocol.EventBridgeEntryBuilder;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Client for publishing and fetching events from EventBridge partitions.
 *
 * <p>Publish usage:
 *
 * <pre>
 * try (var client = new EventBridgeClient("http://gateway:8080")) {
 *   var result = client.newBatch()
 *       .add("order-123", jsonBytes)
 *       .add("order-456", jsonBytes2)
 *       .publish(partitionId)
 *       .join();
 *
 *   if (result.isSuccess()) {
 *     System.out.println("Positions: " + result.logPositions());
 *   }
 * }
 * </pre>
 *
 * <p>Fetch usage:
 *
 * <pre>
 * try (var client = new EventBridgeClient("http://gateway:8080")) {
 *   var result = client.fetch(partitionId, offset, maxBytes).join();
 *
 *   for (var entry : result.entries(offset)) {
 *     System.out.println("Position: " + entry.getPosition());
 *     System.out.println("Value: " + new String(entry.getValueCopy()));
 *   }
 *
 *   // Track progress
 *   long nextOffset = result.nextOffset(offset);
 *   long lag = result.lag();
 * }
 * </pre>
 */
public final class EventBridgePublishClient implements AutoCloseable {

  private final HttpClient httpClient;
  private final String gatewayBaseUrl;
  private final Duration requestTimeout;
  private final ObjectMapper objectMapper;

  public EventBridgePublishClient(final String gatewayBaseUrl) {
    this(gatewayBaseUrl, Duration.ofSeconds(10));
  }

  public EventBridgePublishClient(final String gatewayBaseUrl, final Duration requestTimeout) {
    this.gatewayBaseUrl = gatewayBaseUrl;
    this.requestTimeout = requestTimeout;
    httpClient = HttpClient.newBuilder().connectTimeout(requestTimeout).build();
    objectMapper = new ObjectMapper();
  }

  // -- Publish --

  /** Creates a new batch builder for publishing multiple entries in a single request. */
  public BatchPublisher newBatch() {
    return new BatchPublisher();
  }

  /** Publishes a single keyed entry. */
  public CompletableFuture<PublishResult> publish(
      final int partitionId, final String key, final byte[] value) {
    return newBatch().add(key, value).publish(partitionId);
  }

  /** Publishes a single keyless entry. */
  public CompletableFuture<PublishResult> publish(final int partitionId, final byte[] value) {
    return newBatch().add(value).publish(partitionId);
  }

  // -- Fetch --

  /**
   * Fetches complete batches from a partition starting at the given offset.
   *
   * @param partitionId partition to fetch from
   * @param offset position to start reading from (inclusive)
   * @param maxBytes maximum bytes to return
   * @return future completing with the fetch result
   */
  public CompletableFuture<FetchResult> fetch(
      final int partitionId, final long offset, final int maxBytes) {
    return fetch(partitionId, offset, maxBytes, 0, 0);
  }

  /**
   * Fetches complete batches with long-poll support. If not enough data is available, the server
   * waits up to {@code maxWaitMs} for new data before responding.
   *
   * @param partitionId partition to fetch from
   * @param offset position to start reading from (inclusive)
   * @param maxBytes maximum bytes to return
   * @param minBytes minimum bytes before responding (long-poll threshold)
   * @param maxWaitMs maximum time to wait for minBytes (0 = respond immediately)
   * @return future completing with the fetch result
   */
  public CompletableFuture<FetchResult> fetch(
      final int partitionId,
      final long offset,
      final int maxBytes,
      final int minBytes,
      final long maxWaitMs) {

    final var uri =
        URI.create(
            gatewayBaseUrl
                + "/v1/events/"
                + partitionId
                + "/fetch"
                + "?offset="
                + offset
                + "&maxBytes="
                + maxBytes
                + "&minBytes="
                + minBytes
                + "&maxWaitMs="
                + maxWaitMs);

    final var request =
        HttpRequest.newBuilder()
            .uri(uri)
            .header("Accept", "application/octet-stream")
            .timeout(requestTimeout)
            .GET()
            .build();

    return httpClient
        .sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
        .thenApply(this::parseFetchResponse);
  }

  @Override
  public void close() {
    httpClient.close();
  }

  // -- Response parsing --

  private FetchResult parseFetchResponse(final HttpResponse<byte[]> response) {
    if (response.statusCode() != 200) {
      return FetchResult.error(
          response.statusCode(), "Fetch failed: HTTP " + response.statusCode());
    }

    final var body = response.body();
    if (body == null || body.length == 0) {
      return FetchResult.empty(0);
    }

    // Parse response header
    final long firstBatchPosition = readLong(body, 0);
    final long lastBatchPosition = readLong(body, 8);
    final long highWatermark = readLong(body, 16);
    final int dataLength = readInt(body, 24);

    if (dataLength == 0) {
      return FetchResult.empty(highWatermark);
    }

    final int headerSize = Long.BYTES * 3 + Integer.BYTES;
    final var data = new byte[dataLength];
    System.arraycopy(body, headerSize, data, 0, dataLength);

    return new FetchResult(
        true, firstBatchPosition, lastBatchPosition, data, dataLength, highWatermark, 0, null);
  }

  private PublishResult parsePublishResponse(final HttpResponse<String> response) {
    try {
      final var json = objectMapper.readTree(response.body());
      final var status = json.get("status").asText();

      if ("SUCCESS".equals(status)) {
        final List<Long> values = new ArrayList<>();
        json.get("logPositions").elements().forEachRemaining(n -> values.add(n.asLong()));
        return PublishResult.success(values);
      }

      return PublishResult.error(
          response.statusCode(),
          json.has("rejectionReason") ? json.get("rejectionReason").asText() : "UNKNOWN",
          json.has("message") ? json.get("message").asText() : "Unknown error");

    } catch (final Exception e) {
      return PublishResult.error(
          response.statusCode(), "UNKNOWN", "Failed to parse response: " + response.body());
    }
  }

  // -- Primitive readers (big-endian) --

  private static long readLong(final byte[] data, final int offset) {
    return ((long) readInt(data, offset)) << 32 | (readInt(data, offset + 4) & 0xFFFFFFFFL);
  }

  private static int readInt(final byte[] data, final int offset) {
    return (data[offset] & 0xFF) << 24
        | (data[offset + 1] & 0xFF) << 16
        | (data[offset + 2] & 0xFF) << 8
        | (data[offset + 3] & 0xFF);
  }

  // -- Publish result --

  public record PublishResult(
      boolean success,
      List<Long> logPositions,
      int statusCode,
      String rejectionReason,
      String error) {

    public boolean isSuccess() {
      return success;
    }

    static PublishResult success(final List<Long> logPositions) {
      return new PublishResult(true, logPositions, 200, null, null);
    }

    static PublishResult error(
        final int statusCode, final String rejectionReason, final String error) {
      return new PublishResult(false, List.of(), statusCode, rejectionReason, error);
    }
  }

  // -- Fetch result --

  /**
   * Result of a fetch operation. Contains zero or more complete batches. Entry-level iteration is
   * handled client-side via {@link #entries(long)}.
   *
   * @param success true if the fetch succeeded
   * @param firstBatchPosition position of the first entry in the first batch
   * @param lastBatchPosition position of the last entry in the last batch
   * @param data raw batch bytes — zero or more complete EventBridgeBatch, contiguous
   * @param dataLength actual bytes of batch data
   * @param highWatermark latest committed position on the partition
   * @param statusCode HTTP status code
   * @param error human-readable error message (null on success)
   */
  public record FetchResult(
      boolean success,
      long firstBatchPosition,
      long lastBatchPosition,
      byte[] data,
      int dataLength,
      long highWatermark,
      int statusCode,
      String error) {

    public boolean isSuccess() {
      return success;
    }

    public boolean isEmpty() {
      return dataLength == 0;
    }

    /**
     * Returns the consumer lag — the number of positions between the last fetched position and the
     * high watermark. A lag of 0 means the consumer is fully caught up.
     */
    public long lag() {
      if (lastBatchPosition < 0) {
        return highWatermark;
      }
      return highWatermark - lastBatchPosition;
    }

    /**
     * Returns the offset to use in the next fetch request. This is the position after the last
     * entry in the last batch. If the fetch was empty, returns the original requested offset.
     *
     * @param requestedOffset the offset used in the fetch request
     * @return the next offset to fetch from
     */
    public long nextOffset(final long requestedOffset) {
      if (lastBatchPosition < 0) {
        return requestedOffset;
      }
      return lastBatchPosition + 1;
    }

    /**
     * Iterates over individual entries across all batches in the response. Skips entries before
     * {@code startOffset} — the broker returns complete batches, so the first batch may contain
     * entries before the requested offset.
     *
     * <p>The returned iterator creates one {@link EventBridgeBatchIterator} and one {@link
     * EventBridgeEntry} flyweight, reused across all entries. Callers must consume or copy entry
     * data before advancing.
     *
     * @param startOffset skip entries before this position
     * @return iterable over entries at or after startOffset
     */
    public Iterable<EventBridgeEntry> entries(final long startOffset) {
      return () -> new EntryIterator(data, dataLength, startOffset);
    }

    /** Iterates over all entries in the response without skipping. */
    public Iterable<EventBridgeEntry> entries() {
      return () -> new EntryIterator(data, dataLength, Long.MIN_VALUE);
    }

    static FetchResult empty(final long highWatermark) {
      return new FetchResult(true, 0, -1, new byte[0], 0, highWatermark, 200, null);
    }

    static FetchResult error(final int statusCode, final String error) {
      return new FetchResult(false, 0, -1, new byte[0], 0, -1, statusCode, error);
    }
  }

  /**
   * Iterates over individual entries across multiple contiguous batches. Handles batch boundaries
   * transparently — the caller sees a flat stream of entries.
   *
   * <p>Uses the flyweight pattern — one {@link EventBridgeBatchIterator} and one {@link
   * EventBridgeEntry} are reused. Entry data is only valid until the next {@link #next()} call.
   */
  private static final class EntryIterator implements Iterator<EventBridgeEntry> {

    private final UnsafeBuffer buffer;
    private final int dataLength;
    private final long startOffset;
    private final EventBridgeBatchIterator batchIterator;

    private int batchCursor;
    private boolean started;

    EntryIterator(final byte[] data, final int dataLength, final long startOffset) {
      buffer = new UnsafeBuffer(data, 0, dataLength);
      this.dataLength = dataLength;
      this.startOffset = startOffset;
      batchIterator = new EventBridgeBatchIterator();
      batchCursor = 0;
      started = false;
    }

    @Override
    public boolean hasNext() {
      ensureStarted();
      // Current batch has more entries
      if (batchIterator.hasNext()) {
        return true;
      }
      // Try loading the next batch
      return loadNextBatch();
    }

    @Override
    public EventBridgeEntry next() {
      if (!hasNext()) {
        throw new NoSuchElementException("No more entries");
      }
      return batchIterator.next();
    }

    private void ensureStarted() {
      if (!started) {
        started = true;
        if (loadNextBatch() && startOffset > Long.MIN_VALUE) {
          batchIterator.skipTo(startOffset);
        }
      }
    }

    private boolean loadNextBatch() {
      while (batchCursor + EventBridgeBatch.HEADER_LENGTH <= dataLength) {
        final int batchLength = EventBridgeBatch.getBatchLength(buffer, batchCursor);
        final int totalSize = EventBridgeBatch.totalSize(batchLength);

        if (batchLength <= 0 || batchCursor + totalSize > dataLength) {
          return false;
        }

        batchIterator.wrap(buffer, batchCursor, totalSize);
        batchCursor += totalSize;

        // Skip entire batch if all entries are before startOffset
        if (startOffset > Long.MIN_VALUE) {
          final long batchLastPosition =
              batchIterator.getBatchPosition() + batchIterator.getEntryCount() - 1;
          if (batchLastPosition < startOffset) {
            continue;
          }
        }

        if (batchIterator.hasNext()) {
          return true;
        }
      }
      return false;
    }
  }

  // -- Batch publisher --

  public final class BatchPublisher {

    private final EventBridgeBatchBuilder batchBuilder = new EventBridgeBatchBuilder();
    private final EventBridgeEntryBuilder entryBuilder = new EventBridgeEntryBuilder();

    public BatchPublisher add(final String key, final byte[] value) {
      batchBuilder.addEntry(entryBuilder.reset().key(key).value(value).build());
      return this;
    }

    public BatchPublisher add(final byte[] key, final byte[] value) {
      batchBuilder.addEntry(entryBuilder.reset().key(key).value(value).build());
      return this;
    }

    public BatchPublisher add(final byte[] value) {
      batchBuilder.addEntry(entryBuilder.reset().value(value).build());
      return this;
    }

    public BatchPublisher add(final String value) {
      batchBuilder.addEntry(entryBuilder.reset().value(value).build());
      return this;
    }

    public BatchPublisher addEntry(final byte[] entryBytes) {
      batchBuilder.addEntry(entryBytes);
      return this;
    }

    public CompletableFuture<PublishResult> publish(final int partitionId) {
      if (batchBuilder.getEntryCount() == 0) {
        return CompletableFuture.failedFuture(new IllegalStateException("Batch is empty"));
      }

      final var request =
          HttpRequest.newBuilder()
              .uri(URI.create(gatewayBaseUrl + "/v1/events/" + partitionId))
              .header("Content-Type", "application/octet-stream")
              .timeout(requestTimeout)
              .POST(HttpRequest.BodyPublishers.ofByteArray(batchBuilder.build()))
              .build();

      return httpClient
          .sendAsync(request, HttpResponse.BodyHandlers.ofString())
          .thenApply(EventBridgePublishClient.this::parsePublishResponse);
    }
  }
}
