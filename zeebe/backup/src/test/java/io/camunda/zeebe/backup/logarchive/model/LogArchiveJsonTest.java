/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.logarchive.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

final class LogArchiveJsonTest {

  static Stream<Arguments> bucketObjects() {
    return Stream.of(
        Arguments.of("log-object.json", BucketJsonFixtures.logObject()),
        Arguments.of("log-object-legacy.json", BucketJsonFixtures.legacyLogObject()),
        Arguments.of("log-object-merged.json", BucketJsonFixtures.mergedLogObject()),
        Arguments.of("archived-snapshot.json", BucketJsonFixtures.archivedSnapshot()),
        Arguments.of("index-chunk.json", BucketJsonFixtures.indexChunk()),
        Arguments.of("covered-checkpoint.json", BucketJsonFixtures.coveredCheckpoint()),
        Arguments.of("commit-entry-log-archived.json", BucketJsonFixtures.commitLogArchived()),
        Arguments.of(
            "commit-entry-timeline-started.json", BucketJsonFixtures.commitTimelineStarted()),
        Arguments.of(
            "commit-entry-snapshot-archived.json", BucketJsonFixtures.commitSnapshotArchived()),
        Arguments.of("commit-entry-index-sealed.json", BucketJsonFixtures.commitIndexSealed()),
        Arguments.of("commit-entry-log-merged.json", BucketJsonFixtures.commitLogMerged()),
        Arguments.of("commit-entry-log-released.json", BucketJsonFixtures.commitLogReleased()),
        Arguments.of("index-chunk-file.json", BucketJsonFixtures.indexChunkFile()),
        Arguments.of("root-file.json", BucketJsonFixtures.rootFile()),
        Arguments.of("latest-hint.json", BucketJsonFixtures.latestHint()),
        Arguments.of("timeline-file.json", BucketJsonFixtures.timelineFile()),
        Arguments.of("snapshot-descriptor.json", BucketJsonFixtures.snapshotDescriptor()));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("bucketObjects")
  void shouldWriteBytesOfGoldenFile(final String goldenFile, final Object value)
      throws IOException {
    // given
    final byte[] expected = readGolden(goldenFile);

    // when
    final byte[] actual = LogArchiveJson.write(value);

    // then
    assertThat(actual).isEqualTo(expected);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("bucketObjects")
  void shouldWriteIdenticalBytesWhenWrittenTwice(final String goldenFile, final Object value) {
    // given
    final byte[] first = LogArchiveJson.write(value);

    // when
    final byte[] second = LogArchiveJson.write(value);

    // then
    assertThat(second).isEqualTo(first);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("bucketObjects")
  void shouldReadBackEqualObject(final String goldenFile, final Object value) {
    // given
    final byte[] json = LogArchiveJson.write(value);

    // when
    final Object actual = LogArchiveJson.read(json, value.getClass());

    // then
    assertThat(actual).isEqualTo(value);
  }

  @Test
  void shouldIgnoreUnknownPropertiesWhenReading() {
    // given
    final String json =
        "{\"formatVersion\":1,\"timelineId\":\"tl-0\",\"partitionId\":1,\"rootSeq\":4100,"
            + "\"addedInLaterVersion\":true}";

    // when
    final LatestHint hint =
        LogArchiveJson.read(json.getBytes(StandardCharsets.UTF_8), LatestHint.class);

    // then
    assertThat(hint).isEqualTo(new LatestHint(1, "tl-0", 1, 4100));
  }

  @Test
  void shouldReadCoveredCheckpointWithoutTimestampAsUnknown() {
    // given - as written before the timestamp was recorded
    final String json =
        "{\"checkpointId\":7,\"checkpointPosition\":15,\"checkpointType\":\"MARKER\"}";

    // when
    final var checkpoint =
        LogArchiveJson.read(json.getBytes(StandardCharsets.UTF_8), CoveredCheckpointJson.class);

    // then
    assertThat(checkpoint).isEqualTo(new CoveredCheckpointJson(7, 15, "MARKER", -1));
  }

  @Test
  void shouldReadTimelineFileWithoutTakeoverFieldsAsUnknown() {
    // given - as written before startedAt, parentIndex and parentPosition existed
    final String json =
        "{\"formatVersion\":1,\"timelineId\":\"tl-0.r5\",\"partitionId\":1,"
            + "\"parentTimelineId\":\"tl-0\",\"startIndex\":2731001,\"startedSeq\":1}";

    // when
    final var file = LogArchiveJson.read(json.getBytes(StandardCharsets.UTF_8), TimelineFile.class);

    // then
    assertThat(file).isEqualTo(new TimelineFile(1, "tl-0.r5", 1, "tl-0", 2731001, 1, -1, -1, -1));
  }

  @Test
  void shouldReadTimelineFileWithZeroTakeoverFieldsAsGiven() {
    // given
    final String json =
        "{\"formatVersion\":1,\"timelineId\":\"t\",\"partitionId\":1,\"parentTimelineId\":\"\","
            + "\"startIndex\":1,\"startedSeq\":1,\"startedAt\":0,\"parentIndex\":0,"
            + "\"parentPosition\":0}";

    // when
    final var file = LogArchiveJson.read(json.getBytes(StandardCharsets.UTF_8), TimelineFile.class);

    // then
    assertThat(file).isEqualTo(new TimelineFile(1, "t", 1, "", 1, 1, 0, 0, 0));
  }

  @Test
  void shouldOmitLastRecordTimestampOfLogObjectWhenUnknown() {
    // when
    final Map<String, Object> unknown =
        readAsMap(LogArchiveJson.write(BucketJsonFixtures.logObject()));
    final Map<String, Object> known =
        readAsMap(LogArchiveJson.write(BucketJsonFixtures.mergedLogObject()));

    // then
    assertThat(unknown).doesNotContainKey("lastRecordTimestamp");
    assertThat(known).containsEntry("lastRecordTimestamp", 1792137599000L);
  }

  @Test
  void shouldReadLogObjectWithoutLastRecordTimestampAsUnknown() {
    // given - as written before the field existed
    final byte[] json = readGoldenUnchecked("log-object.json");

    // when
    final var object = LogArchiveJson.read(json, LogObjectJson.class);

    // then
    assertThat(object.lastRecordTimestamp()).isEqualTo(-1L);
  }

  @Test
  void shouldReadRootWithoutMergeAndReleaseFieldsAsNothingMergedOrReleased() {
    // given - as written before merging and releasing existed
    final String json =
        "{\"formatVersion\":1,\"timelineId\":\"tl-0\",\"parentTimelineId\":\"\",\"partitionId\":1,"
            + "\"seq\":4,\"startIndex\":501,\"archivedIndex\":900,\"archivedPosition\":950,"
            + "\"chunks\":[],\"openObjects\":[],\"openCheckpoints\":[],\"snapshots\":[]}";

    // when
    final var root = LogArchiveJson.read(json.getBytes(StandardCharsets.UTF_8), RootFile.class);

    // then
    assertThat(root.mergedIndex()).isEqualTo(500);
    assertThat(root.mergedPosition()).isEqualTo(-1);
    assertThat(root.releasedIndex()).isEqualTo(500);
    assertThat(root.releasedPosition()).isEqualTo(-1);
    assertThat(root.mergedObjects()).isEmpty();
  }

  @Test
  void shouldOmitMergeAndReleaseFieldsOfCommitEntriesOfOtherTypes() {
    // when
    final Map<String, Object> archived =
        readAsMap(LogArchiveJson.write(BucketJsonFixtures.commitLogArchived()));
    final Map<String, Object> merged =
        readAsMap(LogArchiveJson.write(BucketJsonFixtures.commitLogMerged()));
    final Map<String, Object> released =
        readAsMap(LogArchiveJson.write(BucketJsonFixtures.commitLogReleased()));

    // then
    assertThat(archived)
        .doesNotContainKeys("mergedIndex", "mergedPosition", "releasedIndex", "releasedPosition");
    assertThat(merged)
        .containsKeys("mergedIndex", "mergedPosition")
        .doesNotContainKeys("releasedIndex", "releasedPosition");
    assertThat(released)
        .containsKeys("releasedIndex", "releasedPosition")
        .doesNotContainKeys("mergedIndex", "mergedPosition", "objects");
  }

  @Test
  void shouldReadCommitEntryWithoutTakeoverFieldsAsNull() {
    // given - as written before the fields existed
    final String json =
        "{\"formatVersion\":1,\"timelineId\":\"tl-0\",\"partitionId\":1,\"seq\":1,"
            + "\"type\":\"TIMELINE_STARTED\",\"recordPosition\":4100,\"recordTimestamp\":-1,"
            + "\"archivedIndex\":0,\"archivedPosition\":-1,\"startIndex\":1,"
            + "\"parentTimelineId\":\"\"}";

    // when
    final var entry = LogArchiveJson.read(json.getBytes(StandardCharsets.UTF_8), CommitEntry.class);

    // then
    assertThat(entry.startedAt()).isNull();
    assertThat(entry.parentIndex()).isNull();
    assertThat(entry.parentPosition()).isNull();
  }

  @Test
  void shouldOmitOptionalPartsOfCommitEntryThatDoNotApply() {
    // given
    final CommitEntry timelineStarted = BucketJsonFixtures.commitTimelineStarted();

    // when
    final Map<String, Object> json = readAsMap(LogArchiveJson.write(timelineStarted));

    // then
    assertThat(json)
        .containsKeys(
            "startIndex", "parentTimelineId", "startedAt", "parentIndex", "parentPosition");
    assertThat(json).doesNotContainKeys("objects", "checkpoints", "snapshot", "chunk");
  }

  @Test
  void shouldOmitEmptyListsAndNullsOfCommitEntry() {
    // given
    final CommitEntry noParts =
        new CommitEntry(
            1,
            "tl-0",
            1,
            4130,
            CommitEntry.Type.LOG_ARCHIVED,
            4932200,
            1792137605000L,
            2731480,
            4931877,
            null,
            null,
            List.of(),
            List.of(),
            null,
            null);

    // when
    final Map<String, Object> json = readAsMap(LogArchiveJson.write(noParts));

    // then
    assertThat(json)
        .doesNotContainKeys(
            "startIndex", "parentTimelineId", "objects", "checkpoints", "snapshot", "chunk");
    assertThat(LogArchiveJson.read(LogArchiveJson.write(noParts), CommitEntry.class))
        .isEqualTo(noParts);
  }

  @Test
  void shouldAlwaysWriteListsOfRootAndIndexChunkFile() {
    // given
    final RootFile root =
        new RootFile(1, "tl-0", "", 1, 1, 1, 0, -1, List.of(), List.of(), List.of(), List.of());
    final IndexChunkFile chunkFile = new IndexChunkFile(1, "tl-0", 1, 1, List.of(), List.of());

    // when
    final Map<String, Object> rootJson = readAsMap(LogArchiveJson.write(root));
    final Map<String, Object> chunkJson = readAsMap(LogArchiveJson.write(chunkFile));

    // then
    assertThat(rootJson)
        .containsEntry("chunks", List.of())
        .containsEntry("openObjects", List.of())
        .containsEntry("openCheckpoints", List.of())
        .containsEntry("snapshots", List.of());
    assertThat(chunkJson)
        .containsEntry("objects", List.of())
        .containsEntry("checkpoints", List.of());
  }

  @Test
  void shouldReadAbsentListsAsEmptyLists() {
    // given
    final byte[] json =
        "{\"formatVersion\":1,\"timelineId\":\"tl-0\",\"partitionId\":1,\"seq\":2,\"type\":\"INDEX_SEALED\",\"recordPosition\":1,\"recordTimestamp\":1,\"archivedIndex\":0,\"archivedPosition\":-1}"
            .getBytes(StandardCharsets.UTF_8);

    // when
    final CommitEntry entry = LogArchiveJson.read(json, CommitEntry.class);

    // then
    assertThat(entry.objects()).isEmpty();
    assertThat(entry.checkpoints()).isEmpty();
    assertThat(entry.chunk()).isNull();
  }

  @Test
  void shouldSortSnapshotFilesRegardlessOfInputOrder() {
    // given
    final SnapshotDescriptorFile unsorted =
        new SnapshotDescriptorFile("snap-1", 1, 1, 1, 1, 1, 1, 2, 10, List.of("b.sst", "a.sst"));
    final SnapshotDescriptorFile sorted =
        new SnapshotDescriptorFile("snap-1", 1, 1, 1, 1, 1, 1, 2, 10, List.of("a.sst", "b.sst"));

    // when
    final byte[] fromUnsorted = LogArchiveJson.write(unsorted);

    // then
    assertThat(fromUnsorted).isEqualTo(LogArchiveJson.write(sorted));
    assertThat(readAsMap(fromUnsorted)).containsEntry("files", List.of("a.sst", "b.sst"));
  }

  private static byte[] readGolden(final String name) throws IOException {
    try (final InputStream in =
        LogArchiveJsonTest.class.getResourceAsStream("/logarchive/golden/" + name)) {
      assertThat(in).as("golden file %s", name).isNotNull();
      return in.readAllBytes();
    }
  }

  private static byte[] readGoldenUnchecked(final String name) {
    try {
      return readGolden(name);
    } catch (final IOException e) {
      throw new AssertionError(e);
    }
  }

  private static Map<String, Object> readAsMap(final byte[] json) {
    return LogArchiveJson.read(json, Map.class);
  }
}
