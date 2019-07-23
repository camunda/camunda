/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.impl.log.fs;

import static io.zeebe.dispatcher.impl.PositionUtil.partitionId;
import static io.zeebe.dispatcher.impl.PositionUtil.partitionOffset;
import static io.zeebe.dispatcher.impl.PositionUtil.position;
import static io.zeebe.logstreams.impl.log.fs.FsLogSegment.END_OF_SEGMENT;
import static io.zeebe.logstreams.impl.log.fs.FsLogSegment.INSUFFICIENT_CAPACITY;
import static io.zeebe.logstreams.impl.log.fs.FsLogSegment.NO_DATA;
import static io.zeebe.logstreams.impl.log.fs.FsLogSegmentDescriptor.METADATA_LENGTH;

import io.zeebe.logstreams.impl.Loggers;
import io.zeebe.logstreams.spi.LogStorage;
import io.zeebe.logstreams.spi.ReadResultProcessor;
import io.zeebe.util.FileUtil;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;

public class FsLogStorage implements LogStorage {
  public static final Logger LOG = Loggers.LOGSTREAMS_LOGGER;

  private static final int STATE_CREATED = 0;
  private static final int STATE_OPENED = 1;
  private static final int STATE_CLOSED = 2;

  private static final String ERROR_MSG_APPEND_BLOCK_SIZE =
      "Expected to append block with smaller block size then %d, but actual block size was %d.";

  protected final FsLogStorageConfiguration config;
  private final ReadResultProcessor defaultReadResultProcessor = (buffer, readResult) -> readResult;
  protected volatile int state = STATE_CREATED;
  /** Readable log segments */
  private FsLogSegments logSegments;

  private FsLogSegment currentSegment;
  private int dirtySegmentId = -1;

  public FsLogStorage(final FsLogStorageConfiguration cfg) {
    this.config = cfg;
  }

  @Override
  public long append(final ByteBuffer buffer) throws IOException {
    ensureOpenedStorage();
    if (currentSegment == null) {
      throw new IllegalStateException("Current segment is not initialized.");
    }

    final int size = currentSegment.getSize();
    final int capacity = currentSegment.getCapacity();
    final int remainingCapacity = capacity - size;
    final int requiredCapacity = buffer.remaining();

    final int segmentSize = config.getSegmentSize();
    if (requiredCapacity > segmentSize) {
      throw new IllegalArgumentException(
          String.format(ERROR_MSG_APPEND_BLOCK_SIZE, segmentSize, requiredCapacity));
    }

    if (remainingCapacity < requiredCapacity) {
      onSegmentFilled();
    }

    final int appendResult = currentSegment.append(buffer);
    final long opresult = position(currentSegment.getSegmentId(), appendResult);
    markSegmentAsDirty(currentSegment);

    return opresult;
  }

  @Override
  public void delete(long address) {
    ensureOpenedStorage();

    final int segmentId = partitionId(address);

    final int firstSegmentId = logSegments.initialSegmentId;
    final int lastSegmentId = logSegments.getLastSegmentId();
    if (segmentId > firstSegmentId && segmentId <= lastSegmentId) {
      // segment id has to be larger then initial id,
      // since we don't delete data within a segment
      for (int i = logSegments.initialSegmentId; i < segmentId; i++) {
        final FsLogSegment segmentToDelete = logSegments.getSegment(i);
        if (segmentToDelete != null) {
          segmentToDelete.closeSegment();
          segmentToDelete.delete();
        }
      }
      final int diff = segmentId - firstSegmentId;
      LOG.info("Deleted {} segments from log storage ({} to {}).", diff, firstSegmentId, segmentId);
      dirtySegmentId = Math.max(dirtySegmentId, segmentId);
      logSegments.removeSegmentsUntil(segmentId);
    }
  }

  @Override
  public long read(final ByteBuffer readBuffer, final long addr) {
    return read(readBuffer, addr, defaultReadResultProcessor);
  }

  @Override
  public long read(
      final ByteBuffer readBuffer, final long addr, final ReadResultProcessor processor) {
    ensureOpenedStorage();

    final int segmentId = partitionId(addr);
    final int segmentOffset = partitionOffset(addr);

    final FsLogSegment segment = logSegments.getSegment(segmentId);

    long opStatus = OP_RESULT_INVALID_ADDR;

    if (segment != null) {
      final int readResult = segment.readBytes(readBuffer, segmentOffset);

      if (readResult >= 0) {
        // processing
        final int processingResult = processor.process(readBuffer, readResult);
        opStatus =
            processingResult < 0
                ? processingResult
                : position(segmentId, segmentOffset + processingResult);

      } else if (readResult == END_OF_SEGMENT) {
        final long nextAddr = position(segmentId + 1, METADATA_LENGTH);
        // move to next segment
        return read(readBuffer, nextAddr, processor);
      } else if (readResult == NO_DATA) {
        opStatus = OP_RESULT_NO_DATA;
      } else if (readResult == INSUFFICIENT_CAPACITY) {
        // read buffer has no remaining capacity
        opStatus = 0L;
      }
    }

    return opStatus;
  }

  @Override
  public boolean isByteAddressable() {
    return true;
  }

  @Override
  public void open() throws IOException {
    ensureNotOpenedStorage();

    final String path = config.getPath();
    final File logDir = new File(path);
    logDir.mkdirs();

    initLogSegments(logDir);

    checkConsistency();

    state = STATE_OPENED;
  }

  @Override
  public void close() {
    ensureOpenedStorage();

    logSegments.closeAll();

    if (config.isDeleteOnClose()) {
      final String logPath = config.getPath();
      try {
        FileUtil.deleteFolder(logPath);
      } catch (final Exception e) {
        LOG.error("Failed to delete folder {}: {}", logPath, e);
      }
    }

    dirtySegmentId = -1;

    state = STATE_CLOSED;
  }

  @Override
  public boolean isOpen() {
    return state == STATE_OPENED;
  }

  @Override
  public boolean isClosed() {
    return state == STATE_CLOSED;
  }

  @Override
  public long getFirstBlockAddress() {
    ensureOpenedStorage();

    final FsLogSegment firstSegment = logSegments.getFirst();
    if (firstSegment != null && firstSegment.getSizeVolatile() > METADATA_LENGTH) {
      return position(firstSegment.getSegmentId(), METADATA_LENGTH);
    } else {
      return -1;
    }
  }

  @Override
  public void flush() throws Exception {
    ensureOpenedStorage();

    if (dirtySegmentId >= 0) {
      for (int id = dirtySegmentId; id <= currentSegment.getSegmentId(); id++) {
        final FsLogSegment segment = logSegments.getSegment(id);
        if (segment != null) {
          segment.flush();
        } else {
          LOG.warn("Ignoring segment {} on flush as it does not exist", id);
        }
      }

      dirtySegmentId = -1;
    }
  }

  private void onSegmentFilled() throws IOException {
    final FsLogSegment filledSegment = currentSegment;

    final int nextSegmentId = 1 + filledSegment.getSegmentId();
    final String nextSegmentName = config.fileName(nextSegmentId);
    final FsLogSegment newSegment = new FsLogSegment(nextSegmentName);

    newSegment.allocate(nextSegmentId, config.getSegmentSize());
    logSegments.addSegment(newSegment);
    currentSegment = newSegment;
    // Do this last so readers do not attempt to advance to next segment yet
    // before it is visible
    filledSegment.setFilled();
  }

  private void initLogSegments(final File logDir) throws IOException {
    final int initialSegmentId;
    final List<FsLogSegment> readableLogSegments = new ArrayList<>();

    final List<File> logFiles =
        Arrays.asList(logDir.listFiles(config::matchesFragmentFileNamePattern));

    logFiles.forEach(
        (file) -> {
          final FsLogSegment segment = new FsLogSegment(file.getAbsolutePath());
          if (segment.openSegment(false)) {
            readableLogSegments.add(segment);
          } else {
            throw new RuntimeException("Cannot init log segment " + file);
          }
        });

    // sort segments by id
    readableLogSegments.sort(Comparator.comparingInt(FsLogSegment::getSegmentId));

    // set all segments but the last one filled
    for (int i = 0; i < readableLogSegments.size() - 1; i++) {
      final FsLogSegment segment = readableLogSegments.get(i);
      segment.setFilled();
    }

    final int existingSegments = readableLogSegments.size();

    if (existingSegments > 0) {
      currentSegment = readableLogSegments.get(existingSegments - 1);
      initialSegmentId = readableLogSegments.get(0).getSegmentId();
    } else {
      initialSegmentId = config.getInitialSegmentId();
      final String initialSegmentName = config.fileName(initialSegmentId);
      final int segmentSize = config.getSegmentSize();

      final FsLogSegment initialSegment = new FsLogSegment(initialSegmentName);
      initialSegment.allocate(initialSegmentId, segmentSize);

      currentSegment = initialSegment;
      readableLogSegments.add(initialSegment);
    }

    final FsLogSegment[] segmentsArray =
        readableLogSegments.toArray(new FsLogSegment[readableLogSegments.size()]);

    final FsLogSegments logSegments = new FsLogSegments();
    logSegments.init(initialSegmentId, segmentsArray);

    this.logSegments = logSegments;
  }

  private void checkConsistency() {
    try {
      if (!currentSegment.isConsistent()) {
        // try to auto-repair segment
        currentSegment.truncateUncommittedData();
      }

      if (!currentSegment.isConsistent()) {
        throw new RuntimeException("Inconsistent log segment: " + currentSegment.getFileName());
      }
    } catch (final IOException e) {
      throw new RuntimeException("Fail to check consistency", e);
    }
  }

  private void markSegmentAsDirty(final FsLogSegment segment) {
    if (dirtySegmentId < 0) {
      dirtySegmentId = segment.getSegmentId();
    }
  }

  public FsLogStorageConfiguration getConfig() {
    return config;
  }

  private void ensureOpenedStorage() {
    if (state == STATE_CREATED) {
      throw new IllegalStateException("log storage is not open");
    }
    if (state == STATE_CLOSED) {
      throw new IllegalStateException("log storage is already closed");
    }
  }

  private void ensureNotOpenedStorage() {
    if (state == STATE_OPENED) {
      throw new IllegalStateException("log storage is already opened");
    }
  }
}
