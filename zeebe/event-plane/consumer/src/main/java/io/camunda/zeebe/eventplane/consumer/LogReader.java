/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.consumer;

import io.camunda.zeebe.eventplane.history.Histories;
import io.camunda.zeebe.eventplane.history.HistoryLineage;
import io.camunda.zeebe.eventplane.segment.Entry;
import io.camunda.zeebe.eventplane.store.NoSuchObjectException;
import io.camunda.zeebe.eventplane.store.ObjectNames;
import io.camunda.zeebe.eventplane.store.ObjectStore;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads one log's records from the store, in position order, from a cursor or one of the log's
 * ends. Each {@link #poll} works out the log's current history and path from the manifests, lists
 * the segments that hold the next entries, and reads them with ranged GETs. It skips entries and
 * records it has delivered, so overlapping segments of two leaders and rollups that replace
 * segments while it reads are fine. It tells its listener of gaps and of a changed history before
 * it reads on.
 *
 * <p>Not thread-safe: one thread polls. {@link #cursor} and {@link #stop} work from any thread.
 */
public final class LogReader {

  /** How long a hole without a retention marker lasts before a reader reports it as a gap. */
  public static final Duration DEFAULT_HOLE_GRACE = Duration.ofMinutes(1);

  private static final Logger LOG = LoggerFactory.getLogger(LogReader.class);
  private static final int PAGE_SIZE = 1000;
  private static final long NONE = -1;

  private final ObjectStore store;
  private final LogId log;
  private final ConsumerListener listener;
  private final long holeGraceNanos;
  private final MonotonicClock clock;
  private final String retentionPrefix;
  private final String historiesPrefix;
  private @Nullable Start start;
  private volatile @Nullable Cursor cursor;
  private volatile boolean stopped;

  // What the polling thread remembers between polls.
  private long holeIndex = NONE;
  private long holeSince;
  private @Nullable String markerKey;
  private @Nullable RetentionMarker marker;
  private @Nullable HistoryLineage lineage;
  private List<String> lineageKeys = List.of();
  // The directory and index for which a whole listing found no segment, and nothing after it.
  private @Nullable String listedWholeFor;

  public LogReader(final ObjectStore store, final LogId log, final StartFrom from) {
    this(store, log, from, ConsumerListener.NONE);
  }

  public LogReader(
      final ObjectStore store,
      final LogId log,
      final StartFrom from,
      final ConsumerListener listener) {
    this(store, log, from, listener, DEFAULT_HOLE_GRACE);
  }

  /**
   * @param store the store with the log's segments and manifests
   * @param log the log to read
   * @param from a cursor to read on after, or the end of the log to start at
   * @param listener hears of gaps and changed histories
   * @param holeGrace how long entries may be missing, with a later segment in the store and no
   *     retention marker that explains it, before the reader reports a gap and reads on
   */
  public LogReader(
      final ObjectStore store,
      final LogId log,
      final StartFrom from,
      final ConsumerListener listener,
      final Duration holeGrace) {
    this(store, log, from, listener, holeGrace, MonotonicClock.SYSTEM);
  }

  LogReader(
      final ObjectStore store,
      final LogId log,
      final StartFrom from,
      final ConsumerListener listener,
      final Duration holeGrace,
      final MonotonicClock clock) {
    this.store = store;
    this.log = log;
    this.listener = listener;
    holeGraceNanos = holeGrace.toNanos();
    this.clock = clock;
    retentionPrefix = ObjectNames.retentionPrefix(log.cluster(), log.tenant(), log.partition());
    historiesPrefix = ObjectNames.historiesPrefix(log.cluster(), log.tenant());
    switch (from) {
      case final Cursor given -> cursor = given;
      case final Start given -> start = given;
    }
  }

  public LogId log() {
    return log;
  }

  /**
   * How far the reader got, in the current history it read in; empty until the first poll that
   * finds the log works out where a start of {@link Start#EARLIEST} or {@link Start#LATEST} is.
   */
  public Optional<Cursor> cursor() {
    return Optional.ofNullable(cursor);
  }

  /** Makes the reader stop at the next entry boundary, and every later poll do nothing. */
  public void stop() {
    stopped = true;
  }

  /**
   * Delivers the records that the store has after the cursor, in position order, until there are no
   * more or the reader is stopped. If the handler throws, the cursor stays before the record it
   * threw for, and the exception comes out of this method.
   *
   * @return whether it delivered anything, reported a gap or a changed history
   * @throws IOException if the store fails
   */
  public boolean poll(final RecordHandler handler) throws IOException {
    if (stopped) {
      return false;
    }
    final var path = LogPath.of(lineage(), log);
    if (path == null) {
      LOG.trace("Log {} has no current history with its partition yet", log);
      return false;
    }
    if (cursor == null) {
      cursor = resolve(path);
      if (cursor == null) {
        return false;
      }
    }
    final var followed = followHistory(path);
    if (followed == Step.IDLE) {
      return false;
    }
    boolean progress = followed == Step.MOVED;
    final Set<String> gone = new HashSet<>();
    while (!stopped) {
      final var step = step(path, handler, gone);
      if (step == Step.IDLE) {
        break;
      }
      progress |= step == Step.MOVED;
    }
    return progress;
  }

  /**
   * The tenant's histories. A manifest part is written once, or again with the same bytes, so the
   * lineage is read again only when the listing of the parts changed.
   */
  private HistoryLineage lineage() throws IOException {
    final var keys = new ArrayList<String>();
    String after = null;
    while (true) {
      final var page = store.list(historiesPrefix, after, PAGE_SIZE);
      page.forEach(object -> keys.add(object.key()));
      if (page.size() < PAGE_SIZE) {
        break;
      }
      after = page.getLast().key();
    }
    if (lineage == null || !keys.equals(lineageKeys)) {
      lineage = Histories.load(store, log.cluster(), log.tenant());
      lineageKeys = keys;
    }
    return lineage;
  }

  /** Where a start without a cursor is, or null if it can't tell yet. */
  private @Nullable Cursor resolve(final LogPath path) throws IOException {
    if (start == Start.LATEST) {
      final var newest = newest(path);
      if (newest != null) {
        try {
          return latest(path, newest);
        } catch (final NoSuchObjectException e) {
          return null;
        }
      }
      LOG.debug("Log {} has no segments yet, so its latest is its earliest", log);
    }
    // Index 0: the reader starts at the lowest index of the path that the store holds.
    return new Cursor(path.current(), 0, Entry.NO_POSITION);
  }

  /**
   * Moves the cursor into the current history. If the cursor is past the last entry that its
   * history shares with the current one, the listener hears of it, and the cursor goes back there.
   */
  private Step followHistory(final LogPath path) {
    final var at = Objects.requireNonNull(cursor);
    if (at.history().equals(path.current())) {
      return Step.AGAIN;
    }
    final var shared = path.shared(at.history());
    if (shared == null) {
      LOG.warn(
          "Log {} has a cursor in history {}, whose manifest isn't in the store (yet)",
          log,
          at.history());
      return Step.IDLE;
    }
    if (at.index() > shared.index() || at.position() > shared.position()) {
      LOG.info(
          "Log {} changed its history from {} to {}; going on from index {} and position {}",
          log,
          at.history(),
          path.current(),
          shared.index(),
          shared.position());
      listener.onHistoryChange(log, at, shared, path.current());
      cursor = new Cursor(path.current(), shared.index(), shared.position());
      return Step.MOVED;
    }
    cursor = new Cursor(path.current(), at.index(), at.position());
    return Step.AGAIN;
  }

  /** Reads the segment with the next entry, or reports a gap, or finds nothing new. */
  private Step step(final LogPath path, final RecordHandler handler, final Set<String> gone)
      throws IOException {
    var at = Objects.requireNonNull(cursor);
    if (at.index() == 0) {
      final long first = firstIndex(path, gone);
      if (first == NONE) {
        return Step.IDLE;
      }
      at = new Cursor(at.history(), first - 1, at.position());
      cursor = at;
    }
    final long next = at.index() + 1;
    final var found = find(path, next, gone);
    final var segment = found.segment();
    if (segment == null) {
      return missing(path, next, found.later());
    }
    holeIndex = NONE;
    try {
      return read(segment, found.limit(), path, handler) ? Step.MOVED : Step.IDLE;
    } catch (final NoSuchObjectException e) {
      LOG.debug("Segment {} is gone, probably replaced by a rollup; listing again", e.key());
      gone.add(segment.key());
      return at.equals(cursor) ? Step.AGAIN : Step.MOVED;
    }
  }

  /**
   * No segment holds the next entry: a retention marker may say it's gone, or a later segment may
   * show a hole, which the reader reports once it has lasted the grace period on its own clock.
   */
  private Step missing(final LogPath path, final long next, final long later) throws IOException {
    final var retention = newestMarker();
    if (retention != null) {
      long gapEnd = retention.firstIndex() - 1;
      long gapEndPosition = retention.firstPosition() - 1;
      if (!retention.history().equals(path.current())) {
        final var shared = path.shared(retention.history());
        if (shared == null) {
          gapEnd = NONE;
        } else if (shared.index() < gapEnd) {
          gapEnd = shared.index();
          gapEndPosition = shared.position();
        }
      }
      if (next <= gapEnd) {
        gap(path, next, gapEnd, gapEndPosition);
        return Step.MOVED;
      }
    }
    if (later == NONE) {
      holeIndex = NONE;
      return Step.IDLE;
    }
    final long now = clock.nanoTime();
    if (holeIndex != next) {
      LOG.debug(
          "Log {} has no segment with index {}, but one from {}; waiting for it", log, next, later);
      holeIndex = next;
      holeSince = now;
    }
    if (now - holeSince < holeGraceNanos) {
      return Step.IDLE;
    }
    holeIndex = NONE;
    gap(path, next, later - 1, Entry.NO_POSITION);
    return Step.MOVED;
  }

  private void gap(final LogPath path, final long from, final long to, final long position) {
    LOG.warn("Log {} lacks entries {} to {} for good; reading on after them", log, from, to);
    listener.onGap(log, from, to);
    final var at = Objects.requireNonNull(cursor);
    cursor = new Cursor(path.current(), to, Math.max(at.position(), position));
  }

  /** Delivers a segment's entries after the cursor, up to the limit of its history on the path. */
  private boolean read(
      final ListedSegment segment,
      final long limit,
      final LogPath path,
      final RecordHandler handler)
      throws IOException {
    final var before = Objects.requireNonNull(cursor);
    final var tail = SegmentFile.readTail(store, segment, log);
    try (final var entries = SegmentFile.body(store, segment, tail, before.position())) {
      for (var entry = entries.next(); entry != null && !stopped; entry = entries.next()) {
        if (entry.index() > limit) {
          break;
        }
        if (entry.index() > Objects.requireNonNull(cursor).index()) {
          deliver(entry, path.current(), handler);
        }
      }
    }
    return !before.equals(cursor);
  }

  private void deliver(final Entry entry, final UUID current, final RecordHandler handler) {
    for (final var record : RecordDecoder.decode(entry, log.partition())) {
      final var at = Objects.requireNonNull(cursor);
      if (record.getPosition() > at.position()) {
        handler.onRecord(log, record);
        cursor = new Cursor(current, at.index(), record.getPosition());
      }
    }
    final var at = Objects.requireNonNull(cursor);
    cursor = new Cursor(current, entry.index(), Math.max(at.position(), entry.lastPosition()));
  }

  /**
   * The segment for an entry: in the directory of the history whose stretch holds it, then in those
   * of later histories on the path, which can hold entries they took over. Also the lowest first
   * index after it of the segments that serve the path, for telling holes.
   */
  private Found find(final LogPath path, final long index, final Set<String> gone)
      throws IOException {
    long later = NONE;
    for (int stretch = path.stretchOf(index); stretch < path.size(); stretch++) {
      final var directory = path.directory(stretch);
      final long limit = path.limit(stretch);
      final var starting = startingAt(directory, index, limit, gone);
      later = lowest(later, starting.later());
      var segment = starting.segment();
      final var head = directory + "@" + index;
      if (segment == null && (starting.later() != NONE || !head.equals(listedWholeFor))) {
        // A segment that starts before the index and holds it, as a rollup. If a whole listing
        // found none, none can come before a segment at or after the index does, which the cheap
        // listing above sees: at the head of a log, the reader lists only after the index.
        segment = containing(directory, index, gone);
        listedWholeFor = segment == null && starting.later() == NONE ? head : null;
      }
      if (segment != null) {
        return new Found(segment, Math.min(segment.lastIndex(), limit), later);
      }
    }
    return new Found(null, NONE, later);
  }

  /**
   * The best segment that starts at the index, listed after the index's key prefix, and the first
   * index of the segment after it, if that serves the path.
   */
  private Found startingAt(
      final String directory, final long index, final long limit, final Set<String> gone)
      throws IOException {
    ListedSegment best = null;
    String after = directory + "%020d".formatted(index);
    while (true) {
      final var page = store.list(directory, after, PAGE_SIZE);
      for (final var object : page) {
        final var segment = ListedSegment.of(object);
        if (segment == null || gone.contains(segment.key())) {
          continue;
        }
        if (segment.firstIndex() != index) {
          return new Found(best, NONE, segment.firstIndex() <= limit ? segment.firstIndex() : NONE);
        }
        best = ListedSegment.better(best, segment);
      }
      if (page.size() < PAGE_SIZE) {
        return new Found(best, NONE, NONE);
      }
      after = page.getLast().key();
    }
  }

  /** The best segment that starts before the index and holds it, as after a rollup. */
  private @Nullable ListedSegment containing(
      final String directory, final long index, final Set<String> gone) throws IOException {
    ListedSegment best = null;
    String after = null;
    while (true) {
      final var page = store.list(directory, after, PAGE_SIZE);
      for (final var object : page) {
        final var segment = ListedSegment.of(object);
        if (segment == null || gone.contains(segment.key())) {
          continue;
        }
        if (segment.firstIndex() >= index) {
          return best;
        }
        if (segment.lastIndex() >= index) {
          best = ListedSegment.better(best, segment);
        }
      }
      if (page.size() < PAGE_SIZE) {
        return best;
      }
      after = page.getLast().key();
    }
  }

  /** The lowest first index of the segments that serve the path, or {@link #NONE}. */
  private long firstIndex(final LogPath path, final Set<String> gone) throws IOException {
    long first = NONE;
    for (int stretch = 0; stretch < path.size(); stretch++) {
      final var directory = path.directory(stretch);
      String after = null;
      boolean found = false;
      while (!found) {
        final var page = store.list(directory, after, PAGE_SIZE);
        for (final var object : page) {
          final var segment = ListedSegment.of(object);
          if (segment != null && !gone.contains(segment.key())) {
            if (segment.firstIndex() <= path.limit(stretch)) {
              first = lowest(first, Math.max(1, segment.firstIndex()));
            }
            found = true;
            break;
          }
        }
        if (page.size() < PAGE_SIZE) {
          break;
        }
        after = page.getLast().key();
      }
    }
    return first;
  }

  /** The segment with the highest index that serves the path, or null if there is none. */
  private @Nullable Newest newest(final LogPath path) throws IOException {
    Newest newest = null;
    for (int stretch = 0; stretch < path.size(); stretch++) {
      final var directory = path.directory(stretch);
      final long limit = path.limit(stretch);
      String after = null;
      while (true) {
        final var page = store.list(directory, after, PAGE_SIZE);
        for (final var object : page) {
          final var segment = ListedSegment.of(object);
          if (segment == null || segment.firstIndex() > limit) {
            continue;
          }
          final long last = Math.min(segment.lastIndex(), limit);
          if (newest == null
              || last > newest.last()
              || last == newest.last()
                  && ListedSegment.PREFERENCE.compare(segment, newest.segment()) > 0) {
            newest = new Newest(segment, last, stretch);
          }
        }
        if (page.size() < PAGE_SIZE) {
          break;
        }
        after = page.getLast().key();
      }
    }
    return newest;
  }

  /** A cursor after the newest segment's last entry that serves the path. */
  private Cursor latest(final LogPath path, final Newest newest) throws IOException {
    final var segment = newest.segment();
    final long position =
        newest.last() < segment.lastIndex()
            ? path.limitPosition(newest.stretch())
            : SegmentFile.readTail(store, segment, log).footer().lastPosition();
    return new Cursor(path.current(), newest.last(), position);
  }

  /** The newest retention marker, by ULID; it lists only after the newest it has seen. */
  private @Nullable RetentionMarker newestMarker() throws IOException {
    String newest = markerKey;
    String after = markerKey;
    while (true) {
      final var page = store.list(retentionPrefix, after, PAGE_SIZE);
      for (final var object : page) {
        if (object.key().endsWith(".json")) {
          newest = object.key();
        }
      }
      if (page.size() < PAGE_SIZE) {
        break;
      }
      after = page.getLast().key();
    }
    if (newest != null && !newest.equals(markerKey)) {
      final var content = store.get(newest);
      if (content.isPresent()) {
        marker = RetentionMarker.fromJson(content.get());
        markerKey = newest;
      }
    }
    return marker;
  }

  private static long lowest(final long a, final long b) {
    if (a == NONE) {
      return b;
    }
    return b == NONE ? a : Math.min(a, b);
  }

  private enum Step {
    /** Delivered records, or moved the cursor past a gap or into another history. */
    MOVED,
    /** Nothing moved, but there is more to try in this poll. */
    AGAIN,
    /** Nothing more to do until the store changes. */
    IDLE
  }

  /**
   * A segment that holds an entry and the last index it serves the path for, and the lowest first
   * index of a later segment ({@link #NONE} if none).
   */
  private record Found(@Nullable ListedSegment segment, long limit, long later) {}

  /** The newest segment that serves the path, its last index on it, and its stretch. */
  private record Newest(ListedSegment segment, long last, int stretch) {}
}
