/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.logstreams.rdbms;

import io.camunda.zeebe.logstreams.storage.AbstractLogStorage;
import io.camunda.zeebe.logstreams.storage.LogStorage;
import io.camunda.zeebe.logstreams.storage.LogStorageReader;
import io.camunda.zeebe.util.buffer.BufferWriter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.agrona.concurrent.UnsafeBuffer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link LogStorage} that appends to an RDBMS through a {@link LogBlockRepository}.
 *
 * <p>{@link #append} is called under the sequencer's lock, so it only copies the block and queues
 * it. A single writer thread writes whatever has queued up in one transaction, then notifies the
 * append listeners in position order. That one thread is what gives {@code FlowControl} the
 * serialized {@code onWrite}/{@code onCommit} calls it expects from Raft's thread. A block counts
 * as written and committed at once, when its transaction commits.
 *
 * <p>As with Raft, an appended block is readable through {@link #newUncommittedReader()} before it
 * commits: it is kept in memory from {@link #append} until its transaction commits, and the
 * appended listeners are notified straight away. This lets the stream processor, which reads
 * uncommitted records, carry on without waiting for each commit. Should the blocks never commit,
 * the storage fails and the partition steps down, as it would lose uncommitted Raft entries.
 *
 * <p>Unlike with Raft, a database that stops accepting writes does not make this node lose
 * leadership by itself. The storage therefore reports it through the failure callback, which is
 * expected to make the partition step down: at once if another leader has claimed the partition,
 * and once retrying has run out for any other error. After failing or closing, appends are dropped
 * without callbacks, as Raft drops them once it is no longer leader.
 */
public final class RdbmsLogStorage extends AbstractLogStorage implements AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(RdbmsLogStorage.class);

  private final LogBlockRepository repository;
  private final long term;
  private final Config config;
  private final Consumer<Throwable> failureListener;
  private final @Nullable RdbmsLogStorageMetrics metrics;
  private final BlockingQueue<PendingAppend> queue = new LinkedBlockingQueue<>();

  /** Appended blocks whose transaction has not committed yet, by low position. */
  private final NavigableMap<Long, LogBlock> uncommitted = new ConcurrentSkipListMap<>();

  private final AtomicReference<State> state = new AtomicReference<>(State.OPEN);
  private final @Nullable Thread writer;

  /** Taken from the queue but too large for the last batch; starts the next one. Writer only. */
  private @Nullable PendingAppend overflow;

  private RdbmsLogStorage(
      final LogBlockRepository repository,
      final long term,
      final Config config,
      final Consumer<Throwable> failureListener,
      final @Nullable String writerName,
      final @Nullable RdbmsLogStorageMetrics metrics) {
    this.repository = repository;
    this.term = term;
    this.config = config;
    this.failureListener = failureListener;
    this.metrics = metrics;
    writer =
        writerName == null
            ? null
            : Thread.ofPlatform().name(writerName).daemon().unstarted(this::runWriter);
  }

  /**
   * A storage for the leader of {@code term}, which must already have claimed the partition with
   * {@link LogBlockRepository#claimTerm}.
   *
   * @param failureListener called at most once, from the writer thread, when the storage can no
   *     longer append
   */
  public static RdbmsLogStorage writable(
      final String writerName,
      final LogBlockRepository repository,
      final long term,
      final Config config,
      final MeterRegistry meterRegistry,
      final Consumer<Throwable> failureListener) {
    final var storage =
        new RdbmsLogStorage(
            repository,
            term,
            config,
            failureListener,
            writerName,
            new RdbmsLogStorageMetrics(meterRegistry));
    Objects.requireNonNull(storage.writer).start();
    return storage;
  }

  /** A storage for followers, which must never append. */
  public static RdbmsLogStorage readOnly(final LogBlockRepository repository, final Config config) {
    return new RdbmsLogStorage(repository, -1, config, error -> {}, null, null);
  }

  @Override
  public LogStorageReader newReader() {
    return new RdbmsLogStorageReader(repository, config.readerPageSize());
  }

  @Override
  public LogStorageReader newUncommittedReader() {
    return new RdbmsLogStorageReader(repository, config.readerPageSize(), uncommitted);
  }

  @Override
  public void append(
      final long lowestPosition,
      final long highestPosition,
      final BufferWriter bufferWriter,
      final AppendListener listener) {
    if (writer == null) {
      throw new UnsupportedOperationException(
          "Expected to append positions %d - %d, but the storage is read-only; followers must not append"
              .formatted(lowestPosition, highestPosition));
    }
    if (state.get() != State.OPEN) {
      LOG.debug(
          "Dropping append of positions {} - {}, storage is {}",
          lowestPosition,
          highestPosition,
          state.get());
      return;
    }

    // the writer refers to the caller's entries, which may change once this returns
    final var data = new byte[bufferWriter.getLength()];
    bufferWriter.write(new UnsafeBuffer(data), 0);
    // into memory before the queue, so a block is never committed while not yet readable there
    uncommitted.put(lowestPosition, new LogBlock(lowestPosition, highestPosition, term, data));
    queue.add(new PendingAppend(lowestPosition, highestPosition, data, listener));
    notifyAppended(highestPosition);
  }

  /**
   * Stops the writer. Blocks still queued are dropped; a transaction already in flight may still
   * commit, but nobody is notified of it.
   */
  @Override
  public void close() {
    final var previous = state.getAndSet(State.CLOSED);
    if (writer != null && previous != State.CLOSED) {
      writer.interrupt();
    }
    queue.clear();
    uncommitted.clear();
  }

  private void runWriter() {
    final var batch = new ArrayList<PendingAppend>();
    try {
      while (state.get() == State.OPEN) {
        final var carriedOver = overflow;
        overflow = null;
        batch.add(carriedOver != null ? carriedOver : queue.take());
        collectBatch(batch);
        writeWithRetry(batch);
        if (state.get() == State.OPEN) {
          notifyCommitted(batch);
        }
        batch.clear();
      }
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (final Exception e) {
      fail(e);
    }
  }

  /**
   * Adds what has queued up to the batch, and keeps waiting for more until the commit linger has
   * passed since the first block was taken, so more blocks share one transaction. Stops early once
   * the next block would exceed the batch's byte limit, keeping that block for the next batch.
   */
  private void collectBatch(final List<PendingAppend> batch) throws InterruptedException {
    long batchBytes = batch.getFirst().data().length;
    final long deadline = System.nanoTime() + config.commitLinger().toNanos();
    while (true) {
      var next = queue.poll();
      if (next == null) {
        final long remaining = deadline - System.nanoTime();
        if (remaining <= 0) {
          return;
        }
        next = queue.poll(remaining, TimeUnit.NANOSECONDS);
        if (next == null) {
          return;
        }
      }
      if (batchBytes + next.data().length > config.maxBatchBytes()) {
        overflow = next;
        return;
      }
      batch.add(next);
      batchBytes += next.data().length;
    }
  }

  private void writeWithRetry(final List<PendingAppend> batch) throws InterruptedException {
    final var blocks =
        batch.stream()
            .map(append -> new LogBlock(append.low(), append.high(), term, append.data()))
            .toList();
    final long deadline = System.nanoTime() + config.retryTimeout().toNanos();
    var backoff = config.initialRetryBackoff();
    var isRetry = false;

    while (true) {
      try {
        final long startNanos = System.nanoTime();
        repository.append(term, blocks);
        if (metrics != null) {
          metrics.observeAppend(blocks.size(), System.nanoTime() - startNanos);
        }
        return;
      } catch (final TermMismatchException e) {
        throw e;
      } catch (final LogBlockRepositoryException e) {
        // the previous attempt may have committed without us hearing back, in which case this one
        // fails on the blocks it already wrote
        if (isRetry && isAlreadyWritten(blocks.getFirst())) {
          return;
        }
        if (System.nanoTime() + backoff.toNanos() > deadline) {
          throw e;
        }
        LOG.warn(
            "Failed to append positions {} - {}, retrying in {}",
            blocks.getFirst().lowPosition(),
            blocks.getLast().highPosition(),
            backoff,
            e);
        Thread.sleep(backoff);
        backoff = min(backoff.multipliedBy(2), config.maxRetryBackoff());
        isRetry = true;
      }
    }
  }

  private boolean isAlreadyWritten(final LogBlock first) {
    try {
      return repository
          .findBlockAtOrBefore(first.lowPosition())
          .filter(block -> block.lowPosition() == first.lowPosition() && block.term() == term)
          .isPresent();
    } catch (final LogBlockRepositoryException e) {
      return false;
    }
  }

  private void notifyCommitted(final List<PendingAppend> batch) {
    // only now that the blocks are in the database, so a reader that misses them in memory finds
    // them there
    batch.forEach(append -> uncommitted.remove(append.low()));
    for (final var append : batch) {
      // Raft passes its log index here; positions are the closest equivalent
      append.listener().onWrite(append.high(), append.high());
      append.listener().onCommit(append.high(), append.high());
    }
    final var highestPosition = batch.getLast().high();
    notifyCommittedPosition(highestPosition);
    notifyCommit();
  }

  private void fail(final Exception error) {
    if (state.compareAndSet(State.OPEN, State.FAILED)) {
      LOG.error("Failed to append to the RDBMS log in term {}, no longer appending", term, error);
      queue.clear();
      uncommitted.clear();
      failureListener.accept(error);
    }
  }

  private static Duration min(final Duration a, final Duration b) {
    return a.compareTo(b) <= 0 ? a : b;
  }

  private enum State {
    OPEN,
    FAILED,
    CLOSED
  }

  private record PendingAppend(long low, long high, byte[] data, AppendListener listener) {}

  /**
   * @param retryTimeout how long to keep retrying a failed transaction before failing the storage
   * @param initialRetryBackoff the wait before the first retry, doubled for each later one
   * @param maxRetryBackoff the longest wait between two retries
   * @param maxBatchBytes the most block bytes to write in one transaction; a single larger block is
   *     still written on its own
   * @param readerPageSize the most blocks a reader fetches with one query
   * @param commitLinger how long the writer waits for more blocks after taking the first one, so
   *     that fewer, larger transactions are committed at the cost of that much latency; zero
   *     commits whatever has queued up at once
   */
  public record Config(
      Duration retryTimeout,
      Duration initialRetryBackoff,
      Duration maxRetryBackoff,
      int maxBatchBytes,
      int readerPageSize,
      Duration commitLinger) {

    /**
     * Experimental: read directly from the environment until the storage gets proper configuration,
     * so load tests can try values without code changes.
     */
    public static final String COMMIT_LINGER_ENV =
        "CAMUNDA_EXPERIMENTAL_RDBMSLOGSTORAGE_COMMITLINGERMS";

    public static final Config DEFAULT =
        new Config(
            // long enough to ride out a database restart, e.g. its pod being rescheduled, without
            // the leader stepping down, which would not help while the database is down
            Duration.ofSeconds(60),
            Duration.ofMillis(50),
            Duration.ofSeconds(1),
            16 * 1024 * 1024,
            32,
            Duration.ZERO);

    /** The defaults, with the commit linger taken from {@link #COMMIT_LINGER_ENV} if set. */
    public static Config fromEnvironment(final Map<String, String> environment) {
      final var lingerMillis = environment.get(COMMIT_LINGER_ENV);
      if (lingerMillis == null || lingerMillis.isBlank()) {
        return DEFAULT;
      }
      try {
        return DEFAULT.withCommitLinger(Duration.ofMillis(Long.parseLong(lingerMillis.trim())));
      } catch (final NumberFormatException e) {
        LOG.warn(
            "Ignoring {}={}, expected a number of milliseconds", COMMIT_LINGER_ENV, lingerMillis);
        return DEFAULT;
      }
    }

    public Config withCommitLinger(final Duration commitLinger) {
      return new Config(
          retryTimeout,
          initialRetryBackoff,
          maxRetryBackoff,
          maxBatchBytes,
          readerPageSize,
          commitLinger);
    }
  }
}
