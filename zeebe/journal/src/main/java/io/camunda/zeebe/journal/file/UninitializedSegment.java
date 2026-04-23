/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.journal.file;

import io.camunda.zeebe.journal.JournalException;
import io.camunda.zeebe.util.JournalIndexCursor;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

/**
 * Holds a normal segment file that hasn't been written to and that has no {@link
 * SegmentDescriptor}.
 */
record UninitializedSegment(
    SegmentFile file,
    long segmentId,
    int maxSegmentSize,
    MappedByteBuffer buffer,
    FileChannel channel,
    JournalIndex journalIndex,
    JournalIndexCursor journalIndexCursor) {

  /**
   * Creates a proper, initialized segment by writing a {@link SegmentDescriptor } with the given
   * index.
   */
  public Segment initializeForUse(
      final long index, final long lastWrittenAsqn, final JournalMetrics metrics) {
    final var updatedDescriptor =
        SegmentDescriptor.builder()
            .withId(segmentId)
            .withIndex(index)
            .withMaxSegmentSize(maxSegmentSize)
            .build();
    final var descriptorSerializer = SegmentDescriptorSerializer.currentSerializer();
    descriptorSerializer.writeTo(updatedDescriptor, buffer);

    // Initialize the SegmentIndex now that the segment is formally in use
    final SegmentIndex segmentIndex;
    try {
      segmentIndex = new SegmentIndex(file.file().toPath(), maxSegmentSize);
    } catch (final IOException e) {
      throw new JournalException(
          String.format("Failed to initialize SegmentIndex for %s", file.file()), e);
    }

    return new Segment(
        file,
        updatedDescriptor,
        descriptorSerializer,
        buffer,
        channel,
        lastWrittenAsqn,
        journalIndex,
        segmentIndex,
        metrics,
        journalIndexCursor);
  }
}
