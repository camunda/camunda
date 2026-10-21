/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.logarchive.leader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.backup.logarchive.format.LogArchiveFrame;
import io.camunda.zeebe.backup.logarchive.format.SegmentIdentity;
import io.camunda.zeebe.backup.logarchive.format.SegmentV1Decoder;
import io.camunda.zeebe.backup.logarchive.format.SegmentV1Encoder;
import io.camunda.zeebe.backup.logarchive.format.TestJournalRecords;
import io.camunda.zeebe.backup.logarchive.model.IndexChunkFile;
import io.camunda.zeebe.backup.logarchive.model.LogArchiveJson;
import io.camunda.zeebe.backup.logarchive.model.LogArchiveKeys;
import io.camunda.zeebe.backup.logarchive.model.LogObjectJson;
import io.camunda.zeebe.backup.logarchive.state.DbLogArchiveState.OpenObject;
import io.camunda.zeebe.backup.logarchive.state.DbLogArchiveState.UnmergedObject;
import io.camunda.zeebe.backup.logarchive.state.LogArchiveMeta;
import io.camunda.zeebe.backup.logarchive.store.LogArchiveStore;
import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.logstreams.log.LogStreamWriter;
import io.camunda.zeebe.logstreams.log.LogStreamWriter.WriteFailure;
import io.camunda.zeebe.protocol.impl.record.value.management.IndexChunk;
import io.camunda.zeebe.protocol.impl.record.value.management.LogArchiveRecord;
import io.camunda.zeebe.protocol.impl.record.value.management.LogObject;
import io.camunda.zeebe.protocol.record.intent.management.LogArchiveIntent;
import io.camunda.zeebe.scheduler.testing.ControlledActorSchedulerExtension;
import io.camunda.zeebe.util.Either;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentMatchers;

final class LogArchiveMergerTest {

  private static final int PARTITION = 2;
  private static final long TERM = 7;
  private static final String CLUSTER_ID = "cluster-1";
  private static final String TENANT_ID = "tenant-1";
  private static final String TIMELINE = "tl-0";
  private static final String PREFIX = LogArchiveKeys.partitionPrefix(TIMELINE, PARTITION);
  private static final SegmentIdentity IDENTITY =
      new SegmentIdentity(CLUSTER_ID, TENANT_ID, PARTITION);
  private static final long FRAME_BYTES = 1024 * 1024;

  @RegisterExtension
  private final ControlledActorSchedulerExtension scheduler =
      new ControlledActorSchedulerExtension();

  @TempDir private Path tempDirectory;

  private final FakeEntrySource source = new FakeEntrySource();
  private final FakeStore store = new FakeStore();
  private final FakeStateView stateView = new FakeStateView();
  private final List<Long> segmentFirstIndexes = new ArrayList<>(List.of(1L));
  private final LogStreamWriter writer = mock(LogStreamWriter.class);
  private final List<LogArchiveRecord> written = new ArrayList<>();
  private int failWrites;
  private LogArchiveMerger merger;

  @BeforeEach
  void setUp() {
    when(writer.tryWrite(any(), ArgumentMatchers.<LogAppendEntry>any()))
        .thenAnswer(
            invocation -> {
              if (failWrites > 0) {
                failWrites--;
                return Either.left(WriteFailure.WRITE_LIMIT_EXHAUSTED);
              }
              final LogAppendEntry entry = invocation.getArgument(1);
              assertThat(entry.recordMetadata().getIntent()).isEqualTo(LogArchiveIntent.MERGE_LOG);
              written.add((LogArchiveRecord) entry.recordValue());
              return Either.right(1L);
            });
  }

  // -----------------------------------------------------------------------------------------------
  // Trigger and range
  // -----------------------------------------------------------------------------------------------

  @Test
  void shouldMergeOnceTheJournalRolledOverAndTheSegmentIsArchived() throws IOException {
    // given - segment [1, 10] is complete, F = 15
    archive(range(1, 4), range(5, 8), range(9, 12), range(13, 15));
    segmentFirstIndexes.add(11L);
    startMerger(100);

    // when
    resumeTimeline();

    // then - the last object boundary at or below 10 is 8
    final var object = singleProposal();
    assertThat(object.getKey()).isEqualTo(LogArchiveKeys.dataKey(1, 8, TERM));
    assertThat(object.getFirstIndex()).isEqualTo(1);
    assertThat(object.getLastIndex()).isEqualTo(8);
    assertThat(object.getEntryCount()).isEqualTo(8);
    assertThat(object.getFirstPosition()).isEqualTo(lowest(1));
    assertThat(object.getLastPosition()).isEqualTo(highest(8));
    assertThat(object.getLastRecordTimestamp()).isEqualTo(timestamp(8));

    final byte[] bytes = store.objects.get(PREFIX + object.getKey());
    assertThat(bytes).isNotNull();
    assertThat(object.getSizeBytes()).isEqualTo(bytes.length);
    assertThat(object.getSha256()).isEqualTo(sha256(bytes));
    final var decoded = SegmentV1Decoder.decode(bytes);
    assertThat(object.getBodyLength()).isEqualTo(decoded.footer().bodyLength());
    assertThat(decoded.footer().latestTimestamp()).isEqualTo(timestamp(8));
    assertThat(decoded.frames())
        .extracting(LogArchiveFrame::index)
        .containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L);
    assertThat(decoded.frames())
        .extracting(LogArchiveFrame::serializedRecord)
        .containsExactlyElementsOf(
            LongStream.rangeClosed(1, 8)
                .mapToObj(i -> source.frame(i).serializedRecord())
                .toList());
    assertThat(merger.state()).isEqualTo(LogArchiveMerger.State.AWAIT_COMMIT);
    try (final var files = Files.list(tempDirectory)) {
      assertThat(files).isEmpty();
    }
  }

  @Test
  void shouldNotMergeBeforeTheJournalRolledOver() {
    // given
    archive(range(1, 4), range(5, 8));
    startMerger(100);

    // when
    resumeTimeline();

    // then
    assertThat(written).isEmpty();
    assertThat(store.objects).isEmpty();
  }

  @Test
  void shouldNotMergeASegmentThatIsNotArchivedYet() {
    // given - segment [1, 10] is complete, but F = 8
    archive(range(1, 4), range(5, 8));
    segmentFirstIndexes.add(11L);
    startMerger(100);

    // when
    resumeTimeline();

    // then
    assertThat(written).isEmpty();
  }

  @Test
  void shouldMergeAnObjectThatSpansIntoTheNextSegmentWithThatSegment() {
    // given - the object [9, 12] crosses the end of segment [1, 10]; M = 8
    archive(8, range(9, 12), range(13, 16), range(17, 22));
    segmentFirstIndexes.addAll(List.of(11L, 21L));
    startMerger(100);

    // when
    resumeTimeline();

    // then - nothing is due for [1, 10], so the run ends at the boundary below 20
    assertThat(singleProposal().getKey()).isEqualTo(LogArchiveKeys.dataKey(9, 16, TERM));
  }

  @Test
  void shouldMergeOnceMergeMaxObjectsAreUnmergedWithoutRollover() {
    // given
    archive(range(1, 2), range(3, 4), range(5, 6), range(7, 8));
    startMerger(3);

    // when
    resumeTimeline();

    // then
    assertThat(singleProposal().getKey()).isEqualTo(LogArchiveKeys.dataKey(1, 6, TERM));
  }

  @Test
  void shouldMergeAtMostMergeMaxObjectsAndContinueRightAfterTheCommit() {
    // given - one complete segment holding five objects
    archive(range(1, 4), range(5, 8), range(9, 12), range(13, 16), range(17, 20));
    segmentFirstIndexes.add(21L);
    startMerger(2);
    resumeTimeline();
    assertThat(proposedRanges()).containsExactly("1..8");

    // when
    commitLastProposal();
    commitLastProposal();
    commitLastProposal();

    // then
    assertThat(proposedRanges()).containsExactly("1..8", "9..16", "17..20");
    assertThat(merger.state()).isEqualTo(LogArchiveMerger.State.IDLE);
  }

  @Test
  void shouldWriteSeveralFramesWhenTheEntriesExceedTheFrameSize() {
    // given
    archive(range(1, 4), range(5, 8));
    segmentFirstIndexes.add(9L);
    startMerger(100, 300);

    // when
    resumeTimeline();

    // then
    final var decoded =
        SegmentV1Decoder.decode(store.objects.get(PREFIX + singleProposal().getKey()));
    assertThat(decoded.frameOffsets()).hasSizeGreaterThan(1);
    assertThat(decoded.frames()).hasSize(8);
  }

  // -----------------------------------------------------------------------------------------------
  // A run of one object
  // -----------------------------------------------------------------------------------------------

  @Test
  void shouldProposeASingleOpenObjectWithoutUploading() {
    // given
    archive(range(1, 8), range(9, 12));
    segmentFirstIndexes.add(9L);
    startMerger(100);

    // when
    resumeTimeline();

    // then
    final var object = singleProposal();
    assertThat(object).isEqualTo(stateView.openObjects().getFirst().object());
    assertThat(store.putCount).isZero();
  }

  @Test
  void shouldProposeASingleSealedObjectFromItsChunk() {
    // given - the object [1, 8] is sealed into chunk 1
    archive(range(1, 8), range(9, 12));
    final var sealed = stateView.open.removeFirst().object();
    final var chunkKey = LogArchiveKeys.indexKey(1, 8);
    stateView.chunks.add(
        new IndexChunk().setChunkNo(1).setKey(chunkKey).setFirstIndex(1).setLastIndex(8));
    store.objects.put(
        PREFIX + chunkKey,
        LogArchiveJson.write(
            new IndexChunkFile(1, TIMELINE, PARTITION, 1, List.of(json(sealed)), List.of())));
    segmentFirstIndexes.add(9L);
    startMerger(100);

    // when
    resumeTimeline();

    // then
    assertThat(singleProposal()).isEqualTo(sealed);
  }

  // -----------------------------------------------------------------------------------------------
  // Commit, rejection and failures
  // -----------------------------------------------------------------------------------------------

  @Test
  void shouldRetryAfterARejection() {
    // given
    archive(range(1, 4), range(5, 8));
    segmentFirstIndexes.add(9L);
    startMerger(100);
    resumeTimeline();
    assertThat(written).hasSize(1);

    // when
    merger.onMergeLogRejected(0);
    scheduler.workUntilDone();
    assertThat(merger.state()).isEqualTo(LogArchiveMerger.State.BACKOFF);
    scheduler.updateClock(Duration.ofSeconds(1));
    scheduler.workUntilDone();

    // then
    assertThat(proposedRanges()).containsExactly("1..8", "1..8");
  }

  @Test
  void shouldLookForTheNextMergeFromTheNewMergedIndexAfterARejection() {
    // given - a merge of an earlier leader was committed meanwhile
    archive(range(1, 4), range(5, 8), range(9, 12));
    segmentFirstIndexes.addAll(List.of(9L, 13L));
    startMerger(100);
    resumeTimeline();
    assertThat(proposedRanges()).containsExactly("1..8");
    markMerged(8);

    // when
    merger.onMergeLogRejected(8);
    scheduler.workUntilDone();
    scheduler.updateClock(Duration.ofSeconds(1));
    scheduler.workUntilDone();

    // then
    assertThat(proposedRanges()).containsExactly("1..8", "9..12");
  }

  @Test
  void shouldRetryAFailedUpload() {
    // given
    archive(range(1, 4), range(5, 8));
    segmentFirstIndexes.add(9L);
    store.failPuts = 2;
    startMerger(100);

    // when
    resumeTimeline();
    for (int i = 0; i < 3; i++) {
      scheduler.updateClock(Duration.ofSeconds(1));
      scheduler.workUntilDone();
    }

    // then
    assertThat(proposedRanges()).containsExactly("1..8");
    assertThat(store.putCount).isEqualTo(3);
  }

  @Test
  void shouldRetryAFailedWrite() {
    // given
    archive(range(1, 4), range(5, 8));
    segmentFirstIndexes.add(9L);
    failWrites = 1;
    startMerger(100);

    // when
    resumeTimeline();
    scheduler.updateClock(Duration.ofSeconds(1));
    scheduler.workUntilDone();

    // then
    assertThat(proposedRanges()).containsExactly("1..8");
  }

  @Test
  void shouldKeepOneMergeInFlight() {
    // given
    archive(range(1, 4), range(5, 8), range(9, 12));
    segmentFirstIndexes.addAll(List.of(9L, 13L));
    startMerger(100);
    resumeTimeline();

    // when - more triggers arrive before the merge is committed
    merger.onLogArchived(12, highest(12));
    merger.onLogArchived(12, highest(12));
    scheduler.workUntilDone();

    // then
    assertThat(proposedRanges()).containsExactly("1..8");
  }

  // -----------------------------------------------------------------------------------------------
  // Reading from the bucket
  // -----------------------------------------------------------------------------------------------

  @Test
  void shouldRebuildTheSameBytesFromTheBucketWhenTheJournalNoLongerHoldsTheRange() {
    // given - the same run merged from the journal
    archive(range(1, 4), range(5, 8), range(9, 12));
    segmentFirstIndexes.add(9L);
    final var expected = expectedMerge(1, 8, 300);

    // and the journal starts after M + 1; the small objects are in the bucket
    uploadArchivedObjects();
    source.firstIndex = 9;
    startMerger(100, 300);

    // when
    resumeTimeline();

    // then
    final var object = singleProposal();
    assertThat(object.getKey()).isEqualTo(LogArchiveKeys.dataKey(1, 8, TERM));
    assertThat(object.getSha256()).isEqualTo(expected.sha256());
    assertThat(object.getSizeBytes()).isEqualTo(expected.sizeBytes());
    assertThat(object.getLastPosition()).isEqualTo(highest(8));
    assertThat(object.getLastRecordTimestamp()).isEqualTo(timestamp(8));
  }

  @Test
  void shouldRetryWhenAnObjectInTheBucketDiffersFromItsHash() {
    // given
    archive(range(1, 4), range(5, 8));
    segmentFirstIndexes.add(9L);
    uploadArchivedObjects();
    store.objects.put(PREFIX + stateView.open.getFirst().object().getKey(), new byte[] {1});
    source.firstIndex = 9;
    startMerger(100);

    // when
    resumeTimeline();

    // then
    assertThat(written).isEmpty();
    assertThat(merger.state()).isEqualTo(LogArchiveMerger.State.BACKOFF);
  }

  // -----------------------------------------------------------------------------------------------
  // When the merger runs
  // -----------------------------------------------------------------------------------------------

  @Test
  void shouldStayIdleOnATimelineStartedBeforeMerging() {
    // given - state written before merging existed has no merged index
    archive(range(1, 4), range(5, 8));
    stateView.meta.setMergedIndex(-1);
    segmentFirstIndexes.add(9L);
    startMerger(1);

    // when
    resumeTimeline();

    // then
    assertThat(written).isEmpty();
    assertThat(merger.state()).isEqualTo(LogArchiveMerger.State.IDLE);
  }

  @Test
  void shouldNotMergeBeforeTheRecoveryCheck() {
    // given
    archive(range(1, 4), range(5, 8));
    segmentFirstIndexes.add(9L);
    startMerger(100);

    // when
    merger.onLogArchived(8, highest(8));
    scheduler.workUntilDone();

    // then
    assertThat(written).isEmpty();
  }

  @Test
  void shouldNotMergeATimelineTheBucketIsAheadOfUntilTheForkStarted() {
    // given
    archive(range(1, 4), range(5, 8));
    segmentFirstIndexes.add(9L);
    startMerger(100);
    merger.onRecoveryChecked(stateView.meta.copy(), true);
    merger.onLogArchived(8, highest(8));
    scheduler.workUntilDone();
    assertThat(written).isEmpty();

    // when - the fork started; its state has its own objects
    stateView.meta.setTimelineId("tl-0.f9");
    merger.onTimelineStarted("tl-0.f9");
    scheduler.workUntilDone();

    // then
    assertThat(written)
        .singleElement()
        .extracting(LogArchiveRecord::getTimelineId)
        .isEqualTo("tl-0.f9");
    assertThat(store.objects)
        .containsKey(
            LogArchiveKeys.partitionPrefix("tl-0.f9", PARTITION)
                + LogArchiveKeys.dataKey(1, 8, TERM));
  }

  @Test
  void shouldDeleteLeftOverTemporaryFilesOnStart() throws IOException {
    // given
    Files.writeString(tempDirectory.resolve("merge-1.seg.tmp"), "left over");

    // when
    startMerger(100);

    // then
    try (final var files = Files.list(tempDirectory)) {
      assertThat(files).isEmpty();
    }
  }

  @Test
  void shouldCloseSourceOnClose() {
    // given
    startMerger(100);

    // when
    merger.closeAsync();
    scheduler.workUntilDone();

    // then
    assertThat(source.closed).isTrue();
  }

  // -----------------------------------------------------------------------------------------------
  // chooseRun
  // -----------------------------------------------------------------------------------------------

  @Test
  void shouldChooseNothingWhenTheRowsDoNotContinueM() {
    assertThat(
            LogArchiveMerger.chooseRun(
                4, 20, List.of(new UnmergedObject(9, 20, 1)), List.of(1L, 11L, 21L), 100))
        .isEmpty();
  }

  @Test
  void shouldSkipSegmentsBelowM() {
    // given - segments [1, 10], [11, 20], current from 21; M = 12
    final var rows = List.of(new UnmergedObject(13, 16, 1), new UnmergedObject(17, 25, 2));

    // when
    final var run = LogArchiveMerger.chooseRun(12, 25, rows, List.of(1L, 11L, 21L), 100);

    // then
    assertThat(run).containsExactly(new UnmergedObject(13, 16, 1));
  }

  // -----------------------------------------------------------------------------------------------

  private void startMerger(final int mergeMaxObjects) {
    startMerger(mergeMaxObjects, FRAME_BYTES);
  }

  private void startMerger(final int mergeMaxObjects, final long mergeFrameBytes) {
    merger =
        new LogArchiveMerger(
            PARTITION,
            CLUSTER_ID,
            TENANT_ID,
            TERM,
            store,
            writer,
            source,
            () -> List.copyOf(segmentFirstIndexes),
            stateView,
            mergeMaxObjects,
            mergeFrameBytes,
            tempDirectory);
    scheduler.submitActor(merger);
    scheduler.workUntilDone();
  }

  /** The publisher's recovery check passed and the archiver resumed the timeline. */
  private void resumeTimeline() {
    merger.onRecoveryChecked(stateView.meta.copy(), false);
    merger.onLogArchived(stateView.meta.getArchivedIndex(), stateView.meta.getArchivedPosition());
    scheduler.workUntilDone();
  }

  private static long[] range(final long first, final long last) {
    return new long[] {first, last};
  }

  private void archive(final long[]... ranges) {
    archive(0, ranges);
  }

  /** Sets the state to M = {@code mergedIndex} and the given archived objects above it. */
  private void archive(final long mergedIndex, final long[]... ranges) {
    stateView.meta =
        new LogArchiveMeta()
            .setTimelineId(TIMELINE)
            .setStartIndex(1)
            .setMergedIndex(mergedIndex)
            .setNextSeq(ranges.length + 2);
    for (final long[] range : ranges) {
      final var object = archivedObject(range[0], range[1]);
      stateView.unmerged.add(new UnmergedObject(range[0], range[1], highest(range[1])));
      stateView.open.add(new OpenObject(object, List.of()));
      stateView.meta.setArchivedIndex(range[1]).setArchivedPosition(highest(range[1]));
    }
  }

  /** The object the archiver would have cut for the range, as recorded in the state. */
  private LogObject archivedObject(final long first, final long last) {
    final var frames = LongStream.rangeClosed(first, last).mapToObj(source::frame).toList();
    final var encoded = SegmentV1Encoder.encode(IDENTITY, TIMELINE, frames, timestamp(last));
    return new LogObject()
        .setKey(LogArchiveKeys.dataKey(first, last, TERM - 1))
        .setTerm(TERM - 1)
        .setFirstIndex(first)
        .setLastIndex(last)
        .setFirstPosition(encoded.footer().firstPosition())
        .setLastPosition(encoded.footer().lastPosition())
        .setEntryCount(last - first + 1)
        .setSizeBytes(encoded.bytes().length)
        .setSha256(encoded.sha256())
        .setBodyLength(encoded.footer().bodyLength())
        .setLastRecordTimestamp(timestamp(last));
  }

  private void uploadArchivedObjects() {
    for (final var open : stateView.open) {
      final var object = open.object();
      final var frames =
          LongStream.rangeClosed(object.getFirstIndex(), object.getLastIndex())
              .mapToObj(source::frame)
              .toList();
      store.objects.put(
          PREFIX + object.getKey(),
          SegmentV1Encoder.encode(IDENTITY, TIMELINE, frames, object.getLastRecordTimestamp())
              .bytes());
    }
  }

  private SegmentV1Encoder.WrittenSegment expectedMerge(
      final long first, final long last, final long frameBytes) {
    final var out = new ByteArrayOutputStream();
    final var segment = SegmentV1Encoder.writer(out, IDENTITY, TIMELINE, frameBytes);
    LongStream.rangeClosed(first, last).mapToObj(source::frame).forEach(segment::append);
    return segment.finish(timestamp(last));
  }

  /** Applies the merge of everything up to {@code mergedIndex} to the fake state. */
  private void markMerged(final long mergedIndex) {
    stateView.meta.setMergedIndex(mergedIndex).setMergedPosition(highest(mergedIndex));
    stateView.unmerged.removeIf(row -> row.lastIndex() <= mergedIndex);
  }

  /** Simulates the processor committing the last MERGE_LOG command. */
  private void commitLastProposal() {
    final var object = written.getLast().getObjects().getFirst();
    markMerged(object.getLastIndex());
    merger.onLogMerged(10, object.getLastIndex(), object.getLastPosition());
    scheduler.workUntilDone();
  }

  private LogObject singleProposal() {
    assertThat(written).hasSize(1);
    final var record = written.getFirst();
    assertThat(record.getTimelineId()).isEqualTo(stateView.meta.getTimelineId());
    assertThat(record.getObjects()).hasSize(1);
    return new LogObject().copy(record.getObjects().getFirst());
  }

  private List<String> proposedRanges() {
    return written.stream()
        .map(r -> r.getObjects().getFirst())
        .map(o -> o.getFirstIndex() + ".." + o.getLastIndex())
        .toList();
  }

  private static LogObjectJson json(final LogObject object) {
    return new LogObjectJson(
        object.getKey(),
        object.getTerm(),
        object.getFirstIndex(),
        object.getLastIndex(),
        object.getFirstPosition(),
        object.getLastPosition(),
        (int) object.getEntryCount(),
        object.getSizeBytes(),
        object.getSha256(),
        object.getBodyLength(),
        object.getLastRecordTimestamp());
  }

  private static long lowest(final long index) {
    return index * 10;
  }

  private static long highest(final long index) {
    return index * 10 + 2;
  }

  private static long timestamp(final long index) {
    return 1_000 + index;
  }

  private static String sha256(final byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (final Exception e) {
      throw new IllegalStateException(e);
    }
  }

  /** Committed log with real Raft application entries {@code firstIndex..}. */
  private static final class FakeEntrySource implements LogArchiverEntrySource {
    private long firstIndex = 1;
    private long nextIndex = 1;
    private boolean closed;

    LogArchiveFrame frame(final long index) {
      final var bytes =
          TestJournalRecords.raftRecordData(
              index, TERM - 1, lowest(index), highest(index), 40, (byte) index);
      return new LogArchiveFrame(
          index, TERM - 1, lowest(index), lowest(index), highest(index), index, bytes);
    }

    @Override
    public long firstIndex() {
      nextIndex = firstIndex;
      return firstIndex;
    }

    @Override
    public void seek(final long index) {
      nextIndex = Math.max(index, firstIndex);
    }

    @Override
    public boolean hasNext() {
      return true;
    }

    @Override
    public LogArchiverEntry next() {
      final long index = nextIndex++;
      return new LogArchiverEntry(frame(index), false, timestamp(index));
    }

    @Override
    public void close() {
      closed = true;
    }
  }

  private static final class FakeStateView implements LogArchiveStateView {
    private LogArchiveMeta meta = new LogArchiveMeta();
    private final List<UnmergedObject> unmerged = new ArrayList<>();
    private final List<OpenObject> open = new ArrayList<>();
    private final List<IndexChunk> chunks = new ArrayList<>();

    @Override
    public LogArchiveMeta meta() {
      return meta.copy();
    }

    @Override
    public List<UnmergedObject> unmergedObjects() {
      return List.copyOf(unmerged);
    }

    @Override
    public List<OpenObject> openObjects() {
      return List.copyOf(open);
    }

    @Override
    public List<IndexChunk> chunks() {
      return List.copyOf(chunks);
    }

    @Override
    public List<LogObject> mergedObjects() {
      return List.of();
    }
  }

  private static final class FakeStore implements LogArchiveStore {
    private final Map<String, byte[]> objects = new LinkedHashMap<>();
    private int failPuts;
    private int putCount;

    @Override
    public CompletableFuture<Void> put(final String key, final byte[] content) {
      throw new UnsupportedOperationException();
    }

    @Override
    public CompletableFuture<Void> putFile(final String key, final Path file) {
      putCount++;
      if (failPuts > 0) {
        failPuts--;
        return CompletableFuture.failedFuture(new RuntimeException("expected"));
      }
      try {
        objects.put(key, Files.readAllBytes(file));
      } catch (final IOException e) {
        throw new UncheckedIOException(e);
      }
      return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Optional<byte[]>> getIfExists(final String key) {
      return CompletableFuture.completedFuture(Optional.ofNullable(objects.get(key)));
    }

    @Override
    public CompletableFuture<byte[]> getRange(final String key, final long offset, final int len) {
      throw new UnsupportedOperationException();
    }

    @Override
    public CompletableFuture<Void> getToFile(final String key, final Path target) {
      throw new UnsupportedOperationException();
    }

    @Override
    public CompletableFuture<List<String>> list(final String prefix) {
      throw new UnsupportedOperationException();
    }

    @Override
    public CompletableFuture<List<String>> listChildPrefixes(final String prefix) {
      throw new UnsupportedOperationException();
    }

    @Override
    public CompletableFuture<Void> closeAsync() {
      return CompletableFuture.completedFuture(null);
    }
  }
}
