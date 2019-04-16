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

import static io.zeebe.dispatcher.impl.PositionUtil.partitionOffset;
import static io.zeebe.util.StringUtil.getBytes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import io.zeebe.dispatcher.impl.PositionUtil;
import io.zeebe.logstreams.impl.log.fs.FsLogSegmentDescriptor;
import io.zeebe.logstreams.impl.log.fs.FsLogStorage;
import io.zeebe.logstreams.impl.log.fs.FsLogStorageConfiguration;
import io.zeebe.logstreams.spi.LogStorage;
import io.zeebe.util.FileUtil;
import io.zeebe.util.metrics.MetricsManager;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.Random;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

public class FsLogStorageTest {
  private static final int SEGMENT_SIZE = 1024 * 16;

  private static final byte[] MSG = getBytes("test");

  @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

  @Rule public ExpectedException thrown = ExpectedException.none();

  private String logPath;
  private File logDirectory;

  private FsLogStorageConfiguration fsStorageConfig;

  private FsLogStorage fsLogStorage;

  @Before
  public void init() {
    logPath = tempFolder.getRoot().getAbsolutePath();
    logDirectory = new File(logPath);

    fsStorageConfig = new FsLogStorageConfiguration(SEGMENT_SIZE, logPath, 0, false);

    fsLogStorage = new FsLogStorage(fsStorageConfig, new MetricsManager(), 0);
  }

  @Test
  public void shouldGetConfig() {
    assertThat(fsLogStorage.getConfig()).isEqualTo(fsStorageConfig);
  }

  @Test
  public void shouldBeByteAddressable() {
    assertThat(fsLogStorage.isByteAddressable()).isTrue();
  }

  @Test
  public void shouldGetFirstBlockAddressIfEmpty() {
    fsLogStorage.open();

    assertThat(fsLogStorage.getFirstBlockAddress()).isEqualTo(-1);
  }

  @Test
  public void shouldGetFirstBlockAddressIfExists() {
    fsLogStorage.open();

    final long address = fsLogStorage.append(ByteBuffer.wrap(MSG));

    assertThat(fsLogStorage.getFirstBlockAddress()).isEqualTo(address);
  }

  @Test
  public void shouldNotGetFirstBlockAddressIfNotOpen() {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("log storage is not open");

    fsLogStorage.getFirstBlockAddress();
  }

  @Test
  public void shouldNotGetFirstBlockAddressIfClosed() {
    fsLogStorage.open();
    fsLogStorage.close();

    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("log storage is already closed");

    fsLogStorage.getFirstBlockAddress();
  }

  @Test
  public void shouldCreateLogOnOpenStorage() {
    final String initialSegmentFilePath =
        fsStorageConfig.fileName(fsStorageConfig.getInitialSegmentId());

    fsLogStorage.open();

    final File[] files = logDirectory.listFiles();

    assertThat(files).hasSize(1);
    assertThat(files[0].getAbsolutePath()).isEqualTo(initialSegmentFilePath);
  }

  @Test
  public void shouldNotDeleteLogOnCloseStorage() {
    fsLogStorage.open();

    fsLogStorage.close();

    assertThat(logDirectory).exists();
  }

  @Test
  public void shouldDeleteLogOnCloseStorage() {
    fsStorageConfig = new FsLogStorageConfiguration(SEGMENT_SIZE, logPath, 0, true);
    fsLogStorage = new FsLogStorage(fsStorageConfig, new MetricsManager(), 0);

    fsLogStorage.open();

    fsLogStorage.close();

    assertThat(logDirectory).doesNotExist();
  }

  @Test
  public void shouldAppendBlock() {
    fsLogStorage.open();

    final long address = fsLogStorage.append(ByteBuffer.wrap(MSG));

    assertThat(address).isGreaterThan(0);

    final byte[] writtenBytes = readLogFile(fsStorageConfig.fileName(0), address, MSG.length);
    assertThat(writtenBytes).isEqualTo(MSG);
  }

  @Test
  public void shouldAppendBlockOnNextSegment() {
    fsLogStorage.open();
    fsLogStorage.append(ByteBuffer.wrap(MSG));

    assertThat(logDirectory.listFiles().length).isEqualTo(1);

    final int remainingCapacity =
        SEGMENT_SIZE - FsLogSegmentDescriptor.METADATA_LENGTH - MSG.length;
    final byte[] largeBlock = new byte[remainingCapacity + 1];
    new Random().nextBytes(largeBlock);

    final long address = fsLogStorage.append(ByteBuffer.wrap(largeBlock));

    assertThat(address).isGreaterThan(0);
    assertThat(logDirectory.listFiles().length).isEqualTo(2);

    final byte[] writtenBytes =
        readLogFile(fsStorageConfig.fileName(1), partitionOffset(address), largeBlock.length);
    assertThat(writtenBytes).isEqualTo(largeBlock);

    fsLogStorage.close();
  }

  @Test
  public void shouldNotAppendBlockIfSizeIsGreaterThanSegment() {
    final byte[] largeBlock = new byte[SEGMENT_SIZE + 1];
    new Random().nextBytes(largeBlock);

    fsLogStorage.open();

    final long result = fsLogStorage.append(ByteBuffer.wrap(largeBlock));

    assertThat(result).isEqualTo(LogStorage.OP_RESULT_BLOCK_SIZE_TOO_BIG);
  }

  @Test
  public void shouldBeAbleToCreateMoreThan100Segments() {

    final byte[] oneSegment = new byte[SEGMENT_SIZE - FsLogSegmentDescriptor.METADATA_LENGTH];

    new Random().nextBytes(oneSegment);

    fsLogStorage.open();

    long result = 0;

    for (int i = 0; i < 101; i++) {
      Arrays.fill(oneSegment, (byte) i);
      result = fsLogStorage.append(ByteBuffer.wrap(oneSegment));
    }

    fsLogStorage.close();

    fsLogStorage.open();

    final ByteBuffer readBuffer = ByteBuffer.allocate(SEGMENT_SIZE);

    final long ret = fsLogStorage.read(readBuffer, result);

    assertThat(ret).isNotEqualTo(LogStorage.OP_RESULT_INVALID_ADDR);

    fsLogStorage.close();
  }

  @Test
  public void shouldNotAppendBlockIfNotOpen() {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("log storage is not open");

    fsLogStorage.append(ByteBuffer.wrap(MSG));
  }

  @Test
  public void shouldNotAppendBlockIfClosed() {
    fsLogStorage.open();
    fsLogStorage.close();

    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("log storage is already closed");

    fsLogStorage.append(ByteBuffer.wrap(MSG));
  }

  @Test
  public void shouldReadWithProcessor() {
    // given
    final ByteBuffer readBuffer = ByteBuffer.allocate(MSG.length);
    fsLogStorage.open();
    final long address = fsLogStorage.append(ByteBuffer.wrap(MSG));

    // when
    final long result =
        fsLogStorage.read(
            readBuffer,
            address,
            (buffer, resultAddress) -> {
              // then
              assertThat(resultAddress).isEqualTo(MSG.length);
              assertThat(buffer.array()).isEqualTo(MSG);
              return resultAddress;
            });
    assertThat(result).isEqualTo(address + MSG.length);
  }

  @Test
  public void shouldReadWithProcessorAndReturnDifferentAddress() {
    // given
    final ByteBuffer readBuffer = ByteBuffer.allocate(MSG.length);
    fsLogStorage.open();
    final long address = fsLogStorage.append(ByteBuffer.wrap(MSG));

    // when
    final long result =
        fsLogStorage.read(readBuffer, address, (buffer, resultAddress) -> resultAddress - 1);
    assertThat(result).isEqualTo(address + MSG.length - 1);
  }

  @Test
  public void shouldReadWithProcessorAndReturnErrorCode() {
    // given
    final ByteBuffer readBuffer = ByteBuffer.allocate(MSG.length);
    fsLogStorage.open();
    final long address = fsLogStorage.append(ByteBuffer.wrap(MSG));

    // when
    final long result =
        fsLogStorage.read(
            readBuffer,
            address,
            (buffer, resultAddress) -> (int) LogStorage.OP_RESULT_INSUFFICIENT_BUFFER_CAPACITY);
    assertThat(result).isEqualTo(LogStorage.OP_RESULT_INSUFFICIENT_BUFFER_CAPACITY);
  }

  @Test
  public void shouldReadAppendedBlock() {
    final ByteBuffer readBuffer = ByteBuffer.allocate(MSG.length);

    fsLogStorage.open();

    final long address = fsLogStorage.append(ByteBuffer.wrap(MSG));

    final long result = fsLogStorage.read(readBuffer, address);

    assertThat(result).isEqualTo(address + MSG.length);
    assertThat(readBuffer.array()).isEqualTo(MSG);
  }

  @Test
  public void shouldNotReadBlockIfAddressIsInvalid() {
    final ByteBuffer readBuffer = ByteBuffer.allocate(MSG.length);

    fsLogStorage.open();

    final long result = fsLogStorage.read(readBuffer, -1);

    assertThat(result).isEqualTo(LogStorage.OP_RESULT_INVALID_ADDR);
  }

  @Test
  public void shouldNotReadBlockIfNotAvailable() {
    final ByteBuffer readBuffer = ByteBuffer.allocate(MSG.length);

    fsLogStorage.open();

    final long address = fsLogStorage.append(ByteBuffer.wrap(MSG));
    final long nextAddress = fsLogStorage.read(readBuffer, address);

    final long result = fsLogStorage.read(readBuffer, nextAddress);

    assertThat(result).isEqualTo(LogStorage.OP_RESULT_NO_DATA);
  }

  @Test
  public void shouldNotReadBlockIfBufferHasNoRemainingCapacity() {
    final ByteBuffer readBuffer = ByteBuffer.allocate(MSG.length);
    readBuffer.position(readBuffer.capacity());

    fsLogStorage.open();

    final long address = fsLogStorage.append(ByteBuffer.wrap(MSG));

    final long result = fsLogStorage.read(readBuffer, address);

    assertThat(result).isEqualTo(0);
    assertThat(readBuffer.array()).isEqualTo(new byte[MSG.length]);
  }

  @Test
  public void shouldNotReadBlockIfNotOpen() {
    final ByteBuffer readBuffer = ByteBuffer.allocate(MSG.length);

    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("log storage is not open");

    fsLogStorage.read(readBuffer, 0);
  }

  @Test
  public void shouldNotReadBlockIfClosed() {
    final ByteBuffer readBuffer = ByteBuffer.allocate(MSG.length);

    fsLogStorage.open();

    final long address = fsLogStorage.append(ByteBuffer.wrap(MSG));

    fsLogStorage.close();

    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("log storage is already closed");

    fsLogStorage.read(readBuffer, address);
  }

  @Test
  public void shouldRestoreLogOnReOpenedStorage() {
    final ByteBuffer readBuffer = ByteBuffer.allocate(MSG.length);

    fsLogStorage.open();

    final long address = fsLogStorage.append(ByteBuffer.wrap(MSG));

    fsLogStorage.close();

    fsLogStorage.open();

    assertThat(fsLogStorage.getFirstBlockAddress()).isEqualTo(address);

    fsLogStorage.read(readBuffer, address);

    assertThat(readBuffer.array()).isEqualTo(MSG);
  }

  @Test
  public void shouldRepairSegmentIfInconsistentOnOpen() throws IOException {
    // append the log storage
    fsLogStorage.open();
    fsLogStorage.append(ByteBuffer.wrap(MSG));
    fsLogStorage.close();

    try (final FileChannel fileChannel = FileUtil.openChannel(fsStorageConfig.fileName(0), false)) {
      final long originalFileSize = fileChannel.size();

      // append the underlying file
      fileChannel.position(originalFileSize);
      fileChannel.write(ByteBuffer.wrap(getBytes("foo")));

      assertThat(fileChannel.size()).isGreaterThan(originalFileSize);

      // open the log storage to trigger auto-repair
      fsLogStorage.open();

      // verify that the log storage is restored
      assertThat(fileChannel.size()).isEqualTo(originalFileSize);
    }
  }

  @Test
  public void shouldFailIfSegmentIsInconsistentOnOpen() throws IOException {
    // append the log storage
    fsLogStorage.open();
    fsLogStorage.append(ByteBuffer.wrap(MSG));
    fsLogStorage.close();

    try (final FileChannel fileChannel = FileUtil.openChannel(fsStorageConfig.fileName(0), false)) {
      final long fileSize = fileChannel.size();

      // remove bytes of the underlying file
      fileChannel.truncate(fileSize - 1);

      thrown.expect(RuntimeException.class);
      thrown.expectMessage("Inconsistent log segment");

      fsLogStorage.open();
    }
  }

  @Test
  public void shouldNotDeleteIfNotOpen() {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("log storage is not open");

    fsLogStorage.delete(0);
  }

  @Test
  public void shouldNotDeleteIfClosed() {
    fsLogStorage.open();

    fsLogStorage.append(ByteBuffer.wrap(MSG));

    fsLogStorage.close();

    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("log storage is already closed");

    fsLogStorage.delete(0);
  }

  @Test
  public void shouldDoNothingIfAddressIsNegative() {
    // given
    fsLogStorage.open();
    final int remainingCapacity = SEGMENT_SIZE - FsLogSegmentDescriptor.METADATA_LENGTH;
    final byte[] largeBlock = new byte[remainingCapacity];

    // segments 1, 2, 3 + msg
    fsLogStorage.append(ByteBuffer.wrap(largeBlock));
    fsLogStorage.append(ByteBuffer.wrap(largeBlock));

    final long firstMessageAddress = fsLogStorage.append(ByteBuffer.wrap(MSG));
    final long secondMessageAddress = appendLargeBlockWithMsgAfterwards(MSG.length * 2);

    // when
    final long address = PositionUtil.position(-1, 0);
    fsLogStorage.delete(address);

    // then
    assertThat(logDirectory.listFiles().length).isEqualTo(3);
    assertMessage(firstMessageAddress, MSG);
    assertMessage(secondMessageAddress, MSG);

    fsLogStorage.close();
  }

  @Test
  public void shouldDeleteUpToAddress() {
    // given
    fsLogStorage.open();
    final int remainingCapacity = SEGMENT_SIZE - FsLogSegmentDescriptor.METADATA_LENGTH;
    final byte[] largeBlock = new byte[remainingCapacity];

    // segments 1, 2, 3 + msg
    fsLogStorage.append(ByteBuffer.wrap(largeBlock));
    fsLogStorage.append(ByteBuffer.wrap(largeBlock));

    final long firstMessageAddress = fsLogStorage.append(ByteBuffer.wrap(MSG));
    final long secondMessageAddress = appendLargeBlockWithMsgAfterwards(MSG.length * 2);

    // when
    fsLogStorage.delete(secondMessageAddress);

    // then
    assertThat(logDirectory.listFiles().length).isEqualTo(1);
    assertMessage(firstMessageAddress, MSG);
    assertMessage(secondMessageAddress, MSG);

    fsLogStorage.close();
  }

  @Test
  public void shouldDoNothingOnDeleteSameAddress() {
    // given
    fsLogStorage.open();
    final int remainingCapacity = SEGMENT_SIZE - FsLogSegmentDescriptor.METADATA_LENGTH;
    final byte[] largeBlock = new byte[remainingCapacity];

    // segments 1, 2, 3 + msg
    fsLogStorage.append(ByteBuffer.wrap(largeBlock));
    fsLogStorage.append(ByteBuffer.wrap(largeBlock));

    final long firstMessageAddress = fsLogStorage.append(ByteBuffer.wrap(MSG));
    final long secondMessageAddress = appendLargeBlockWithMsgAfterwards(MSG.length * 2);

    // when
    fsLogStorage.delete(secondMessageAddress);
    fsLogStorage.delete(secondMessageAddress);

    // then
    assertThat(logDirectory.listFiles().length).isEqualTo(1);
    assertMessage(firstMessageAddress, MSG);
    assertMessage(secondMessageAddress, MSG);

    fsLogStorage.close();
  }

  @Test
  public void shouldDeleteMultipleTimes() {
    // given
    fsLogStorage.open();
    final int remainingCapacity = SEGMENT_SIZE - FsLogSegmentDescriptor.METADATA_LENGTH;
    final byte[] largeBlock = new byte[remainingCapacity];

    // segments 1, 2, 3 + msg
    fsLogStorage.append(ByteBuffer.wrap(largeBlock));
    final long address = fsLogStorage.append(ByteBuffer.wrap(largeBlock));

    final long firstMessageAddress = fsLogStorage.append(ByteBuffer.wrap(MSG));
    final long secondMessageAddress = appendLargeBlockWithMsgAfterwards(MSG.length * 2);

    // when
    fsLogStorage.delete(address);
    fsLogStorage.delete(secondMessageAddress);

    // then
    assertThat(logDirectory.listFiles().length).isEqualTo(1);
    assertMessage(firstMessageAddress, MSG);
    assertMessage(secondMessageAddress, MSG);

    fsLogStorage.close();
  }

  @Test
  public void shouldAppendAfterDeleteSegments() {
    // given
    fsLogStorage.open();
    final int remainingCapacity = SEGMENT_SIZE - FsLogSegmentDescriptor.METADATA_LENGTH;
    final byte[] largeBlock = new byte[remainingCapacity];

    // segments 1, 2, 3 + msg
    fsLogStorage.append(ByteBuffer.wrap(largeBlock));
    final long address = fsLogStorage.append(ByteBuffer.wrap(largeBlock));

    fsLogStorage.delete(address);
    final long firstMessageAddress = fsLogStorage.append(ByteBuffer.wrap(MSG));
    final long secondMessageAddress = appendLargeBlockWithMsgAfterwards(MSG.length * 2);

    // when
    fsLogStorage.delete(secondMessageAddress);

    // then
    assertThat(logDirectory.listFiles().length).isEqualTo(1);
    assertMessage(firstMessageAddress, MSG);
    assertMessage(secondMessageAddress, MSG);

    fsLogStorage.close();
  }

  @Test
  public void shouldNotDeleteHigherThenExistingSegmentIds() {
    // given
    fsLogStorage.open();

    // segments 1, 2, 3 + msg
    final int remainingCapacity = SEGMENT_SIZE - FsLogSegmentDescriptor.METADATA_LENGTH;
    final byte[] largeBlock = new byte[remainingCapacity];
    fsLogStorage.append(ByteBuffer.wrap(largeBlock));
    fsLogStorage.append(ByteBuffer.wrap(largeBlock));

    final long firstMessageAddress = fsLogStorage.append(ByteBuffer.wrap(MSG));
    final long secondMessageAddress = appendLargeBlockWithMsgAfterwards(MSG.length * 2);

    // when
    final long address = PositionUtil.position(Integer.MAX_VALUE, 0);
    fsLogStorage.delete(address);

    // then
    assertThat(logDirectory.listFiles().length).isEqualTo(3);
    assertMessage(firstMessageAddress, MSG);
    assertMessage(secondMessageAddress, MSG);

    fsLogStorage.close();
  }

  @Test
  public void shouldNotDeleteInitialSegment() {
    // given
    fsLogStorage.open();

    // segments 1 + msg
    final long addressMessage = appendLargeBlockWithMsgAfterwards(MSG.length);

    // when
    fsLogStorage.delete(addressMessage);

    // then
    assertThat(logDirectory.listFiles().length).isEqualTo(1);
    assertMessage(addressMessage, MSG);

    fsLogStorage.close();
  }

  private byte[] readLogFile(final String logFilePath, final long address, final int capacity) {
    final ByteBuffer buffer = ByteBuffer.allocate(capacity);

    final FileChannel fileChannel = FileUtil.openChannel(logFilePath, false);

    try {
      fileChannel.read(buffer, address);
    } catch (final IOException e) {
      fail("fail to read from log file: " + logFilePath, e);
    }

    return buffer.array();
  }

  private void assertMessage(final long address, final byte[] message) {
    final int length = message.length;
    final ByteBuffer readBuffer = ByteBuffer.allocate(length);
    final long result = fsLogStorage.read(readBuffer, address);
    assertThat(result).isEqualTo(address + length);
    assertThat(readBuffer.array()).isEqualTo(message);
  }

  private long appendLargeBlockWithMsgAfterwards(int msgLength) {
    final byte[] largeBlockBeforeMessage =
        new byte[SEGMENT_SIZE - FsLogSegmentDescriptor.METADATA_LENGTH - (msgLength)];
    new Random().nextBytes(largeBlockBeforeMessage);
    fsLogStorage.append(ByteBuffer.wrap(largeBlockBeforeMessage));
    return fsLogStorage.append(ByteBuffer.wrap(MSG));
  }
}
