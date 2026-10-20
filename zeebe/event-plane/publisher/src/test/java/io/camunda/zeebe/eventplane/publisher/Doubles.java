/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.publisher;

import io.camunda.zeebe.eventplane.segment.Identity;
import io.camunda.zeebe.eventplane.store.FileSystemStore;
import io.camunda.zeebe.eventplane.store.ObjectStore;
import io.camunda.zeebe.eventplane.store.StoredObject;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Stand-ins for a publisher's ports, which record what happens to them in one list of events. */
final class Doubles {

  private Doubles() {}

  /** A journal whose entries become committed as a test says. */
  static final class ListSource implements EntrySource {

    final List<Long> seeks = new ArrayList<>();
    private final List<SourcedEntry> entries;
    private int committed;
    private int next;

    ListSource(final List<SourcedEntry> entries) {
      this.entries = entries;
      committed = entries.size();
    }

    /** Commits only the first {@code count} entries. */
    ListSource committed(final int count) {
      committed = Math.min(count, entries.size());
      return this;
    }

    @Override
    public long firstIndex() {
      return entries.isEmpty() ? 1 : entries.getFirst().entry().index();
    }

    @Override
    public void seek(final long index) {
      if (index < firstIndex()) {
        throw new IllegalArgumentException(
            "Expected a seek to the first index %d or later, but got %d"
                .formatted(firstIndex(), index));
      }
      seeks.add(index);
      next = (int) (index - firstIndex());
    }

    @Override
    public @Nullable SourcedEntry next() {
      return next < committed ? entries.get(next++) : null;
    }
  }

  /** What a store was asked to put, and whether it failed to. */
  record Put(String key, byte[] content, boolean failed) {}

  /**
   * A store on the filesystem, which fails as many puts as it is told to, and records them in the
   * list of events.
   */
  static final class FlakyStore implements ObjectStore {

    final List<Put> puts = new ArrayList<>();
    int failures;
    private final FileSystemStore store;
    private final List<String> events;

    FlakyStore(final Path root, final List<String> events) {
      store = new FileSystemStore(root);
      this.events = events;
    }

    @Override
    public URI root() {
      return store.root();
    }

    @Override
    public void put(final String key, final byte[] content) throws IOException {
      if (failures > 0) {
        failures--;
        events.add("failed put " + key);
        puts.add(new Put(key, content.clone(), true));
        throw new IOException("Expected the put to fail, as the test wants");
      }
      events.add("put " + key);
      puts.add(new Put(key, content.clone(), false));
      store.put(key, content);
    }

    @Override
    public Optional<byte[]> get(final String key) throws IOException {
      return store.get(key);
    }

    @Override
    public byte[] getRange(final String key, final long offset, final int length)
        throws IOException {
      return store.getRange(key, offset, length);
    }

    @Override
    public Optional<StoredObject> head(final String key) throws IOException {
      return store.head(key);
    }

    @Override
    public List<StoredObject> list(
        final String prefix, @Nullable final String startAfter, final int limit)
        throws IOException {
      return store.list(prefix, startAfter, limit);
    }

    @Override
    public List<String> listPrefixes(final String prefix) throws IOException {
      return store.listPrefixes(prefix);
    }

    @Override
    public void delete(final String key) throws IOException {
      store.delete(key);
    }
  }

  /** A floor store that fails as many stores as it is told to, and keeps every floor it took. */
  static final class Floors implements FloorStore {

    final List<UploadFloor> stored = new ArrayList<>();
    @Nullable UploadFloor floor;
    int failures;
    private final List<String> events;

    Floors(final List<String> events) {
      this.events = events;
    }

    @Override
    public @Nullable UploadFloor load() {
      return floor;
    }

    @Override
    public void store(final UploadFloor floor) throws IOException {
      if (failures > 0) {
        failures--;
        events.add("failed floor " + floor.index());
        throw new IOException("Expected the floor to fail, as the test wants");
      }
      events.add("floor " + floor.index());
      stored.add(floor);
      this.floor = floor;
    }
  }

  static final class Announcements implements Announcer {

    final List<Upload> uploads = new ArrayList<>();
    private final List<String> events;

    Announcements(final List<String> events) {
      this.events = events;
    }

    @Override
    public void announce(final Identity log, final Upload upload) {
      events.add("announce " + upload.footer().lastIndex());
      uploads.add(upload);
    }
  }

  static final class Clock implements InstantSource {

    private Instant now = Instant.parse("2026-10-20T12:00:00Z");

    void advance(final Duration duration) {
      now = now.plus(duration);
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
