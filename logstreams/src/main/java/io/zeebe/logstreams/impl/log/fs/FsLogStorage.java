/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
import io.zeebe.util.metrics.Metric;
import io.zeebe.util.metrics.MetricsManager;
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

  protected final FsLogStorageConfiguration config;
  private final MetricsManager metricsManager;
  private final ReadResultProcessor defaultReadResultProcessor = (buffer, readResult) -> readResult;

  /** Readable log segments */
  private FsLogSegments logSegments;

  private FsLogSegment currentSegment;

  private int dirtySegmentId = -1;

  protected volatile int state = STATE_CREATED;

  private Metric totalBytesMetric;
  private Metric segmentCountMetric;

  private final int partitionId;

  public FsLogStorage(
      final FsLogStorageConfiguration cfg,
      final MetricsManager metricsManager,
      final int partitionId) {
    this.config = cfg;
    this.metricsManager = metricsManager;
    this.partitionId = partitionId;
  }

  @Override
  public boolean isByteAddressable() {
    return true;
  }

  @Override
  public long append(final ByteBuffer buffer) {
    ensureOpenedStorage();

    final int size = currentSegment.getSize();
    final int capacity = currentSegment.getCapacity();
    final int remainingCapacity = capacity - size;
    final int requiredCapacity = buffer.remaining();

    if (requiredCapacity > config.getSegmentSize()) {
      return OP_RESULT_BLOCK_SIZE_TOO_BIG;
    }

    if (remainingCapacity < requiredCapacity) {
      onSegmentFilled();
    }

    long opresult = -1;

    if (currentSegment != null) {
      final int appendResult = currentSegment.append(buffer);

      if (appendResult >= 0) {
        opresult = position(currentSegment.getSegmentId(), appendResult);
        markSegmentAsDirty(currentSegment);
        totalBytesMetric.getAndAddOrdered(requiredCapacity);
      } else {
        opresult = appendResult;
      }
    }

    return opresult;
  }

  private void onSegmentFilled() {
    final FsLogSegment filledSegment = currentSegment;

    final int nextSegmentId = 1 + filledSegment.getSegmentId();
    final String nextSegmentName = config.fileName(nextSegmentId);
    final FsLogSegment newSegment = new FsLogSegment(nextSegmentName);

    if (newSegment.allocate(nextSegmentId, config.getSegmentSize())) {
      logSegments.addSegment(newSegment);
      currentSegment = newSegment;
      // Do this last so readers do not attempt to advance to next segment yet
      // before it is visible
      filledSegment.setFilled();
      segmentCountMetric.setOrdered(logSegments.getSegmentCount());
    }
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
  public void open() {
    ensureNotOpenedStorage();

    totalBytesMetric =
        metricsManager
            .newMetric("storage_fs_total_bytes")
            .label("partition", String.valueOf(partitionId))
            .create();
    segmentCountMetric =
        metricsManager
            .newMetric("storage_fs_segment_count")
            .label("partition", String.valueOf(partitionId))
            .create();

    final String path = config.getPath();
    final File logDir = new File(path);
    logDir.mkdirs();

    initLogSegments(logDir);

    checkConsistency();

    state = STATE_OPENED;
  }

  private void initLogSegments(final File logDir) {
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

      totalBytesMetric.getAndAddOrdered(segment.getSize());
    }

    final int existingSegments = readableLogSegments.size();

    if (existingSegments > 0) {
      currentSegment = readableLogSegments.get(existingSegments - 1);
    } else {
      final int initialSegmentId = config.getInitialSegmentId();
      final String initialSegmentName = config.fileName(initialSegmentId);
      final int segmentSize = config.getSegmentSize();

      final FsLogSegment initialSegment = new FsLogSegment(initialSegmentName);

      if (!initialSegment.allocate(initialSegmentId, segmentSize)) {
        throw new RuntimeException("Cannot allocate initial segment");
      }

      currentSegment = initialSegment;
      readableLogSegments.add(initialSegment);
    }

    totalBytesMetric.getAndAddOrdered(currentSegment.getSize());

    final FsLogSegment[] segmentsArray =
        readableLogSegments.toArray(new FsLogSegment[readableLogSegments.size()]);

    final FsLogSegments logSegments = new FsLogSegments();
    logSegments.init(config.getInitialSegmentId(), segmentsArray);
    segmentCountMetric.setOrdered(logSegments.getSegmentCount());

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

  @Override
  public void close() {
    segmentCountMetric.close();
    totalBytesMetric.close();

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
  public void flush() throws Exception {
    ensureOpenedStorage();

    if (dirtySegmentId >= 0) {
      for (int id = dirtySegmentId; id <= currentSegment.getSegmentId(); id++) {
        logSegments.getSegment(id).flush();
      }

      dirtySegmentId = -1;
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

  @Override
  public boolean isOpen() {
    return state == STATE_OPENED;
  }

  @Override
  public boolean isClosed() {
    return state == STATE_CLOSED;
  }
}
