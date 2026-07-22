/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.sink.pipeline;

import io.camunda.analytics.lake.catalog.LakeCommitCoordinator;
import io.camunda.analytics.lake.catalog.LakeCommitCoordinator.TableChange;
import io.camunda.analytics.lake.sink.DataFileResult;
import io.camunda.analytics.lake.sink.Descriptor;
import io.camunda.analytics.lake.sink.DescriptorSink;
import io.camunda.analytics.lake.write.IcebergLakeWriter;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.apache.iceberg.AppendFiles;
import org.apache.iceberg.DataFile;
import org.apache.iceberg.DataFiles;
import org.apache.iceberg.FileFormat;
import org.apache.iceberg.Snapshot;
import org.apache.iceberg.Table;
import org.apache.iceberg.catalog.Namespace;
import org.apache.iceberg.catalog.TableIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link DescriptorSink} rung 2 — the multi-table successor of {@link DirectCommitSink}: registers
 * a {@link Descriptor}'s raw files <em>and</em> every rider-produced derived table's files (metrics
 * partials) in <b>one atomic transaction</b> via {@link LakeCommitCoordinator#commitAll}, so a raw
 * table and its derived tables can never durably disagree — no fixed commit order, no
 * min-across-tables cut at restart, no crash window between tables (see {@code
 * io.camunda.analytics.lake.catalog}'s package javadoc).
 *
 * <p>Every table in the batch gets the <em>same</em> stamps ({@code lake.offset.p*}, {@code
 * lake.zbpos.z*}, {@code lake.frontier.p*}), each applied over that table's own carried-forward
 * prior summary — the same rules {@link DirectCommitSink}'s javadoc explains, factored so both
 * sinks share one implementation.
 *
 * <p>Idempotence: the committed offset is read from the <em>primary</em> (raw) table only — the
 * batch is atomic, so all its tables always sit at the same cut; a redelivered descriptor is either
 * entirely new or entirely committed. On skip, the descriptor's files are replay-produced orphans
 * and are queued for the coordinator's sweep.
 */
public final class CoordinatedDescriptorSink implements DescriptorSink {

  private static final Logger LOG = LoggerFactory.getLogger(CoordinatedDescriptorSink.class);

  private final LakeCommitCoordinator coordinator;
  private final Namespace namespace;
  private final Function<String, Table> tables;

  /**
   * @param coordinator the shared multi-table committer
   * @param namespace the namespace every table name in descriptors resolves under
   * @param tables live {@link Table} handle per Iceberg table name (primary and derived); handles
   *     are shared with everything else in the process — the coordinator refreshes them after each
   *     batch
   */
  public CoordinatedDescriptorSink(
      final LakeCommitCoordinator coordinator,
      final Namespace namespace,
      final Function<String, Table> tables) {
    this.coordinator = coordinator;
    this.namespace = namespace;
    this.tables = tables;
  }

  @Override
  public void accept(final Descriptor descriptor) {
    final Table primary = tables.apply(descriptor.table());
    primary.refresh();
    final Snapshot current = primary.currentSnapshot();
    final Map<String, String> primarySummary = current == null ? Map.of() : current.summary();
    final long committed =
        offsetOf(
            primarySummary, IcebergLakeWriter.OFFSET_PROPERTY_PREFIX, descriptor.sourcePartition());
    if (descriptor.lastOffset() <= committed) {
      LOG.debug(
          "Skipping already-committed descriptor for {} partition {}: committed offset {} >= "
              + "descriptor's {}; queueing its replay-produced files for sweep",
          descriptor.table(),
          descriptor.sourcePartition(),
          committed,
          descriptor.lastOffset());
      coordinator.enqueueDeletes(allFilePaths(descriptor), "redelivered-descriptor");
      return;
    }

    // Insertion order = primary first, then derived — purely cosmetic (the batch is atomic);
    // the coordinator itself re-sorts by identifier for lock ordering.
    final Map<String, List<DataFileResult>> byTable = new LinkedHashMap<>();
    byTable.put(descriptor.table(), descriptor.files());
    byTable.putAll(descriptor.derivedFiles());

    final List<TableChange> changes = new ArrayList<>(byTable.size());
    byTable.forEach(
        (tableName, files) -> {
          final Table table = tables.apply(tableName);
          changes.add(
              new TableChange(
                  TableIdentifier.of(namespace, tableName),
                  table,
                  staged -> appendWithStamps(staged, files, descriptor)));
        });
    coordinator.commitAll(changes, journalStamps(descriptor));
  }

  /**
   * One table's staged append: the descriptor's files for it, plus the full stamp set applied over
   * this table's own carried-forward prior summary. Runs against the coordinator's staged view of
   * the table — {@code staged.currentSnapshot()} is the freshly-refreshed base the swap will be
   * predicated on.
   */
  private static void appendWithStamps(
      final Table staged, final List<DataFileResult> files, final Descriptor descriptor) {
    final Snapshot base = staged.currentSnapshot();
    final Map<String, String> priorSummary = base == null ? Map.of() : base.summary();
    final AppendFiles append = staged.newAppend();
    for (final DataFileResult file : files) {
      append.appendFile(toDataFile(staged, file));
    }
    // Carry-forward rule (all three prefixes) -- see DirectCommitSink's class javadoc; identical
    // here, per table of the batch.
    priorSummary.forEach(
        (key, value) -> {
          if (key.startsWith(IcebergLakeWriter.OFFSET_PROPERTY_PREFIX)
              || key.startsWith(IcebergLakeWriter.FRONTIER_PROPERTY_PREFIX)
              || key.startsWith(IcebergLakeWriter.ZBPOS_PROPERTY_PREFIX)) {
            append.set(key, value);
          }
        });
    stamp(descriptor).forEach(append::set);
    append.commit();
  }

  /** The descriptor's stamp set — identical for every table of the batch, and for the journal. */
  private static Map<String, String> stamp(final Descriptor descriptor) {
    final Map<String, String> stamps = new LinkedHashMap<>();
    stamps.put(
        IcebergLakeWriter.OFFSET_PROPERTY_PREFIX + descriptor.sourcePartition(),
        Long.toString(descriptor.lastOffset()));
    stamps.put(
        IcebergLakeWriter.FRONTIER_PROPERTY_PREFIX + descriptor.sourcePartition(),
        Long.toString(descriptor.localFrontierMs()));
    descriptor
        .zeebeWatermarks()
        .forEach(
            (zeebePartition, position) ->
                stamps.put(
                    IcebergLakeWriter.ZBPOS_PROPERTY_PREFIX + zeebePartition,
                    Long.toString(position)));
    return stamps;
  }

  private static Map<String, String> journalStamps(final Descriptor descriptor) {
    final Map<String, String> stamps = stamp(descriptor);
    stamps.put(
        "firstOffset.p" + descriptor.sourcePartition(), Long.toString(descriptor.firstOffset()));
    return stamps;
  }

  private static List<String> allFilePaths(final Descriptor descriptor) {
    final List<String> paths = new ArrayList<>();
    descriptor.files().forEach(file -> paths.add(file.path()));
    descriptor.derivedFiles().values().forEach(files -> files.forEach(f -> paths.add(f.path())));
    return paths;
  }

  /** Same registration shape as {@code DirectCommitSink#toDataFile} — see its javadoc. */
  private static DataFile toDataFile(final Table table, final DataFileResult file) {
    return DataFiles.builder(table.spec())
        .withPath(file.path())
        .withFormat(FileFormat.PARQUET)
        .withPartitionValues(List.of(LocalDate.ofEpochDay(file.epochDay()).toString()))
        .withFileSizeInBytes(file.fileSizeBytes())
        .withMetrics(file.metrics())
        .build();
  }

  private static long offsetOf(
      final Map<String, String> summary, final String prefix, final int partition) {
    final String value = summary.get(prefix + partition);
    return value == null ? -1L : Long.parseLong(value);
  }
}
