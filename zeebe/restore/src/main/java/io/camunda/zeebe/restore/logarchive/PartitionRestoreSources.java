/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore.logarchive;

import io.camunda.zeebe.backup.api.BackupIdentifierWildcard.CheckpointPattern;
import io.camunda.zeebe.backup.api.BackupStatusCode;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.common.BackupIdentifierWildcardImpl;
import io.camunda.zeebe.backup.common.BackupMetadata;
import io.camunda.zeebe.backup.logarchive.model.HandoverFile;
import io.camunda.zeebe.backup.logarchive.model.LogArchiveJson;
import io.camunda.zeebe.backup.logarchive.store.LogArchiveStore;
import io.camunda.zeebe.backup.management.BackupMetadataSyncer;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * What one partition can be restored from, on both sides of the handover.
 *
 * @param partitionId the partition
 * @param oldMetadata the partition's {@code metadata.json} of the old continuous backups, if any
 * @param completedOldBackups the ids of the partition's completed old backups
 * @param handovers the partition's handover records of the old side ({@code handover/<H>.json})
 * @param timelines the log archive timelines that may cover the partition's restore points: those
 *     named by the handover records or the configuration, and the ones forked or restored from them
 */
public record PartitionRestoreSources(
    int partitionId,
    Optional<BackupMetadata> oldMetadata,
    SortedSet<Long> completedOldBackups,
    List<HandoverFile> handovers,
    List<LogArchiveView> timelines) {

  private static final Logger LOG = LoggerFactory.getLogger(PartitionRestoreSources.class);

  public PartitionRestoreSources {
    completedOldBackups = new TreeSet<>(completedOldBackups);
    handovers = List.copyOf(handovers);
    timelines = List.copyOf(timelines);
  }

  /**
   * Loads the sources of partitions 1 to {@code partitionCount}: the old side from the backup store
   * and the archive timelines that descend from the known ones.
   *
   * @param configuredTimeline the configured log archive timeline; searched too, because the
   *     old-side handover file may be missing (the leader died before writing it)
   * @param explicitTimeline the timeline the operator named; if present, the only one searched
   */
  public static Map<Integer, PartitionRestoreSources> load(
      final BackupStore backupStore,
      final LogArchiveStore logArchiveStore,
      final int partitionCount,
      final String configuredTimeline,
      final Optional<String> explicitTimeline) {
    final var partitionIds = IntStream.rangeClosed(1, partitionCount).boxed().toList();
    final var handovers = new HashMap<Integer, List<HandoverFile>>();
    final var seeds = new TreeMap<Integer, Set<String>>();
    for (final int partitionId : partitionIds) {
      final var records =
          backupStore.loadHandoverRecords(partitionId).join().values().stream()
              .map(bytes -> LogArchiveJson.read(bytes, HandoverFile.class))
              .toList();
      handovers.put(partitionId, records);
      final var partitionSeeds = new LinkedHashSet<String>();
      if (explicitTimeline.isPresent()) {
        partitionSeeds.add(explicitTimeline.get());
      } else {
        records.forEach(record -> partitionSeeds.add(record.logArchiveTimeline()));
        partitionSeeds.add(configuredTimeline);
      }
      seeds.put(partitionId, partitionSeeds);
    }
    final var timelines =
        explicitTimeline.isPresent()
            ? explicitTimeline(logArchiveStore, explicitTimeline.get(), partitionIds)
            : LogArchiveTimelines.discover(logArchiveStore, seeds);

    final var sources = new TreeMap<Integer, PartitionRestoreSources>();
    for (final int partitionId : partitionIds) {
      final var source =
          new PartitionRestoreSources(
              partitionId,
              loadMetadata(backupStore, partitionId),
              completedBackups(backupStore, partitionId),
              handovers.getOrDefault(partitionId, List.of()),
              timelines.getOrDefault(partitionId, List.of()));
      LOG.info(
          "Partition {}: {} completed old backups, handovers {}, log archive timelines {}",
          partitionId,
          source.completedOldBackups().size(),
          source.handovers().stream().map(HandoverFile::checkpointId).toList(),
          source.timelines().stream().map(LogArchiveView::timelineId).toList());
      sources.put(partitionId, source);
    }
    return sources;
  }

  private static Map<Integer, List<LogArchiveView>> explicitTimeline(
      final LogArchiveStore store, final String timelineId, final List<Integer> partitionIds) {
    final var views = new HashMap<Integer, List<LogArchiveView>>();
    partitionIds.forEach(
        partitionId ->
            views.put(partitionId, List.of(LogArchiveView.read(store, timelineId, partitionId))));
    return views;
  }

  private static Optional<BackupMetadata> loadMetadata(
      final BackupStore store, final int partitionId) {
    return store
        .loadBackupMetadata(partitionId)
        .join()
        .flatMap(
            bytes -> {
              try {
                return Optional.of(
                    BackupMetadataSyncer.MAPPER.readValue(bytes, BackupMetadata.class));
              } catch (final IOException e) {
                LOG.warn("Failed to parse backup metadata for partition {}", partitionId, e);
                return Optional.empty();
              }
            });
  }

  private static SortedSet<Long> completedBackups(final BackupStore store, final int partitionId) {
    final var wildcard =
        new BackupIdentifierWildcardImpl(
            Optional.empty(), Optional.of(partitionId), CheckpointPattern.any());
    return store.list(wildcard).join().stream()
        .filter(status -> status.statusCode() == BackupStatusCode.COMPLETED)
        .map(status -> status.id().checkpointId())
        .collect(Collectors.toCollection(TreeSet::new));
  }

  /** Whether the partition knows anything of a log archive. */
  public boolean hasLogArchive() {
    return !handovers.isEmpty() || !timelines.isEmpty();
  }
}
