/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.sink;

import java.util.List;
import java.util.Map;

/**
 * The control-path message for one finished flush of one table: which files now exist and what
 * source range they cover. This is the unit the committer (or today's direct commit) turns into one
 * atomic Iceberg commit of files + offset stamp + frontier stamp + origin-position dedup watermark
 * stamps — rows and position land together or not at all.
 *
 * @param table Iceberg table name
 * @param sourcePartition event-bridge partition id these rows came from
 * @param files the flush's data files (primary day file(s) + optional spill file)
 * @param firstOffset first source offset covered by this flush (inclusive)
 * @param lastOffset last source offset covered (inclusive) — the offset stamp's new value
 * @param localFrontierMs this partition's frontier at flush time: min start of open instances, or
 *     the event-time watermark when none are open
 * @param zeebeWatermarks the origin-position dedup watermark {@code
 *     io.camunda.analytics.lake.translate.LakeTranslator#watermarkSnapshot()} reported at seal
 *     time, keyed by Zeebe partition id — the committer stamps one {@code lake.zbpos.z*} property
 *     per entry (see {@code
 *     io.camunda.analytics.lake.write.IcebergLakeWriter#ZBPOS_PROPERTY_PREFIX}'s javadoc)
 * @param derivedFiles files produced by seal riders for <em>derived</em> tables (metrics partials),
 *     keyed by Iceberg table name — computed from exactly the raw rows in {@link #files}, so they
 *     cover the same offset range and must land in the same atomic commit (see {@code
 *     io.camunda.analytics.lake.catalog}'s package javadoc); empty when no rider is wired
 */
public record Descriptor(
    String table,
    int sourcePartition,
    List<DataFileResult> files,
    long firstOffset,
    long lastOffset,
    long localFrontierMs,
    Map<Integer, Long> zeebeWatermarks,
    Map<String, List<DataFileResult>> derivedFiles) {

  public Descriptor {
    files = List.copyOf(files);
    zeebeWatermarks = Map.copyOf(zeebeWatermarks);
    derivedFiles = Map.copyOf(derivedFiles);
  }

  /** Rider-less descriptor: raw files only, no derived tables. */
  public Descriptor(
      final String table,
      final int sourcePartition,
      final List<DataFileResult> files,
      final long firstOffset,
      final long lastOffset,
      final long localFrontierMs,
      final Map<Integer, Long> zeebeWatermarks) {
    this(
        table,
        sourcePartition,
        files,
        firstOffset,
        lastOffset,
        localFrontierMs,
        zeebeWatermarks,
        Map.of());
  }
}
