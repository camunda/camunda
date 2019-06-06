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

import static io.zeebe.logstreams.impl.log.fs.FsLogSegmentDescriptor.METADATA_LENGTH;
import static io.zeebe.logstreams.impl.log.fs.FsLogSegmentDescriptor.SEGMENT_CAPACITY_OFFSET;
import static io.zeebe.logstreams.impl.log.fs.FsLogSegmentDescriptor.SEGMENT_ID_OFFSET;
import static io.zeebe.logstreams.impl.log.fs.FsLogSegmentDescriptor.SEGMENT_SIZE_OFFSET;

import io.zeebe.logstreams.impl.Loggers;
import io.zeebe.util.FileUtil;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import org.agrona.IoUtil;
import org.agrona.LangUtil;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;

public class FsLogSegment {
  public static final Logger LOG = Loggers.LOGSTREAMS_LOGGER;

  public static final short INVALID_ADDR = -1;

  public static final short NO_DATA = -2;

  public static final short END_OF_SEGMENT = -3;

  public static final short INSUFFICIENT_CAPACITY = -4;

  private static final short STATE_ACTIVE = 1;

  private static final short STATE_FILLED = 2;

  protected volatile short state;

  private final String fileName;

  private FileChannel fileChannel;

  private UnsafeBuffer metadataSection;

  private MappedByteBuffer mappedBuffer;

  private final Rater rater =
      new Rater(
          1024 * 1024 * 4,
          () -> {
            try {
              this.flush();
            } catch (IOException e) {
              throw new RuntimeException(e);
            }
          });

  public FsLogSegment(String fileName) {
    this.fileName = fileName;
  }

  public boolean openSegment(boolean create) {
    fileChannel = FileUtil.openChannel(fileName, create);

    if (fileChannel != null) {
      try {
        mappedBuffer = fileChannel.map(MapMode.READ_WRITE, 0, METADATA_LENGTH);
        metadataSection = new UnsafeBuffer(mappedBuffer, 0, METADATA_LENGTH);
      } catch (IOException e) {
        fileChannel = null;
        metadataSection = null;
        LangUtil.rethrowUnchecked(e);
      }
    }

    return fileChannel != null;
  }

  public void closeSegment() {
    if (fileChannel.isOpen()) {
      try {
        this.metadataSection = null;
        IoUtil.unmap(mappedBuffer);
        fileChannel.close();
      } catch (IOException e) {
        LOG.error("Failed to close segment", e);
      }
    }
  }

  public void delete() {
    final File file = new File(fileName);
    FileUtil.deleteFile(file);
  }

  public String getFileName() {
    return fileName;
  }

  public int getSegmentId() {
    return metadataSection.getInt(SEGMENT_ID_OFFSET);
  }

  private void setSegmentId(int segmentId) {
    metadataSection.putInt(SEGMENT_ID_OFFSET, segmentId);
  }

  public int getSize() {
    return metadataSection.getInt(SEGMENT_SIZE_OFFSET);
  }

  int getSizeVolatile() {
    return metadataSection.getIntVolatile(SEGMENT_SIZE_OFFSET);
  }

  private void setSizeOrdered(int tail) {
    metadataSection.putIntOrdered(SEGMENT_SIZE_OFFSET, tail);
  }

  private void setSizeVolatile(int tail) {
    metadataSection.putIntVolatile(SEGMENT_SIZE_OFFSET, tail);
  }

  public int getCapacity() {
    return metadataSection.getInt(SEGMENT_CAPACITY_OFFSET);
  }

  protected void setCapacity(int capacity) {
    metadataSection.putInt(SEGMENT_CAPACITY_OFFSET, capacity);
  }

  public boolean isFilled() {
    return state == STATE_FILLED;
  }

  public boolean isActive() {
    return state == STATE_ACTIVE;
  }

  public boolean allocate(int segmentId, int segmentSize) {
    boolean allocated = false;

    try {
      final File file = new File(fileName);
      final long availableSpace = FileUtil.getAvailableSpace(file.getParentFile());

      if (availableSpace > segmentSize) {
        openSegment(true);

        setSegmentId(segmentId);
        setCapacity(segmentSize);
        setSizeVolatile(METADATA_LENGTH);

        allocated = true;
      }
    } catch (Exception e) {
      LOG.error("Failed to allocate", e);
    }

    return allocated;
  }

  /**
   * @param block
   * @return the offset at which the block was appended
   */
  public int append(final ByteBuffer block) {
    final int blockLength = block.remaining();
    final int currentSize = getSize();
    final int remainingCapacity = getCapacity() - currentSize;

    if (remainingCapacity < blockLength) {
      return INSUFFICIENT_CAPACITY;
    }

    int newSize = currentSize;

    while (newSize - currentSize < blockLength) {
      try {
        final int writtenBytes = fileChannel.write(block, newSize);
        newSize += writtenBytes;
      } catch (Exception e) {
        LOG.error("Failed to write", e);
        return -1;
      }
    }

    setSizeOrdered(newSize);
    rater.mark(blockLength);

    return currentSize;
  }

  public void flush() throws IOException {
    if (fileChannel.isOpen()) {
      fileChannel.force(false);
    }
  }

  /**
   * Reads a sequence of bytes into the provided read buffer. Returns the result of the read
   * operation which is either
   *
   * <ul>
   *   <li>Number of bytes read in case of a successful read operation
   *   <li>{@link #NO_DATA} in case no data is available
   *   <li>{@link #END_OF_SEGMENT} in case the end of the segment is reached
   * </ul>
   *
   * @param readBuffer the buffer to read data into
   * @param fileOffset the offset in the file to read from
   * @return operation result
   */
  public int readBytes(ByteBuffer readBuffer, int fileOffset) {
    final int limit = getSizeVolatile();
    final int bufferOffset = readBuffer.position();
    final int bufferRemaining = readBuffer.remaining();

    int opResult = INVALID_ADDR;

    if (fileOffset >= METADATA_LENGTH && fileOffset <= limit) {
      final int available = limit - fileOffset;
      final int bytesToRead = Math.min(bufferRemaining, available);

      if (bytesToRead > 0) {
        readBuffer.limit(bufferOffset + bytesToRead);

        try {
          opResult = fileChannel.read(readBuffer, fileOffset);
        } catch (IOException e) {
          throw new RuntimeException(
              "Failed to read from file " + fileName + " at offset: " + fileOffset, e);
        }

      } else if (available == 0) {
        opResult = isFilled() ? END_OF_SEGMENT : NO_DATA;
      } else if (bufferRemaining == 0) {
        opResult = INSUFFICIENT_CAPACITY;
      }
    }

    return opResult;
  }

  public void setFilled() {
    // invoked by appender when segment is filled
    state = STATE_FILLED;
  }

  public boolean isConsistent() throws IOException {
    final long currentFileSize = fileChannel.size();
    final int committedSize = getSize();

    return currentFileSize == committedSize;
  }

  public void truncateUncommittedData() throws IOException {
    final int committedSize = getSize();

    closeSegment();

    try (FileChannel fileChannel = FileUtil.openChannel(fileName, false)) {
      fileChannel.truncate(committedSize);
    }

    openSegment(false);
  }
}
