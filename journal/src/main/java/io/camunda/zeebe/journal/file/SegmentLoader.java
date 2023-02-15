/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.journal.file;

import io.camunda.zeebe.journal.CorruptedJournalException;
import io.camunda.zeebe.journal.JournalException;
import io.camunda.zeebe.util.FileUtil;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Create a segment file. Load a segment from the segment file. */
final class SegmentLoader {
  private static final Logger LOGGER = LoggerFactory.getLogger(SegmentLoader.class);
  private static final ByteOrder ENDIANNESS = ByteOrder.LITTLE_ENDIAN;

  private final SegmentAllocator allocator;
  private final long minFreeDiskSpace;

  SegmentLoader(final int minFreeDiskSpace) {
    this(SegmentAllocator.fill(), minFreeDiskSpace);
  }

  SegmentLoader(final SegmentAllocator allocator, final long minFreeDiskSpace) {
    this.allocator = allocator;
    this.minFreeDiskSpace = minFreeDiskSpace;
  }

  Segment createSegment(
      final Path segmentFile,
      final SegmentDescriptor descriptor,
      final long lastWrittenAsqn,
      final JournalIndex journalIndex) {
    final MappedByteBuffer mappedSegment;

    try {
      mappedSegment = mapNewSegment(segmentFile, descriptor);
    } catch (final IOException e) {
      throw new JournalException(
          String.format("Failed to create new segment file %s", segmentFile), e);
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
      FileUtil.flushDirectory(segmentFile.getParent());
    } catch (final IOException e) {
      throw new JournalException(
          String.format("Failed to flush journal directory after creating segment %s", segmentFile),
          e);
    }

    return loadSegment(segmentFile, mappedSegment, descriptor, lastWrittenAsqn, journalIndex);
  }

  UninitializedSegment createUninitializedSegment(
      final Path segmentFile, final SegmentDescriptor descriptor, final JournalIndex journalIndex) {
    final MappedByteBuffer mappedSegment;

    try {
      mappedSegment = mapNewSegment(segmentFile, descriptor);
    } catch (final IOException e) {
      throw new JournalException(
          String.format("Failed to create new segment file %s", segmentFile), e);
    }

    // while flushing the file's contents ensures its data is present on disk on recovery, it's also
    // necessary to flush the directory to ensure that the file itself is visible as an entry of
    // that directory after recovery
    try {
      FileUtil.flushDirectory(segmentFile.getParent());
    } catch (final IOException e) {
      throw new JournalException(
          String.format("Failed to flush journal directory after creating segment %s", segmentFile),
          e);
    }
    return new UninitializedSegment(
        new SegmentFile(segmentFile.toFile()),
        descriptor.id(),
        descriptor.maxSegmentSize(),
        mappedSegment,
        journalIndex);
  }

  Segment loadExistingSegment(
      final Path segmentFile, final long lastWrittenAsqn, final JournalIndex journalIndex) {
    final var descriptor = readDescriptor(segmentFile);
    final MappedByteBuffer mappedSegment;

    try (final var channel =
        FileChannel.open(segmentFile, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
      mappedSegment = mapSegment(channel, descriptor.maxSegmentSize());
    } catch (final IOException e) {
      throw new JournalException(
          String.format("Failed to load existing segment %s", segmentFile), e);
    }

    return loadSegment(segmentFile, mappedSegment, descriptor, lastWrittenAsqn, journalIndex);
  }

  /* ---- Internal methods ------ */
  private Segment loadSegment(
      final Path file,
      final MappedByteBuffer buffer,
      final SegmentDescriptor descriptor,
      final long lastWrittenAsqn,
      final JournalIndex journalIndex) {
    final SegmentFile segmentFile = new SegmentFile(file.toFile());
    return new Segment(segmentFile, descriptor, buffer, lastWrittenAsqn, journalIndex);
  }

  private MappedByteBuffer mapSegment(final FileChannel channel, final long segmentSize)
      throws IOException {
    final var mappedSegment = channel.map(MapMode.READ_WRITE, 0, segmentSize);
    mappedSegment.order(ENDIANNESS);

    return mappedSegment;
  }

  private SegmentDescriptor readDescriptor(final Path file) {
    final var fileName = file.getFileName().toString();

    try (final FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
      final var fileSize = Files.size(file);
      final byte version = readVersion(channel, fileName);
      final int length = SegmentDescriptor.getEncodingLengthForVersion(version);
      if (fileSize < length) {
        throw new CorruptedJournalException(
            String.format(
                "Expected segment '%s' with version %d to be at least %d bytes long but it only has %d.",
                fileName, version, length, fileSize));
      }

      final ByteBuffer buffer = ByteBuffer.allocate(length);
      final int readBytes = channel.read(buffer, 0);

      if (readBytes != -1 && readBytes < length) {
        throw new JournalException(
            String.format(
                "Expected to read %d bytes of segment '%s' with %d version but only read %d bytes.",
                length, fileName, version, readBytes));
      }

      buffer.flip();
      return new SegmentDescriptor(buffer);
    } catch (final IndexOutOfBoundsException e) {
      throw new JournalException(
          String.format(
              "Expected to read descriptor of segment '%s', but nothing was read.", fileName),
          e);
    } catch (final UnknownVersionException e) {
      throw new CorruptedJournalException(
          String.format("Couldn't read or recognize version of segment '%s'.", fileName), e);
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
      throw new CorruptedJournalException(
          String.format(
              "Expected to read the version byte from segment '%s' but got EOF instead.",
              fileName));
    }

    return buffer.get(0);
  }

  private MappedByteBuffer mapNewSegment(final Path segmentPath, final SegmentDescriptor descriptor)
      throws IOException {
    final var maxSegmentSize = descriptor.maxSegmentSize();

    checkDiskSpace(segmentPath, maxSegmentSize);

    try (final var channel =
        FileChannel.open(
            segmentPath,
            StandardOpenOption.READ,
            StandardOpenOption.WRITE,
            StandardOpenOption.CREATE_NEW)) {
      allocator.allocate(channel, maxSegmentSize);
      return mapSegment(channel, maxSegmentSize);
    } catch (final FileAlreadyExistsException e) {
      LOGGER.warn(
          "Failed to create segment {}: an unused file already existed, and will be replaced",
          segmentPath,
          e);
      Files.delete(segmentPath);
      return mapNewSegment(segmentPath, descriptor);
    }
  }

  private void checkDiskSpace(final Path segmentPath, final int maxSegmentSize) {
    final var available = segmentPath.getParent().toFile().getUsableSpace();
    final var required = Math.max(maxSegmentSize, minFreeDiskSpace);
    if (available < required) {
      throw new JournalException.OutOfDiskSpace(
          "Not enough space to allocate a new journal segment. Required: %s, Available: %s"
              .formatted(required, available));
    }
  }
}
