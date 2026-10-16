/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.logarchive.model;

import java.util.List;

/**
 * Sample bucket objects. The values match the golden files in {@code
 * src/test/resources/logarchive/golden} and the examples in spec section 9. Changing a value here
 * means changing the golden files too.
 */
final class BucketJsonFixtures {

  static final String SHA256 = "9c1f5e0b7a2d4c8e91f03a6b5d7c2e4f8a1b9c3d5e7f0a2b4c6d8e0f1a3b5c7d";

  private BucketJsonFixtures() {}

  static LogObjectJson logObject() {
    return new LogObjectJson(
        "data/00000000000002731001-00000000000002731480-t7.rec",
        7,
        2731001,
        2731480,
        4930950,
        4931877,
        480,
        3412880,
        SHA256);
  }

  static ArchivedSnapshotJson archivedSnapshot() {
    return new ArchivedSnapshotJson(
        "snap-2731000", 2731000, 7, 4930000, 4929000, 4928000, 4929500, 3, 88123456);
  }

  static IndexChunkJson indexChunk() {
    return new IndexChunkJson(
        1,
        "index/00000000000002600001-00000000000002731000.json",
        2600001,
        2731000,
        4800000,
        4931877,
        1000,
        1792137000000L,
        1792137600000L,
        SHA256);
  }

  static CoveredCheckpointJson coveredCheckpoint() {
    return new CoveredCheckpointJson(1792137600000L, 4931877, "MARKER");
  }

  static CommitEntry commitLogArchived() {
    return new CommitEntry(
        1,
        "tl-0",
        1,
        4127,
        CommitEntry.Type.LOG_ARCHIVED,
        4931990,
        1792137602113L,
        2731480,
        4931877,
        null,
        null,
        List.of(logObject()),
        List.of(coveredCheckpoint()),
        null,
        null);
  }

  static CommitEntry commitTimelineStarted() {
    return new CommitEntry(
        1,
        "tl-0.r1792137600000",
        1,
        1,
        CommitEntry.Type.TIMELINE_STARTED,
        4100,
        1792137600500L,
        2600000,
        -1,
        2600001L,
        "tl-0",
        List.of(),
        List.of(),
        null,
        null);
  }

  static CommitEntry commitSnapshotArchived() {
    return new CommitEntry(
        1,
        "tl-0",
        1,
        4128,
        CommitEntry.Type.SNAPSHOT_ARCHIVED,
        4932001,
        1792137603000L,
        2731480,
        4931877,
        null,
        null,
        List.of(),
        List.of(),
        archivedSnapshot(),
        null);
  }

  static CommitEntry commitIndexSealed() {
    return new CommitEntry(
        1,
        "tl-0",
        1,
        4129,
        CommitEntry.Type.INDEX_SEALED,
        4932100,
        1792137604000L,
        2731480,
        4931877,
        null,
        null,
        List.of(),
        List.of(),
        null,
        indexChunk());
  }

  static IndexChunkFile indexChunkFile() {
    return new IndexChunkFile(1, "tl-0", 1, 1, List.of(logObject()), List.of(coveredCheckpoint()));
  }

  static RootFile rootFile() {
    return new RootFile(
        1,
        "tl-0",
        "",
        1,
        4100,
        1,
        2731480,
        4931877,
        List.of(indexChunk()),
        List.of(logObject()),
        List.of(coveredCheckpoint()),
        List.of(archivedSnapshot()));
  }

  static LatestHint latestHint() {
    return new LatestHint(1, "tl-0", 1, 4100);
  }

  static SnapshotDescriptorFile snapshotDescriptor() {
    // Deliberately unsorted: the record sorts the files, so the golden file shows them sorted.
    return new SnapshotDescriptorFile(
        "snap-2731000",
        2731000,
        7,
        4930000,
        4929000,
        4928000,
        4929500,
        3,
        88123456,
        List.of("checksum.sha256", "000123.sst", "MANIFEST-000005"));
  }
}
