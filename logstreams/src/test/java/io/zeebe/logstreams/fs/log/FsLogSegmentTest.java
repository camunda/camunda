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
package io.zeebe.logstreams.fs.log;

import static io.zeebe.util.StringUtil.getBytes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import io.zeebe.logstreams.impl.log.fs.FsLogSegment;
import io.zeebe.logstreams.impl.log.fs.FsLogSegmentDescriptor;
import io.zeebe.util.FileUtil;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Random;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

public class FsLogSegmentTest {
  private static final byte[] MSG = getBytes("test");

  private static final int CAPACITY = 1024 * 16;

  @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

  @Rule public ExpectedException thrown = ExpectedException.none();

  private String logPath;
  private String logFileName;

  private FsLogSegment fsLogSegment;

  @Before
  public void init() {
    logPath = tempFolder.getRoot().getAbsolutePath();

    logFileName = new File(logPath, "test-log-segment.data").getAbsolutePath();

    fsLogSegment = new FsLogSegment(logFileName);
  }

  @Test
  public void shouldCreateNewSegment() {
    final boolean isOpen = fsLogSegment.openSegment(true);

    assertThat(isOpen).isTrue();
  }

  @Test
  public void shouldNotCreateNewSegment() {
    final boolean isOpen = fsLogSegment.openSegment(false);

    assertThat(isOpen).isFalse();
  }

  @Test
  public void shouldGetFileName() {
    assertThat(fsLogSegment.getFileName()).isEqualTo(logFileName);
  }

  @Test
  public void shouldSetFilled() {
    assertThat(fsLogSegment.isFilled()).isFalse();

    fsLogSegment.setFilled();

    assertThat(fsLogSegment.isFilled()).isTrue();
  }

  @Test
  public void shouldAllocateSegment() {
    final boolean isAllocated = fsLogSegment.allocate(1, CAPACITY);

    assertThat(isAllocated).isTrue();

    assertThat(fsLogSegment.getSegmentId()).isEqualTo(1);
    assertThat(fsLogSegment.getCapacity()).isEqualTo(CAPACITY);
    assertThat(fsLogSegment.getSize()).isEqualTo(FsLogSegmentDescriptor.METADATA_LENGTH);
  }

  @Test
  public void shouldAppendBlock() {
    fsLogSegment.allocate(1, CAPACITY);
    final int previousSize = fsLogSegment.getSize();

    final int offset = fsLogSegment.append(ByteBuffer.wrap(MSG));

    assertThat(offset).isEqualTo(previousSize);

    final byte[] writtenBytes = readLogFile(logFileName, offset, MSG.length);

    assertThat(writtenBytes).isEqualTo(MSG);

    assertThat(fsLogSegment.getSize()).isEqualTo(previousSize + MSG.length);
  }

  @Test
  public void shouldNotAppendBlockIfSizeGreaterThanCapacity() {
    fsLogSegment.allocate(1, CAPACITY);

    final byte[] largeBlock = new byte[CAPACITY + 1];
    new Random().nextBytes(largeBlock);

    final int result = fsLogSegment.append(ByteBuffer.wrap(largeBlock));

    assertThat(result).isEqualTo(FsLogSegment.INSUFFICIENT_CAPACITY);
  }

  @Test
  public void shouldNotAppendBlockIfSizeGreaterThanRemainingCapacity() {
    fsLogSegment.allocate(1, CAPACITY);

    fsLogSegment.append(ByteBuffer.wrap(MSG));

    final byte[] largeBlock = new byte[CAPACITY];
    new Random().nextBytes(largeBlock);

    final int result = fsLogSegment.append(ByteBuffer.wrap(largeBlock));

    assertThat(result).isEqualTo(FsLogSegment.INSUFFICIENT_CAPACITY);
  }

  @Test
  public void shouldReadAppendedBlock() {
    fsLogSegment.allocate(1, CAPACITY);

    final int offset = fsLogSegment.append(ByteBuffer.wrap(MSG));

    final ByteBuffer readBuffer = ByteBuffer.allocate(MSG.length);

    final int result = fsLogSegment.readBytes(readBuffer, offset);

    assertThat(result).isEqualTo(MSG.length);
    assertThat(readBuffer.array()).isEqualTo(MSG);
  }

  @Test
  public void shouldReadPartOfAppendedBlock() {
    fsLogSegment.allocate(1, CAPACITY);

    final int offset = fsLogSegment.append(ByteBuffer.wrap(MSG));

    final ByteBuffer readBuffer = ByteBuffer.allocate(2);

    final int result = fsLogSegment.readBytes(readBuffer, offset);

    assertThat(result).isEqualTo(2);
    assertThat(readBuffer.array()).isEqualTo(new byte[] {MSG[0], MSG[1]});
  }

  @Test
  public void shouldNotReadBlockIfOfferLessThanMetaDataLength() {
    fsLogSegment.allocate(1, CAPACITY);

    final ByteBuffer readBuffer = ByteBuffer.allocate(MSG.length);

    final int result = fsLogSegment.readBytes(readBuffer, 1);

    assertThat(result).isEqualTo(FsLogSegment.INVALID_ADDR);
  }

  @Test
  public void shouldNotReadBlockIfOfferGreaterThanSize() {
    fsLogSegment.allocate(1, CAPACITY);

    final int currentSize = fsLogSegment.getSize();

    final ByteBuffer readBuffer = ByteBuffer.allocate(MSG.length);

    final int result = fsLogSegment.readBytes(readBuffer, currentSize + 1);

    assertThat(result).isEqualTo(FsLogSegment.INVALID_ADDR);
  }

  @Test
  public void shouldNotReadBlockIfNoData() {
    fsLogSegment.allocate(1, CAPACITY);

    final int currentSize = fsLogSegment.getSize();

    final ByteBuffer readBuffer = ByteBuffer.allocate(MSG.length);

    final int result = fsLogSegment.readBytes(readBuffer, currentSize);

    assertThat(result).isEqualTo(FsLogSegment.NO_DATA);
  }

  @Test
  public void shouldNotReadBlockIfFilled() {
    fsLogSegment.allocate(1, CAPACITY);
    fsLogSegment.setFilled();

    final int currentSize = fsLogSegment.getSize();

    final ByteBuffer readBuffer = ByteBuffer.allocate(MSG.length);

    final int result = fsLogSegment.readBytes(readBuffer, currentSize);

    assertThat(result).isEqualTo(FsLogSegment.END_OF_SEGMENT);
  }

  @Test
  public void shouldNotReadBlockIfBufferHasNoRemainingCapacity() {
    fsLogSegment.allocate(1, CAPACITY);

    final int offset = fsLogSegment.append(ByteBuffer.wrap(MSG));

    final ByteBuffer readBuffer = ByteBuffer.allocate(MSG.length);
    readBuffer.position(readBuffer.capacity());

    final int result = fsLogSegment.readBytes(readBuffer, offset);

    assertThat(result).isEqualTo(FsLogSegment.INSUFFICIENT_CAPACITY);
  }

  @Test
  public void shouldRestoreExistingSegment() throws IOException {
    fsLogSegment.allocate(1, CAPACITY);
    fsLogSegment.append(ByteBuffer.wrap(MSG));

    final int currentSize = fsLogSegment.getSize();

    fsLogSegment.closeSegment();

    final boolean isOpen = fsLogSegment.openSegment(false);

    assertThat(isOpen).isTrue();
    assertThat(fsLogSegment.getSize()).isEqualTo(currentSize);
  }

  @Test
  public void shouldCheckConsistency() throws IOException {
    fsLogSegment.allocate(1, CAPACITY);
    fsLogSegment.append(ByteBuffer.wrap(MSG));

    assertThat(fsLogSegment.isConsistent()).isTrue();

    // modify the underlying file
    try (FileChannel fileChannel = FileUtil.openChannel(logFileName, false)) {
      final long fileSize = fileChannel.size();

      fileChannel.position(fileSize);
      fileChannel.write(ByteBuffer.wrap(getBytes("foo")));

      assertThat(fsLogSegment.isConsistent()).isFalse();
    }
  }

  @Test
  public void shouldTruncateDataIfOverlapSize() throws IOException {
    fsLogSegment.allocate(1, CAPACITY);
    fsLogSegment.append(ByteBuffer.wrap(MSG));

    // append the underlying file
    try (FileChannel fileChannel = FileUtil.openChannel(logFileName, false)) {
      final long originalFileSize = fileChannel.size();

      fileChannel.position(originalFileSize);
      fileChannel.write(ByteBuffer.wrap(getBytes("foo")));

      assertThat(fileChannel.size()).isGreaterThan(originalFileSize);

      fsLogSegment.truncateUncommittedData();

      assertThat(fileChannel.size()).isEqualTo(originalFileSize);
    }
  }

  protected byte[] readLogFile(final String logFilePath, final long address, final int capacity) {
    final ByteBuffer buffer = ByteBuffer.allocate(capacity);

    final FileChannel fileChannel = FileUtil.openChannel(logFilePath, false);

    try {
      fileChannel.read(buffer, address);
    } catch (IOException e) {
      fail("fail to read from log file: " + logFilePath, e);
    }

    return buffer.array();
  }
}
