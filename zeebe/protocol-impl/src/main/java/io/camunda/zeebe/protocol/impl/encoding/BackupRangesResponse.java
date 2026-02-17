/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.encoding;

import io.camunda.zeebe.protocol.management.BackupRangesResponseDecoder;
import io.camunda.zeebe.protocol.management.BackupRangesResponseEncoder;
import io.camunda.zeebe.protocol.management.BackupRangesResponseEncoder.RangesEncoder;
import io.camunda.zeebe.protocol.management.CheckpointInfoEncoder;
import io.camunda.zeebe.protocol.management.MessageHeaderDecoder;
import io.camunda.zeebe.protocol.management.MessageHeaderEncoder;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import io.camunda.zeebe.util.buffer.BufferReader;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.LongHashSet;

public final class BackupRangesResponse implements BufferReader, BufferWriter {

  private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
  private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();

  private final BackupRangesResponseEncoder bodyEncoder = new BackupRangesResponseEncoder();
  private final BackupRangesResponseDecoder bodyDecoder = new BackupRangesResponseDecoder();

  private List<PartitionBackupRange> ranges = new ArrayList<>();

  @Override
  public int getLength() {
    int rangesLength = 0;
    for (final var range : ranges) {
      rangesLength +=
          RangesEncoder.sbeBlockLength()
              + RangesEncoder.MissingCheckpointsEncoder.HEADER_SIZE
              + (range.missingCheckpoints().size()
                  * RangesEncoder.MissingCheckpointsEncoder.sbeBlockLength());
    }

    return headerEncoder.encodedLength()
        + bodyEncoder.sbeBlockLength()
        + RangesEncoder.HEADER_SIZE
        + rangesLength;
  }

  @Override
  public int write(final MutableDirectBuffer buffer, final int offset) {
    bodyEncoder.wrapAndApplyHeader(buffer, offset, headerEncoder);

    final var rangesEncoder = bodyEncoder.rangesCount(ranges.size());
    for (final var range : ranges) {
      final var rangeEncoder = rangesEncoder.next();
      rangeEncoder.partitionId(range.partitionId());
      writeCheckpointInfo(range.first(), rangeEncoder.first());
      writeCheckpointInfo(range.last(), rangeEncoder.last());
      final var missingCheckpointsEncoder =
          rangesEncoder.missingCheckpointsCount(range.missingCheckpoints().size());
      for (final var checkpointId : range.missingCheckpoints()) {
        missingCheckpointsEncoder.next().checkpointId(checkpointId);
      }
    }
    return bodyEncoder.encodedLength() + headerEncoder.encodedLength();
  }

  private static void writeCheckpointInfo(
      final CheckpointInfo range, final CheckpointInfoEncoder rangeEncoder) {
    if (range != null) {
      rangeEncoder
          .checkpointId(range.checkpointId)
          .firstLogPosition(range.firstLogPosition)
          .checkpointPosition(range.checkpointPosition)
          .checkpointType(range.checkpointType.getValue())
          .checkpointTimestamp(range.checkpointTimestamp.toEpochMilli());
    }
  }

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    bodyDecoder.wrapAndApplyHeader(buffer, offset, headerDecoder);

    final var rangesDecoder = bodyDecoder.ranges();
    ranges = new ArrayList<>(rangesDecoder.count());
    for (final var range : rangesDecoder) {
      final var missingCheckpointsDecoder = range.missingCheckpoints();
      final var missingCheckpoints = new LongHashSet(missingCheckpointsDecoder.count());
      for (final var missingCheckpoint : missingCheckpointsDecoder) {
        missingCheckpoints.add(missingCheckpoint.checkpointId());
      }

      final var first = range.first();
      final var last = range.last();
      ranges.add(
          new PartitionBackupRange(
              range.partitionId(),
              new CheckpointInfo(
                  first.checkpointId(),
                  first.firstLogPosition(),
                  first.checkpointPosition(),
                  CheckpointType.valueOf(first.checkpointType()),
                  Instant.ofEpochMilli(first.checkpointTimestamp())),
              new CheckpointInfo(
                  last.checkpointId(),
                  last.firstLogPosition(),
                  last.checkpointPosition(),
                  CheckpointType.valueOf(last.checkpointType()),
                  Instant.ofEpochMilli(last.checkpointTimestamp())),
              missingCheckpoints));
    }
  }

  public List<PartitionBackupRange> getRanges() {
    return ranges;
  }

  public void setRanges(final List<PartitionBackupRange> ranges) {
    this.ranges = ranges;
  }

  public record CheckpointInfo(
      long checkpointId,
      long firstLogPosition,
      long checkpointPosition,
      CheckpointType checkpointType,
      Instant checkpointTimestamp) {}

  public record PartitionBackupRange(
      int partitionId, CheckpointInfo first, CheckpointInfo last, Set<Long> missingCheckpoints) {}
}
