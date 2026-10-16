/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.logarchive.leader;

import io.camunda.zeebe.backup.logarchive.model.LatestHint;
import io.camunda.zeebe.backup.logarchive.model.LogArchiveJson;
import io.camunda.zeebe.backup.logarchive.model.LogArchiveKeys;
import io.camunda.zeebe.backup.logarchive.processing.LogArchiveListener;
import io.camunda.zeebe.backup.logarchive.processing.LogArchivePublication;
import io.camunda.zeebe.backup.logarchive.processing.PublishRequest;
import io.camunda.zeebe.backup.logarchive.processing.SealRequest;
import io.camunda.zeebe.backup.logarchive.state.LogArchiveMeta;
import io.camunda.zeebe.backup.logarchive.store.LogArchiveStore;
import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.logstreams.log.LogStreamWriter;
import io.camunda.zeebe.logstreams.log.WriteContext;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.value.management.IndexChunk;
import io.camunda.zeebe.protocol.impl.record.value.management.LogArchiveRecord;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.management.LogArchiveIntent;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.util.Either;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Writes the bucket objects the processor decided on, strictly in request order, one step at a time
 * (spec section 8.3). A failed step is retried with exponential backoff; nothing is skipped or
 * reordered. Pending requests are dropped on close or recovery because a new leader republishes
 * everything.
 */
@NullMarked
public final class LogArchivePublisher extends Actor
    implements LogArchivePublication, LogArchiveListener {

  private static final Logger LOG = LoggerFactory.getLogger(LogArchivePublisher.class);
  private static final Duration INITIAL_BACKOFF = Duration.ofMillis(100);
  private static final Duration MAX_BACKOFF = Duration.ofSeconds(10);
  private static final long NO_CHUNK = -1L;

  private final int partitionId;
  private final LogArchiveStore store;
  private final LogStreamWriter writer;

  // all fields below are only accessed on the actor thread
  private final Queue<PublishRequest> queue = new ArrayDeque<>();
  private Step step = Step.COMMIT;
  private boolean busy;
  private boolean closed;
  // incremented whenever queued state is discarded, so callbacks of an earlier term are ignored
  private long generation;
  private Duration backoff = INITIAL_BACKOFF;
  private long pendingSealChunkNo = NO_CHUNK;

  public LogArchivePublisher(
      final int partitionId, final LogArchiveStore store, final LogStreamWriter writer) {
    super(
        buildActorName("LogArchivePublisher", partitionId),
        null,
        Map.of(ACTOR_PROP_PARTITION_ID, Integer.toString(partitionId)));
    this.partitionId = partitionId;
    this.store = store;
    this.writer = writer;
  }

  @Override
  public void publish(final PublishRequest request) {
    actor.run(
        () -> {
          if (closed) {
            return;
          }
          queue.add(request);
          drive();
        });
  }

  @Override
  public void onRecovered(final LogArchiveMeta meta) {
    actor.run(
        () -> {
          generation++;
          queue.clear();
          busy = false;
          step = Step.COMMIT;
          backoff = INITIAL_BACKOFF;
          pendingSealChunkNo = NO_CHUNK;
        });
  }

  @Override
  public void onIndexSealed(final long chunkNo) {
    actor.run(
        () -> {
          if (pendingSealChunkNo == chunkNo) {
            pendingSealChunkNo = NO_CHUNK;
          }
        });
  }

  @Override
  public void onCommandRejected(
      final LogArchiveIntent command, final RejectionType type, final String reason) {
    LOG.debug(
        "Partition {}: {} rejected by the processor ({}: {}); not retrying",
        partitionId,
        command,
        type,
        reason);
    if (command == LogArchiveIntent.SEAL_INDEX) {
      actor.run(() -> pendingSealChunkNo = NO_CHUNK);
    }
  }

  @Override
  protected void onActorClosing() {
    closed = true;
    generation++;
    queue.clear();
  }

  private void drive() {
    if (busy || closed || queue.isEmpty()) {
      return;
    }
    busy = true;
    step = Step.COMMIT;
    backoff = INITIAL_BACKOFF;
    execute();
  }

  private void execute() {
    final var request = queue.peek();
    if (request == null || closed) {
      busy = false;
      return;
    }
    final var prefix = LogArchiveKeys.partitionPrefix(request.timelineId(), partitionId);
    final long seq = request.seq();

    switch (step) {
      case COMMIT -> {
        final var json = request.commitEntryJson();
        if (json == null) {
          advance();
        } else {
          put(prefix + LogArchiveKeys.commitKey(seq), json);
        }
      }
      case ROOT -> {
        final var json = request.rootJson();
        if (json == null) {
          advance();
        } else {
          put(prefix + LogArchiveKeys.rootKey(seq), json);
        }
      }
      case LATEST -> {
        if (request.rootJson() == null) {
          advance();
        } else {
          final var hint =
              new LatestHint(LogArchiveJson.FORMAT_VERSION, request.timelineId(), partitionId, seq);
          put(prefix + LogArchiveKeys.latestKey(), LogArchiveJson.write(hint));
        }
      }
      case CONFIRM -> {
        if (request.rootJson() == null) {
          advance();
        } else {
          final var record =
              new LogArchiveRecord().setTimelineId(request.timelineId()).setPublishedSeq(seq);
          if (write(LogArchiveIntent.CONFIRM_PUBLISHED, record)) {
            advance();
          }
        }
      }
      case SEAL_PUT -> {
        final var seal = request.seal();
        if (seal == null) {
          advance();
        } else {
          put(prefix + seal.chunk().getKey(), seal.chunkJson());
        }
      }
      case SEAL_WRITE -> {
        final var seal = request.seal();
        if (seal == null || seal.chunk().getChunkNo() == pendingSealChunkNo) {
          advance();
        } else {
          writeSeal(request.timelineId(), seal);
        }
      }
      case DONE -> {
        queue.poll();
        busy = false;
        drive();
      }
    }
  }

  private void writeSeal(final String timelineId, final SealRequest seal) {
    final var record =
        new LogArchiveRecord()
            .setTimelineId(timelineId)
            .setChunk(new IndexChunk().copy(seal.chunk()));
    final long chunkNo = seal.chunk().getChunkNo();
    if (write(LogArchiveIntent.SEAL_INDEX, record)) {
      pendingSealChunkNo = chunkNo;
      advance();
    }
  }

  private void put(final String key, final byte[] content) {
    final long expected = generation;
    CompletableFuture<Void> future;
    try {
      future = store.put(key, content);
    } catch (final RuntimeException e) {
      future = CompletableFuture.failedFuture(e);
    }
    future.whenComplete(
        (ignored, error) ->
            actor.run(
                () -> {
                  if (expected != generation) {
                    return;
                  }
                  if (error == null) {
                    advance();
                  } else {
                    LOG.warn(
                        "Partition {}: failed to PUT {}, retrying in {}",
                        partitionId,
                        key,
                        backoff,
                        error);
                    retryLater();
                  }
                }));
  }

  /** Returns whether the command was written; schedules a retry of the current step otherwise. */
  private boolean write(final LogArchiveIntent intent, final LogArchiveRecord record) {
    final var result =
        writer.tryWrite(
            WriteContext.internal(),
            LogAppendEntry.of(
                new RecordMetadata()
                    .recordType(RecordType.COMMAND)
                    .valueType(ValueType.LOG_ARCHIVE)
                    .intent(intent),
                record));
    if (result instanceof Either.Left<?, ?>(final var failure)) {
      LOG.warn(
          "Partition {}: failed to write {} ({}), retrying in {}",
          partitionId,
          intent,
          failure,
          backoff);
      retryLater();
      return false;
    }
    return true;
  }

  private void advance() {
    step = Step.values()[Math.min(step.ordinal() + 1, Step.DONE.ordinal())];
    backoff = INITIAL_BACKOFF;
    execute();
  }

  private void retryLater() {
    final long expected = generation;
    final long delayMs = backoff.toMillis();
    backoff =
        backoff.multipliedBy(2).compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : backoff.multipliedBy(2);
    actor.schedule(
        Duration.ofMillis(delayMs),
        () -> {
          if (expected == generation) {
            execute();
          }
        });
  }

  private enum Step {
    COMMIT,
    ROOT,
    LATEST,
    CONFIRM,
    SEAL_PUT,
    SEAL_WRITE,
    DONE
  }
}
