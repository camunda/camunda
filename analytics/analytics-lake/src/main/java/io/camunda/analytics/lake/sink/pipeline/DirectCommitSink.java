/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.sink.pipeline;

import io.camunda.analytics.lake.sink.DataFileResult;
import io.camunda.analytics.lake.sink.Descriptor;
import io.camunda.analytics.lake.sink.DescriptorSink;
import io.camunda.analytics.lake.write.IcebergLakeWriter;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import org.apache.iceberg.AppendFiles;
import org.apache.iceberg.DataFile;
import org.apache.iceberg.DataFiles;
import org.apache.iceberg.FileFormat;
import org.apache.iceberg.Snapshot;
import org.apache.iceberg.Table;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link DescriptorSink} rung 1 — the direct-commit implementation {@link DescriptorSink}'s own
 * javadoc anticipates: registers a {@link Descriptor}'s files with one Iceberg {@link Table} via
 * {@link DataFiles#builder} — using the table's own {@code days(...)} partition spec (schema v2 —
 * see {@code IcebergLakeWriter#INSTANCE_SCHEMA}'s javadoc) and each file's own {@link
 * DataFileResult#epochDay()} as its partition tuple, since a partitioned table's data file must
 * carry exactly one partition value — in one {@link Table#newAppend()}, then stamps the offset,
 * frontier, and origin-position dedup watermark summary properties using the exact same
 * carry-forward rule {@link IcebergLakeWriter#OFFSET_PROPERTY_PREFIX}'s javadoc describes: iceberg
 * does not carry a snapshot's summary forward into the next one, so a commit that only stamped the
 * partition it just flushed would make every <em>other</em> partition's last-known
 * offset/frontier/watermark appear to regress the instant a different partition's descriptor lands
 * here. All three properties are therefore carried forward the same way — dropping any one of them
 * while keeping the others would reintroduce exactly the bug class the offset rule exists to avoid.
 *
 * <p>Idempotence: if the table's currently-committed offset for {@code
 * descriptor.sourcePartition()} is already {@code >=} {@code descriptor.lastOffset()}, {@link
 * #accept} is a no-op — the expected shape of a redelivered descriptor after a crash (see {@link
 * DescriptorSink}'s own javadoc).
 *
 * <p>One instance is meant to be shared by every {@link SinkPipeline} feeding the same table,
 * regardless of source partition (their flush threads are otherwise independent) — {@link #accept}
 * holds {@code table}'s own commit mutex ({@link
 * IcebergLakeWriter#commitLock(org.apache.iceberg.Table)}) for the whole
 * read-current-summary-then-append dance below, so two partitions' flush threads committing to the
 * same table never race each other. That same mutex is also held by {@code
 * io.camunda.analytics.lake.write.LakeCompactor}'s own rewrite/manifest/expire passes against this
 * table (poll-loop thread) — see its class javadoc — so this sink's flush-thread commits and the
 * compactor's poll-thread commits never race each other either.
 */
public final class DirectCommitSink implements DescriptorSink {

  /**
   * Snapshot summary property prefix for the per-partition frontier stamp (see class javadoc).
   * Defined on {@link IcebergLakeWriter}, not here — see {@link
   * IcebergLakeWriter#FRONTIER_PROPERTY_PREFIX}'s own javadoc for why.
   */
  public static final String FRONTIER_PROPERTY_PREFIX = IcebergLakeWriter.FRONTIER_PROPERTY_PREFIX;

  /**
   * Snapshot summary property prefix for the per-Zeebe-partition origin-position dedup watermark
   * stamp (see class javadoc). Defined on {@link IcebergLakeWriter}, not here — see {@link
   * IcebergLakeWriter#ZBPOS_PROPERTY_PREFIX}'s own javadoc for why.
   */
  public static final String ZBPOS_PROPERTY_PREFIX = IcebergLakeWriter.ZBPOS_PROPERTY_PREFIX;

  private static final Logger LOG = LoggerFactory.getLogger(DirectCommitSink.class);

  private final Table table;
  private final ReentrantLock commitLock;

  /**
   * @param table the raw table this sink commits descriptors to
   * @param commitLock {@code table}'s own commit mutex — see {@link
   *     IcebergLakeWriter#commitLock(org.apache.iceberg.Table)}; callers always obtain this from
   *     the same {@link IcebergLakeWriter} that owns {@code table}, so it is shared with {@code
   *     LakeCompactor}'s commits against the same table
   */
  public DirectCommitSink(final Table table, final ReentrantLock commitLock) {
    this.table = table;
    this.commitLock = commitLock;
  }

  @Override
  public void accept(final Descriptor descriptor) {
    if (!descriptor.derivedFiles().isEmpty()) {
      // Derived tables' files must land atomically with the raw files, which a single-table
      // commit cannot do -- descriptors with riders belong to CoordinatedDescriptorSink.
      throw new IllegalArgumentException(
          "DirectCommitSink cannot commit derived-table files atomically; use "
              + "CoordinatedDescriptorSink for descriptors carrying rider output");
    }
    commitLock.lock();
    try {
      acceptLocked(descriptor);
    } finally {
      commitLock.unlock();
    }
  }

  private void acceptLocked(final Descriptor descriptor) {
    table.refresh();
    final Snapshot current = table.currentSnapshot();
    final Map<String, String> priorSummary = current == null ? Map.of() : current.summary();
    final long committed = offsetOf(priorSummary, descriptor.sourcePartition());
    if (descriptor.lastOffset() <= committed) {
      LOG.debug(
          "Skipping already-committed descriptor for {} partition {}: committed offset {} >= "
              + "descriptor's {}",
          descriptor.table(),
          descriptor.sourcePartition(),
          committed,
          descriptor.lastOffset());
      return;
    }

    final AppendFiles append = table.newAppend();
    for (final DataFileResult file : descriptor.files()) {
      append.appendFile(toDataFile(table, file));
    }
    // Carry-forward rule (all three prefixes) -- see class javadoc.
    priorSummary.forEach(
        (key, value) -> {
          if (key.startsWith(IcebergLakeWriter.OFFSET_PROPERTY_PREFIX)
              || key.startsWith(FRONTIER_PROPERTY_PREFIX)
              || key.startsWith(ZBPOS_PROPERTY_PREFIX)) {
            append.set(key, value);
          }
        });
    append.set(
        IcebergLakeWriter.OFFSET_PROPERTY_PREFIX + descriptor.sourcePartition(),
        Long.toString(descriptor.lastOffset()));
    append.set(
        FRONTIER_PROPERTY_PREFIX + descriptor.sourcePartition(),
        Long.toString(descriptor.localFrontierMs()));
    // One lake.zbpos.z<zeebePartitionId> stamp per captured watermark entry (see Descriptor's own
    // javadoc) -- keyed by Zeebe partition id, not descriptor.sourcePartition(); see
    // ZBPOS_PROPERTY_PREFIX's javadoc for why the two ids are not interchangeable here.
    descriptor
        .zeebeWatermarks()
        .forEach(
            (zeebePartitionId, position) ->
                append.set(ZBPOS_PROPERTY_PREFIX + zeebePartitionId, Long.toString(position)));
    append.commit();
  }

  /**
   * {@code table.spec()} is this table's one-and-only {@code days(...)} partition spec (see {@code
   * IcebergLakeWriter#tableOrCreate}); {@code file.epochDay()} is that file's single family day
   * (see {@code io.camunda.analytics.lake.sink.encode.DayRouter}'s javadoc for why every file the
   * sink produces carries exactly one). {@code withPartitionValues} takes the partition's ISO date
   * string, not a raw integer epoch day -- {@code Conversions#fromPartitionString} parses a {@code
   * DATE} partition value as an ISO local date. {@code withMetrics} also sets the record count from
   * the encoder-collected {@link org.apache.iceberg.Metrics}, which must already equal {@code
   * file.rowCount()} (both come from the same encoder {@code finish()} call).
   */
  private static DataFile toDataFile(final Table table, final DataFileResult file) {
    return DataFiles.builder(table.spec())
        .withPath(file.path())
        .withFormat(FileFormat.PARQUET)
        .withPartitionValues(List.of(LocalDate.ofEpochDay(file.epochDay()).toString()))
        .withFileSizeInBytes(file.fileSizeBytes())
        .withMetrics(file.metrics())
        .build();
  }

  private static long offsetOf(final Map<String, String> summary, final int partition) {
    final String value = summary.get(IcebergLakeWriter.OFFSET_PROPERTY_PREFIX + partition);
    return value == null ? -1L : Long.parseLong(value);
  }
}
