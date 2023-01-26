/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.journal.file;

import java.nio.MappedByteBuffer;

/**
 * Holds a normal segment file that hasn't been written to and that has no {@link
 * SegmentDescriptor}.
 */
public record UninitializedSegment(
    SegmentFile file,
    SegmentDescriptor initialDescriptor,
    MappedByteBuffer buffer,
    JournalIndex journalIndex) {

  /**
   * Creates a proper, initialized segment by writing a {@link SegmentDescriptor } with the given
   * index.
   */
  public Segment initializeForUse(
      final long index, final long lastWrittenAsqn, final long lastWrittenIndex) {
    final var updatedDescriptor =
        SegmentDescriptor.builder()
            .withId(initialDescriptor.id())
            .withIndex(index)
            .withMaxSegmentSize(initialDescriptor.maxSegmentSize())
            .build();
    updatedDescriptor.copyTo(buffer);
    return new Segment(
        file, updatedDescriptor, buffer, lastWrittenIndex, lastWrittenAsqn, journalIndex);
  }
}
