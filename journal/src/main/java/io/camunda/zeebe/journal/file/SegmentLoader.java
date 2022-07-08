/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.journal.file;

import io.camunda.zeebe.journal.JournalException;
import io.camunda.zeebe.journal.file.record.CorruptedLogException;
import io.camunda.zeebe.util.FileUtil;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.OpenOption;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Create a segment file. Load a segment from the segment file. */
public class SegmentLoader {

  private static final ByteOrder ENDIANNESS = ByteOrder.LITTLE_ENDIAN;
  private final long lastWrittenIndex;
  private final JournalIndex journalIndex;

  public SegmentLoader(final long lastWrittenIndex, final JournalIndex journalIndex) {
    this.lastWrittenIndex = lastWrittenIndex;
    this.journalIndex = journalIndex;
  }

  JournalSegment createSegment(final JournalSegmentDescriptor descriptor, final File segmentFile) {
    final MappedByteBuffer mappedSegment;

    try {
      mappedSegment = mapNewSegment(segmentFile, descriptor);
    } catch (final IOException e) {
      throw new JournalException(String.format("Failed to map new segment %s", segmentFile), e);
    }

    try {
      descriptor.copyTo(mappedSegment);
      mappedSegment.force();
    } catch (final InternalError e) {
      throw new JournalException(
          String.format(
              "Failed to ensure durability of segment %s with descriptor %s, rolling back",
              segmentFile, descriptor),
          e);
    }

    // while flushing the file's contents ensures its data is present on disk on recovery, it's also
    // necessary to flush the directory to ensure that the file itself is visible as an entry of
    // that directory after recovery
    try {
      final var directory = segmentFile.toPath().getParent();
      FileUtil.flushDirectory(directory);
    } catch (final IOException e) {
      throw new JournalException(
          String.format("Failed to flush journal directory after creating segment %s", segmentFile),
          e);
    }

    return loadSegment(segmentFile, mappedSegment, descriptor);
  }

  JournalSegment loadExistingSegment(final File segmentFile) {
    final var descriptor = readDescriptor(segmentFile);
    final MappedByteBuffer mappedSegment;

    try {
      mappedSegment = mapSegment(segmentFile, descriptor, Collections.emptySet());
    } catch (final IOException e) {
      throw new JournalException(
          String.format("Failed to load existing segment %s", segmentFile), e);
    }

    return loadSegment(segmentFile, mappedSegment, descriptor);
  }

  /* ---- Internal methods ------ */
  private JournalSegment loadSegment(
      final File file, final MappedByteBuffer buffer, final JournalSegmentDescriptor descriptor) {
    final JournalSegmentFile segmentFile = new JournalSegmentFile(file);
    return new JournalSegment(segmentFile, descriptor, buffer, lastWrittenIndex, journalIndex);
  }

  private MappedByteBuffer mapSegment(
      final File segmentFile,
      final JournalSegmentDescriptor descriptor,
      final Set<OpenOption> extraOptions)
      throws IOException {
    final var options = new HashSet<>(extraOptions);
    options.add(StandardOpenOption.READ);
    options.add(StandardOpenOption.WRITE);

    try (final var channel = FileChannel.open(segmentFile.toPath(), options)) {
      final var mappedSegment = channel.map(MapMode.READ_WRITE, 0, descriptor.maxSegmentSize());
      mappedSegment.order(ENDIANNESS);

      return mappedSegment;
    }
  }

  private JournalSegmentDescriptor readDescriptor(final File file) {
    try (final FileChannel channel = FileChannel.open(file.toPath(), StandardOpenOption.READ)) {
      final byte version = readVersion(channel, file.getName());
      final int length = JournalSegmentDescriptor.getEncodingLengthForVersion(version);
      if (file.length() < length) {
        throw new CorruptedLogException(
            String.format(
                "Expected segment '%s' with version %d to be at least %d bytes long but it only has %d.",
                file.getName(), version, length, file.length()));
      }

      final ByteBuffer buffer = ByteBuffer.allocate(length);
      final int readBytes = channel.read(buffer, 0);

      if (readBytes != -1 && readBytes < length) {
        throw new JournalException(
            String.format(
                "Expected to read %d bytes of segment '%s' with %d version but only read %d bytes.",
                length, file.getName(), version, readBytes));
      }

      buffer.flip();
      return new JournalSegmentDescriptor(buffer);
    } catch (final IndexOutOfBoundsException e) {
      throw new JournalException(
          String.format(
              "Expected to read descriptor of segment '%s', but nothing was read.", file.getName()),
          e);
    } catch (final UnknownVersionException e) {
      throw new CorruptedLogException(
          String.format("Couldn't read or recognize version of segment '%s'.", file.getName()), e);
    } catch (final IOException e) {
      throw new JournalException(e);
    }
  }

  private byte readVersion(final FileChannel channel, final String fileName) throws IOException {
    final ByteBuffer buffer = ByteBuffer.allocate(1);
    final int readBytes = channel.read(buffer);

    if (readBytes == 0) {
      throw new JournalException(
          String.format(
              "Expected to read the version byte from segment '%s' but nothing was read.",
              fileName));
    } else if (readBytes == -1) {
      throw new CorruptedLogException(
          String.format(
              "Expected to read the version byte from segment '%s' but got EOF instead.",
              fileName));
    }

    return buffer.get(0);
  }

  private MappedByteBuffer mapNewSegment(
      final File segmentFile, final JournalSegmentDescriptor descriptor) throws IOException {
    try {
      return mapSegment(segmentFile, descriptor, Set.of(StandardOpenOption.CREATE_NEW));
    } catch (final FileAlreadyExistsException e) {
      // assuming we haven't written in that segment, just overwrite it; if we have, we may be able
      // reuse, but that's up to the caller
      if (lastWrittenIndex >= descriptor.index()) {
        throw new JournalException(
            String.format(
                "Failed to create journal segment %s, as it already exists, and the last written "
                    + "index %d indicates we've already written to it",
                segmentFile, lastWrittenIndex),
            e);
      }

      return mapSegment(segmentFile, descriptor, Set.of(StandardOpenOption.TRUNCATE_EXISTING));
    }
  }
}
