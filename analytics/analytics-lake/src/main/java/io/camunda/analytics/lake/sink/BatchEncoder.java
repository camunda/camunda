/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.sink;

/**
 * Encodes sorted rows into one Parquet data file. One instance per open output file; created by the
 * {@link Factory}, fed ranges of {@link SortedRun}s by the day router (each appended range becomes
 * one Parquet row group — align row-group size with segment capacity), finished exactly once. Flush
 * thread only; implementation allocations are quarantined there and budgeted.
 *
 * <p>This is the seam between our batch world and the file format: rung 1 = iceberg-parquet via a
 * flyweight row view (library computes field IDs, column metrics, bloom filters, column indexes);
 * rung 1.5 = driving parquet-java's column writers directly (no boxing) behind this same interface;
 * the L1 merger reuses whichever implementation is current, so file quality is uniform at every
 * ladder level by construction.
 */
public interface BatchEncoder {

  /** Appends the sorted rows {@code [fromIndex, toIndex)} as the next row group. */
  void append(SortedRun run, int fromIndex, int toIndex);

  /** Writes the footer, completes the file, returns what the descriptor needs. */
  DataFileResult finish();

  /** Abandons the file (crash/shutdown path); safe to call after a failed append. */
  void abort();

  interface Factory {
    /**
     * Opens an encoder for a new data file of {@code schema}'s table holding rows of family day
     * {@code epochDay} — epoch days, UTC, and may legitimately be negative (a day before
     * 1970-01-01). Every file this produces carries exactly one family day: the tables are
     * partitioned by {@code days(...)} on their family-day column, and a partitioned table's data
     * file must carry exactly one partition tuple, so a "mixed day" file is no longer a legal
     * output — {@link io.camunda.analytics.lake.sink.encode.DayRouter} routes any family day beyond
     * its concurrently-open slots to its own dedicated one-shot file instead of a shared spill file
     * (see that class's javadoc).
     */
    BatchEncoder newFile(TableSchema schema, long epochDay);
  }
}
