/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.publisher;

import static io.camunda.zeebe.eventplane.segment.Entry.NO_POSITION;

import io.camunda.zeebe.eventplane.history.Histories;
import io.camunda.zeebe.eventplane.history.HistoryManifest;
import io.camunda.zeebe.eventplane.segment.Footer;
import io.camunda.zeebe.eventplane.segment.Level;
import io.camunda.zeebe.eventplane.segment.LogId;
import io.camunda.zeebe.eventplane.segment.SegmentWriter;
import io.camunda.zeebe.eventplane.store.ObjectNames;
import io.camunda.zeebe.eventplane.store.ObjectStore;
import io.camunda.zeebe.eventplane.store.Ulid;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Publishes one partition's journal in one history: it writes the history's manifest part, then
 * cuts the entries into segments, and for each puts it under a new ULID, stores the upload floor,
 * and announces it. A segment whose put or floor fails stays pending, and the next call tries it
 * again, with the same key and bytes, before reading on. Not thread-safe.
 */
public final class PartitionPublisher implements AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(PartitionPublisher.class);

  /** The level in a segment's name: {@link Level#PUBLISHER}'s code, as the footer has it too. */
  private static final int PUBLISHER_LEVEL = 0;

  private final LogId log;
  private final HistoryManifest manifest;
  private final EntrySource source;
  private final ObjectStore store;
  private final FloorStore floors;
  private final Announcer announcer;
  private final CutPolicy policy;
  private final InstantSource clock;

  private boolean started;
  private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
  private @Nullable SegmentWriter writer;
  private Instant openedAt = Instant.MIN;
  private long bytes;
  private long latestRecordTimestamp;
  private long lastUploadedPosition = NO_POSITION;
  private @Nullable Pending pending;

  /**
   * @param log the partition to publish
   * @param manifest the history to publish it in: that partition's part of the history's manifest
   * @throws MismatchedManifestException if the manifest isn't the log's part of a history
   */
  public PartitionPublisher(
      final LogId log,
      final HistoryManifest manifest,
      final EntrySource source,
      final ObjectStore store,
      final FloorStore floors,
      final Announcer announcer,
      final CutPolicy policy,
      final InstantSource clock) {
    if (!manifest.clusterId().equals(log.cluster())) {
      throw new ManifestClusterMismatchException(log, manifest);
    }
    if (!manifest.physicalTenantId().equals(log.tenant())) {
      throw new ManifestTenantMismatchException(log, manifest);
    }
    if (!manifest.partitions().keySet().equals(Set.of(log.partition()))) {
      throw new ManifestPartitionsMismatchException(log, manifest);
    }
    this.log = log;
    this.manifest = manifest;
    this.source = source;
    this.store = store;
    this.floors = floors;
    this.announcer = announcer;
    this.policy = policy;
    this.clock = clock;
  }

  /**
   * Writes the history's manifest part, which has the same bytes however often it is written, and
   * goes to the entry after the upload floor, or to the source's first entry if there is no floor.
   * If the source was compacted past the floor, it goes to its first entry and warns.
   *
   * @throws PublisherAlreadyStartedException if it is started already
   */
  public void start() throws IOException {
    if (started) {
      throw new PublisherAlreadyStartedException(log);
    }
    Histories.writePart(store, manifest);
    final var floor = floors.load();
    final long first = source.firstIndex();
    source.seek(floor == null ? first : resumeAfter(floor, first));
    lastUploadedPosition = floor == null ? NO_POSITION : floor.position();
    started = true;
  }

  /**
   * Appends the entries the source has, and uploads each segment that is due: by size, or by age
   * once its first entry is older than the policy allows. Returns what it uploaded.
   *
   * @throws PublisherNotStartedException if it isn't started
   */
  public List<Upload> poll() throws IOException {
    if (!started) {
      throw new PublisherNotStartedException(log);
    }
    final var uploads = new ArrayList<Upload>();
    if (pending != null) {
      uploads.add(upload(pending));
    }
    for (var next = source.next(); next != null; next = source.next()) {
      final var segment = writer == null ? open() : writer;
      segment.append(next.entry());
      bytes += next.entry().serializedRecord().length;
      latestRecordTimestamp = Math.max(latestRecordTimestamp, next.latestRecordTimestamp());
      if (bytes >= policy.maxBytes()) {
        uploads.add(cut(segment));
      }
    }
    if (writer != null
        && Duration.between(openedAt, clock.instant()).compareTo(policy.maxAge()) >= 0) {
      uploads.add(cut(writer));
    }
    return uploads;
  }

  /**
   * Like {@link #poll}, and also uploads the segment it has begun, as at a journal's end.
   *
   * @throws PublisherNotStartedException if it isn't started
   */
  public List<Upload> flush() throws IOException {
    final var uploads = new ArrayList<>(poll());
    if (writer != null) {
      uploads.add(cut(writer));
    }
    return uploads;
  }

  /** Drops the segment it has begun; its entries are published again after the floor. */
  @Override
  public void close() throws IOException {
    if (writer != null) {
      writer.close();
      writer = null;
    }
  }

  /** The index after the floor, or {@code first} if the source no longer holds it. */
  private long resumeAfter(final UploadFloor floor, final long first) {
    final long next = floor.index() + 1;
    if (next >= first) {
      return next;
    }
    LOG.warn(
        "Expected the journal of {} to hold entry {}, which follows the upload floor at index {},"
            + " but its first entry is {}: it was compacted past the floor, so entries {} to {} are"
            + " not published",
        log,
        next,
        floor.index(),
        first,
        next,
        first - 1);
    return first;
  }

  private SegmentWriter open() throws IOException {
    buffer.reset();
    bytes = 0;
    latestRecordTimestamp = NO_POSITION;
    openedAt = clock.instant();
    writer = new SegmentWriter(buffer, log, manifest.historyId(), Level.PUBLISHER);
    return writer;
  }

  private Upload cut(final SegmentWriter segment) throws IOException {
    final var footer = segment.finish(latestRecordTimestamp);
    segment.close();
    writer = null;
    pending = new Pending(key(footer), buffer.toByteArray(), footer);
    return upload(pending);
  }

  private String key(final Footer footer) {
    return ObjectNames.segment(
        log,
        manifest.historyId(),
        footer.firstIndex(),
        footer.lastIndex(),
        PUBLISHER_LEVEL,
        Ulid.next());
  }

  private Upload upload(final Pending segment) throws IOException {
    final var footer = segment.footer();
    store.put(segment.key(), segment.content());
    // A segment of entries without records leaves the position where the last upload had it.
    final long position = footer.hasRecords() ? footer.lastPosition() : lastUploadedPosition;
    floors.store(new UploadFloor(manifest.historyId(), footer.lastIndex(), position));
    lastUploadedPosition = position;
    pending = null;
    final var upload = new Upload(segment.key(), segment.content().length, footer);
    announcer.announce(log, upload);
    return upload;
  }

  private record Pending(String key, byte[] content, Footer footer) {}
}
