/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.compaction;

import java.nio.file.Path;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/** In-memory seams and harness wiring for the cleaner-pass tests. */
final class CompactionTestSupport {

  private CompactionTestSupport() {}

  /** Wires a full {@link CompactionPass} over a directory with in-memory seams. */
  static Harness harness(
      final Path directory,
      final CompactionConfig config,
      final DirtyLogReader dirtyLog,
      final InstantSource clock,
      final LongSupplier lastCommittedPosition,
      final CompactionPass.Fault fault) {
    final var store = new FileManifestStore(directory);
    final var leases = new ReaderLeaseRegistry();
    final BooleanSupplier externalPredicate = () -> true;
    final var trash = new TrashQueue(directory, leases, store::latest, externalPredicate);
    final var pass =
        new CompactionPass(
            directory, config, store, dirtyLog, trash, clock, lastCommittedPosition, fault);
    return new Harness(directory, store, leases, trash, pass);
  }

  /** Holder for a wired compaction stack. */
  record Harness(
      Path directory,
      FileManifestStore store,
      ReaderLeaseRegistry leases,
      TrashQueue trash,
      CompactionPass pass) {}

  /** An in-memory {@link DirtyLogReader} over a fixed, ascending-position record list. */
  static final class ListDirtyLogReader implements DirtyLogReader {
    private final List<CompactionRecord> records;

    ListDirtyLogReader(final List<CompactionRecord> records) {
      this.records = new ArrayList<>(records);
      this.records.sort((a, b) -> Long.compare(a.position(), b.position()));
    }

    @Override
    public void read(
        final long fromExclusive, final long toInclusive, final DirtyRecordVisitor visitor) {
      for (final CompactionRecord record : records) {
        if (record.position() <= fromExclusive) {
          continue;
        }
        if (record.position() > toInclusive) {
          break;
        }
        if (!visitor.visit(record)) {
          return;
        }
      }
    }
  }

  /** A settable {@link InstantSource} for deterministic tombstone-grace tests (no wall-clock). */
  static final class MutableInstantSource implements InstantSource {
    private final AtomicLong millis;

    MutableInstantSource(final long initialMillis) {
      millis = new AtomicLong(initialMillis);
    }

    void advance(final long deltaMillis) {
      millis.addAndGet(deltaMillis);
    }

    void set(final long absoluteMillis) {
      millis.set(absoluteMillis);
    }

    @Override
    public Instant instant() {
      return Instant.ofEpochMilli(millis.get());
    }

    @Override
    public long millis() {
      return millis.get();
    }
  }
}
