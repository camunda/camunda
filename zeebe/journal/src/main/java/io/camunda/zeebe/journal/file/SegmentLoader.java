/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.journal.file;

import static java.util.Objects.requireNonNull;

import io.camunda.zeebe.journal.CorruptedJournalException;
import io.camunda.zeebe.journal.JournalException;
import io.camunda.zeebe.journal.JournalRecord;
import io.camunda.zeebe.util.FileUtil;
import io.camunda.zeebe.util.JournalIndexCursor;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.agrona.IoUtil;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Create a segment file. Load a segment from the segment file. */
final class SegmentLoader {
  private static final Logger LOGGER = LoggerFactory.getLogger(SegmentLoader.class);
  private static final ByteOrder ENDIANNESS = ByteOrder.LITTLE_ENDIAN;

  private final SegmentAllocator allocator;
  private final long minFreeDiskSpace;
  private final JournalMetrics metrics;
  private final @Nullable JournalIndexCursor journalIndexCursor;

  SegmentLoader(
      final long minFreeDiskSpace,
      final JournalMetrics metrics,
      final SegmentAllocator allocator,
      final @Nullable JournalIndexCursor journalIndexCursor) {
    this.minFreeDiskSpace = minFreeDiskSpace;
    this.metrics = metrics;
    this.allocator = allocator;
    this.journalIndexCursor = journalIndexCursor;
  }

  Segment createSegment(
      final Path segmentFile,
      final SegmentDescriptor descriptor,
      final long lastWrittenAsqn,
      final JournalIndex journalIndex) {

    final MappedAllocation allocation;
    final var descriptorSerializer = SegmentDescriptorSerializer.currentSerializer();

    try {
      allocation = mapNewSegment(segmentFile, descriptor);
    } catch (final IOException e) {
      throw new JournalException(
          String.format("Failed to create new segment file %s", segmentFile), e);
    }

    try {
      descriptorSerializer.writeTo(descriptor, allocation.buffer());
      allocation.buffer().force();
    } catch (final InternalError e) {
      // Clean up the open channel if forcing fails
      try {
        allocation.channel().close();
      } catch (final Exception ignored) {
      }
      throw new JournalException("Failed to ensure durability...", e);
    }

    try {
      FileUtil.flushDirectory(segmentFile.getParent());
    } catch (final IOException e) {
      try {
        allocation.channel().close();
      } catch (final Exception ignored) {
      }
      throw new JournalException("Failed to flush journal directory...", e);
    }

    return loadSegment(
        segmentFile,
        allocation.buffer(),
        allocation.channel(), // Pass the open channel!
        descriptor,
        descriptorSerializer,
        lastWrittenAsqn,
        journalIndex);
  }

  UninitializedSegment createUninitializedSegment(
      final Path segmentFile, final SegmentDescriptor descriptor, final JournalIndex journalIndex) {
    final MappedAllocation mappedAllocation;

    try {
      mappedAllocation = mapNewSegment(segmentFile, descriptor);
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
        mappedAllocation.buffer(),
        mappedAllocation.channel(),
        journalIndex,
        journalIndexCursor);
  }

  Segment loadExistingSegment(
      final Path segmentFile, final long lastWrittenAsqn, final JournalIndex journalIndex) {

    final var descriptorSerializer = SegmentDescriptorSerializer.currentSerializer();
    FileChannel channel = null;

    try {
      // Open the channel and keep it open
      channel = FileChannel.open(segmentFile, StandardOpenOption.READ, StandardOpenOption.WRITE);

      final var initialMappedLength = Files.size(segmentFile);
      MappedByteBuffer mappedSegment = mapSegment(channel, initialMappedLength);
      final var descriptor =
          readDescriptor(descriptorSerializer, mappedSegment, segmentFile.getFileName().toString());

      if (descriptor.maxSegmentSize() > initialMappedLength) {
        IoUtil.unmap(mappedSegment);
        mappedSegment = mapSegment(channel, descriptor.maxSegmentSize());
      }

      final Segment segment =
          loadSegment(
              segmentFile,
              mappedSegment,
              channel, // Pass the open channel!
              descriptor,
              descriptorSerializer,
              lastWrittenAsqn,
              journalIndex);

      // --------------------------------------------------------------------------------
      // STARTUP RECOVERY: Ensure the lazy-flushed Index matches the durable Log
      // --------------------------------------------------------------------------------
      repairIndex(segment);

      return segment;

    } catch (
        final Exception
            e) { // Catch Exception to cover IOException and RuntimeExceptions from readDescriptor
      // Prevent FD leak on corruption/failure
      if (channel != null) {
        try {
          channel.close();
        } catch (final Exception ignored) {
        }
      }
      throw new JournalException(
          String.format("Failed to load existing segment %s", segmentFile), e);
    }
  }

  /* ---- Internal methods ------ */
  private Segment loadSegment(
      final Path file,
      final MappedByteBuffer buffer,
      final FileChannel channel, // <--- New parameter
      final SegmentDescriptor descriptor,
      final SegmentDescriptorSerializer descriptorSerializer,
      final long lastWrittenAsqn,
      final JournalIndex journalIndex) {

    // 1. Initialize the Memory-Mapped SegmentIndex
    final SegmentIndex segmentIndex;
    try {
      segmentIndex = new SegmentIndex(file, descriptor.maxSegmentSize());
    } catch (final IOException e) {
      throw new JournalException(
          String.format("Failed to initialize SegmentIndex for %s", file), e);
    }

    final SegmentFile segmentFile = new SegmentFile(file.toFile());

    // 2. Inject the index and the file channel into the Segment
    return new Segment(
        segmentFile,
        descriptor,
        descriptorSerializer,
        buffer,
        channel, // <--- Hand it over to the Segment
        lastWrittenAsqn,
        journalIndex,
        segmentIndex, // <--- Hand the index over to the Segment
        metrics,
        journalIndexCursor);
  }

  /**
   * Scans the durable log file to repair the SegmentIndex if the OS crashed before flushing it, or
   * if the index flushed garbage ahead of a torn log write.
   */
  private void repairIndex(final Segment segment) {
    final SegmentIndex index = segment.segmentIndex();
    final SegmentReader reader = segment.createReader();

    try {
      long lastValidLogAsqn = -1;

      while (reader.hasNext()) {
        final JournalRecord record = reader.next();
        final long asqn = record.asqn();

        if (asqn != SegmentedJournal.ASQN_IGNORE) {
          lastValidLogAsqn = asqn;

          // If the log has data that the index missed (lazy flush power loss), append it back to
          // the index now.
          if (asqn > index.getLastIndexedAsqn()) {
            final int dataOffset = reader.buffer().position() - record.data().capacity();
            if (journalIndexCursor != null) {
              // Rebuild the per-batch index entries with their true lowest/highest ASQN range and
              // per-batch offset/length, exactly as the live append path
              // (SegmentWriter#tryUpdateIndex) does. A single record can span an ASQN *range* (a
              // batch of events) and may even contain several batches; indexing it as one
              // (asqn, asqn) entry collapses the range to the record's lowest ASQN, which makes
              // fetches that start mid-range be skipped by SegmentIndex#scanEntries after a
              // restart.
              journalIndexCursor.wrap(record.data(), dataOffset);
              while (journalIndexCursor.hasNext()) {
                journalIndexCursor.next();
                index.appendEntry(
                    journalIndexCursor.currentLowestAsqn(),
                    journalIndexCursor.currentHighestAsqn(),
                    record.index(),
                    journalIndexCursor.currentOffset(),
                    journalIndexCursor.currentLength());
                lastValidLogAsqn =
                    Math.max(lastValidLogAsqn, journalIndexCursor.currentHighestAsqn());
              }
            } else {
              index.appendEntry(asqn, asqn, record.index(), dataOffset, record.data().capacity());
            }
          }
        }
      }

      // If the index has data that the log doesn't have (index flushed, but log tore),
      // truncate the garbage from the index.
      if (index.getLastIndexedAsqn() > lastValidLogAsqn) {
        index.truncate(lastValidLogAsqn);
      }
    } finally {
      reader.close();
    }
  }

  private MappedByteBuffer mapSegment(final FileChannel channel, final long segmentSize)
      throws IOException {
    final var mappedSegment = channel.map(MapMode.READ_WRITE, 0, segmentSize);
    mappedSegment.order(ENDIANNESS);

    return mappedSegment;
  }

  private SegmentDescriptor readDescriptor(
      final SegmentDescriptorSerializer descriptorSerializer,
      final ByteBuffer buffer,
      final String fileName) {
    try {
      return descriptorSerializer.readFrom(buffer);
    } catch (final IndexOutOfBoundsException e) {
      throw new JournalException(
          String.format(
              "Expected to read descriptor of segment '%s', but nothing was read.", fileName),
          e);
    } catch (final UnknownVersionException e) {
      throw new CorruptedJournalException(
          String.format("Couldn't read or recognize version of segment '%s'.", fileName), e);
    }
  }

  private MappedAllocation mapNewSegment(final Path segmentPath, final SegmentDescriptor descriptor)
      throws IOException {
    final var maxSegmentSize = descriptor.maxSegmentSize();
    checkDiskSpace(segmentPath, maxSegmentSize);

    try {
      Files.createFile(segmentPath);
    } catch (final FileAlreadyExistsException e) {
      LOGGER.warn(
          "Failed to create segment {}: an unused file already existed, and will be replaced",
          segmentPath,
          e);
      Files.delete(segmentPath);
      return mapNewSegment(segmentPath, descriptor);
    }

    final var raf = new RandomAccessFile(segmentPath.toFile(), "rw");
    final var channel = raf.getChannel();

    boolean success = false;
    try {
      allocateSegment(maxSegmentSize, channel, raf.getFD());
      raf.setLength(maxSegmentSize);
      final var mappedSegment = mapSegment(channel, maxSegmentSize);

      success = true;
      return new MappedAllocation(mappedSegment, channel);
    } finally {
      // If anything fails during allocation or mapping, prevent FD leak
      if (!success) {
        try {
          channel.close();
        } catch (final Exception ignored) {
        }
        try {
          raf.close();
        } catch (final Exception ignored) {
        }
      }
    }
  }

  private void checkDiskSpace(final Path segmentPath, final int maxSegmentSize) {
    final var parent =
        requireNonNull(
            segmentPath.getParent(),
            () -> String.format("Expected file %s to have a parent but it was null", segmentPath));
    final var available = parent.toFile().getUsableSpace();
    final var required = Math.max(maxSegmentSize, minFreeDiskSpace);
    if (available < required) {
      throw new JournalException.OutOfDiskSpace(
          "Not enough space to allocate a new journal segment. Required: %s, Available: %s"
              .formatted(required, available));
    }
  }

  private void allocateSegment(
      final int maxSegmentSize, final FileChannel channel, final FileDescriptor fileDescriptor)
      throws IOException {
    try (final var ignored = metrics.observeSegmentAllocation()) {
      allocator.allocate(channel, fileDescriptor, maxSegmentSize);
    }
  }

  private record MappedAllocation(MappedByteBuffer buffer, FileChannel channel) {}
}
