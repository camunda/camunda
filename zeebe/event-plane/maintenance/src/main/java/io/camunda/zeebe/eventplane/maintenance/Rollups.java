/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.maintenance;

import static io.camunda.zeebe.eventplane.maintenance.StoredSegment.PUBLISHER_LEVEL;
import static io.camunda.zeebe.eventplane.maintenance.StoredSegment.ROLLUP_LEVEL;

import io.camunda.zeebe.eventplane.segment.EntryOutOfOrderException;
import io.camunda.zeebe.eventplane.segment.InvalidSegmentException;
import io.camunda.zeebe.eventplane.segment.LogId;
import io.camunda.zeebe.eventplane.store.ObjectNames;
import io.camunda.zeebe.eventplane.store.ObjectStore;
import io.camunda.zeebe.eventplane.store.SegmentName;
import io.camunda.zeebe.eventplane.store.Ulid;
import java.io.IOException;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Merges the publisher's segments of one history that are old enough into rollups, and tidies up
 * after rollups, including those that a crash cut short.
 */
final class Rollups {

  private static final Logger LOG = LoggerFactory.getLogger(Rollups.class);

  private final ObjectStore store;
  private final MaintenanceConfig config;
  private final InstantSource clock;
  private final RollupBuilder builder;

  Rollups(final ObjectStore store, final MaintenanceConfig config, final InstantSource clock) {
    this.store = store;
    this.config = config;
    this.clock = clock;
    builder = new RollupBuilder(store);
  }

  /** Rolls up and tidies one history's directory in a log. */
  MaintenanceReport run(final LogId log, final UUID history) throws IOException {
    final var segments = Listing.segments(store, log, history);
    final var publishers = new ArrayList<>(withLevel(segments, PUBLISHER_LEVEL));
    int deleted = 0;

    final var rollups = deleteCoveredRollups(withLevel(segments, ROLLUP_LEVEL));
    deleted += rollups.deleted();
    for (final var rollup : rollups.kept()) {
      deleted += deleteCovered(publishers, rollup);
    }

    var report = new MaintenanceReport(0, deleted, 0);
    final var candidates = oldEnough(publishers);
    var start = candidates.firstEntry();
    while (start != null) {
      final var chain = chainFrom(candidates, start.getValue());
      if (chain.size() > 1) {
        report = report.plus(rollUp(log, history, chain, publishers));
      }
      start = candidates.higherEntry(chain.getLast().lastIndex());
    }
    return report;
  }

  /**
   * Deletes the rollups that an earlier rollup covers: with the same range, the later by ULID goes,
   * as a second maintenance may have written it for the same segments. A later rollup that reaches
   * beyond the earlier one stays, because it holds entries the earlier one doesn't, which no
   * publisher's segment holds any more.
   */
  private Tidied deleteCoveredRollups(final List<StoredSegment> rollups) throws IOException {
    final var kept = new ArrayList<StoredSegment>();
    int deleted = 0;
    for (final var rollup :
        rollups.stream().sorted(Comparator.comparing(r -> r.name().ulid())).toList()) {
      if (kept.stream().anyMatch(earlier -> earlier.covers(rollup))) {
        LOG.info("Deleting the rollup '{}', which an earlier rollup covers", rollup.key());
        store.delete(rollup.key());
        deleted++;
      } else {
        kept.add(rollup);
      }
    }
    return new Tidied(kept, deleted);
  }

  /** Deletes the publishers' segments that the rollup covers, and takes them off the list. */
  private int deleteCovered(final List<StoredSegment> publishers, final StoredSegment rollup)
      throws IOException {
    int deleted = 0;
    for (final var iterator = publishers.iterator(); iterator.hasNext(); ) {
      final var segment = iterator.next();
      if (rollup.covers(segment)) {
        store.delete(segment.key());
        iterator.remove();
        deleted++;
      }
    }
    return deleted;
  }

  /**
   * The publishers' segments that are at least {@code rollupAge} old, by their first index. Of
   * several that start at the same index, the one that reaches furthest, the earlier by ULID if
   * they tie.
   */
  private TreeMap<Long, StoredSegment> oldEnough(final List<StoredSegment> publishers) {
    final Instant threshold = clock.instant().minus(config.rollupAge());
    final var candidates = new TreeMap<Long, StoredSegment>();
    for (final var segment : publishers) {
      if (!segment.uploadedAt().isAfter(threshold)) {
        candidates.merge(segment.firstIndex(), segment, Rollups::reachingFurther);
      }
    }
    return candidates;
  }

  private static StoredSegment reachingFurther(final StoredSegment a, final StoredSegment b) {
    if (a.lastIndex() != b.lastIndex()) {
      return a.lastIndex() > b.lastIndex() ? a : b;
    }
    return a.isUploadedBeforeThan(b) ? a : b;
  }

  /**
   * The segments from {@code first} on, each starting at the index after its predecessor's last,
   * until there is none, or their sizes add up to the target.
   */
  private List<StoredSegment> chainFrom(
      final TreeMap<Long, StoredSegment> candidates, final StoredSegment first) {
    final var chain = new ArrayList<StoredSegment>();
    chain.add(first);
    long bytes = first.size();
    while (bytes < config.rollupTargetBytes()) {
      final var next = candidates.get(chain.getLast().lastIndex() + 1);
      if (next == null) {
        break;
      }
      chain.add(next);
      bytes += next.size();
    }
    return chain;
  }

  /**
   * PUTs the rollup of a chain, and then deletes every publisher's segment that lies within its
   * range, those of the chain and any duplicate.
   */
  private MaintenanceReport rollUp(
      final LogId log,
      final UUID history,
      final List<StoredSegment> chain,
      final List<StoredSegment> publishers)
      throws IOException {
    final RollupBuilder.Rollup rollup;
    try {
      final var built = builder.build(log, history, chain);
      if (built.isEmpty()) {
        LOG.info(
            "Not rolling up the segments {} to {} of {} in history {}, as one of them is gone",
            chain.getFirst().firstIndex(),
            chain.getLast().lastIndex(),
            log,
            history);
        return MaintenanceReport.NONE;
      }
      rollup = built.get();
    } catch (final InvalidSegmentException
        | EntryOutOfOrderException
        | UnexpectedSegmentException e) {
      LOG.warn(
          "Not rolling up the segments {} to {} of {} in history {}, as one of them is not as expected",
          chain.getFirst().firstIndex(),
          chain.getLast().lastIndex(),
          log,
          history,
          e);
      return MaintenanceReport.NONE;
    }

    final var name =
        new SegmentName(
            history,
            rollup.footer().firstIndex(),
            rollup.footer().lastIndex(),
            ROLLUP_LEVEL,
            Ulid.next());
    final var key = ObjectNames.segment(log, name);
    store.put(key, rollup.content());
    LOG.info("Rolled up {} segments into '{}'", chain.size(), key);
    final var segment = new StoredSegment(key, name, rollup.content().length);
    return new MaintenanceReport(1, deleteCovered(publishers, segment), 0);
  }

  private static List<StoredSegment> withLevel(
      final List<StoredSegment> segments, final int level) {
    return segments.stream().filter(segment -> segment.name().level() == level).toList();
  }

  /** The rollups that stay, and how many others were deleted. */
  private record Tidied(List<StoredSegment> kept, int deleted) {}
}
