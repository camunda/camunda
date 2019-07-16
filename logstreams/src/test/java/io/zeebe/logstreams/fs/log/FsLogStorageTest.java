/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
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
import io.zeebe.util.collection.Tuple;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
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

    fsLogStorage = new FsLogStorage(fsStorageConfig);
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
  public void shouldGetFirstBlockAddressIfEmpty() throws IOException {
    fsLogStorage.open();

    assertThat(fsLogStorage.getFirstBlockAddress()).isEqualTo(-1);
  }

  @Test
  public void shouldGetFirstBlockAddressIfExists() throws IOException {
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
  public void shouldNotGetFirstBlockAddressIfClosed() throws IOException {
    fsLogStorage.open();
    fsLogStorage.close();

    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("log storage is already closed");

    fsLogStorage.getFirstBlockAddress();
  }

  @Test
  public void shouldCreateLogOnOpenStorage() throws IOException {
    final String initialSegmentFilePath =
        fsStorageConfig.fileName(fsStorageConfig.getInitialSegmentId());

    fsLogStorage.open();

    final File[] files = logDirectory.listFiles();

    assertThat(files).hasSize(1);
    assertThat(files[0].getAbsolutePath()).isEqualTo(initialSegmentFilePath);
  }

  @Test
  public void shouldNotDeleteLogOnCloseStorage() throws IOException {
    fsLogStorage.open();

    fsLogStorage.close();

    assertThat(logDirectory).exists();
  }

  @Test
  public void shouldDeleteLogOnCloseStorage() throws IOException {
    fsStorageConfig = new FsLogStorageConfiguration(SEGMENT_SIZE, logPath, 0, true);
    fsLogStorage = new FsLogStorage(fsStorageConfig);

    fsLogStorage.open();

    fsLogStorage.close();

    assertThat(logDirectory).doesNotExist();
  }

  @Test
  public void shouldAppendBlock() throws IOException {
    fsLogStorage.open();

    final long address = fsLogStorage.append(ByteBuffer.wrap(MSG));

    assertThat(address).isGreaterThan(0);

    final byte[] writtenBytes = readLogFile(fsStorageConfig.fileName(0), address, MSG.length);
    assertThat(writtenBytes).isEqualTo(MSG);
  }

  @Test
  public void shouldAppendBlockOnNextSegment() throws IOException {
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
  public void shouldThrowExceptionWhenBlockSizeIsGreaterThanSegment() throws IOException {
    // given
    final byte[] largeBlock = new byte[SEGMENT_SIZE + 1];
    new Random().nextBytes(largeBlock);
    fsLogStorage.open();

    // expect
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage(
        "Expected to append block with smaller block size then 16384, but actual block size was 16385.");

    // when
    fsLogStorage.append(ByteBuffer.wrap(largeBlock));
  }

  @Test
  public void shouldBeAbleToCreateMoreThan100Segments() throws IOException {

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
  public void shouldNotAppendBlockIfNotOpen() throws IOException {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("log storage is not open");

    fsLogStorage.append(ByteBuffer.wrap(MSG));
  }

  @Test
  public void shouldNotAppendBlockIfClosed() throws IOException {
    fsLogStorage.open();
    fsLogStorage.close();

    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("log storage is already closed");

    fsLogStorage.append(ByteBuffer.wrap(MSG));
  }

  @Test
  public void shouldReadWithProcessor() throws IOException {
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
  public void shouldReadWithProcessorAndReturnDifferentAddress() throws IOException {
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
  public void shouldReadWithProcessorAndReturnErrorCode() throws IOException {
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
  public void shouldReadAppendedBlock() throws IOException {
    final ByteBuffer readBuffer = ByteBuffer.allocate(MSG.length);

    fsLogStorage.open();

    final long address = fsLogStorage.append(ByteBuffer.wrap(MSG));

    final long result = fsLogStorage.read(readBuffer, address);

    assertThat(result).isEqualTo(address + MSG.length);
    assertThat(readBuffer.array()).isEqualTo(MSG);
  }

  @Test
  public void shouldNotReadBlockIfAddressIsInvalid() throws IOException {
    final ByteBuffer readBuffer = ByteBuffer.allocate(MSG.length);

    fsLogStorage.open();

    final long result = fsLogStorage.read(readBuffer, -1);

    assertThat(result).isEqualTo(LogStorage.OP_RESULT_INVALID_ADDR);
  }

  @Test
  public void shouldNotReadBlockIfNotAvailable() throws IOException {
    final ByteBuffer readBuffer = ByteBuffer.allocate(MSG.length);

    fsLogStorage.open();

    final long address = fsLogStorage.append(ByteBuffer.wrap(MSG));
    final long nextAddress = fsLogStorage.read(readBuffer, address);

    final long result = fsLogStorage.read(readBuffer, nextAddress);

    assertThat(result).isEqualTo(LogStorage.OP_RESULT_NO_DATA);
  }

  @Test
  public void shouldNotReadBlockIfBufferHasNoRemainingCapacity() throws IOException {
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
  public void shouldNotReadBlockIfClosed() throws IOException {
    final ByteBuffer readBuffer = ByteBuffer.allocate(MSG.length);

    fsLogStorage.open();

    final long address = fsLogStorage.append(ByteBuffer.wrap(MSG));

    fsLogStorage.close();

    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("log storage is already closed");

    fsLogStorage.read(readBuffer, address);
  }

  @Test
  public void shouldRestoreLogOnReOpenedStorage() throws IOException {
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

    try (FileChannel fileChannel = FileUtil.openChannel(fsStorageConfig.fileName(0), false)) {
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

    try (FileChannel fileChannel = FileUtil.openChannel(fsStorageConfig.fileName(0), false)) {
      final long fileSize = fileChannel.size();

      // remove bytes of the underlying file
      fileChannel.truncate(fileSize - 1);

      thrown.expect(RuntimeException.class);
      thrown.expectMessage("Inconsistent log segment");

      fsLogStorage.open();
    }
  }

  @Test
  public void shouldOpenStorageAfterLogSegmentsAreDeleted() throws IOException {
    // given
    final int segments = 5;
    final int deletedSegments = 3;
    final int maxCapacity = SEGMENT_SIZE - FsLogSegmentDescriptor.METADATA_LENGTH;

    fsLogStorage.open();

    final List<Tuple<Long, byte[]>> messages =
        IntStream.rangeClosed(1, segments)
            .mapToObj(
                i -> {
                  final byte[] message = new byte[maxCapacity];
                  Arrays.fill(message, (byte) i);
                  try {
                    return new Tuple<>(fsLogStorage.append(ByteBuffer.wrap(message)), message);
                  } catch (IOException e) {
                    throw new RuntimeException(e);
                  }
                })
            .collect(Collectors.toList());

    // when
    fsLogStorage.delete(messages.get(deletedSegments).getLeft());
    fsLogStorage.close();
    fsLogStorage.open();

    // when
    assertThat(fsLogStorage.getFirstBlockAddress())
        .isEqualTo(messages.get(deletedSegments).getLeft());
    messages.stream()
        .skip(deletedSegments)
        .forEach(message -> assertMessage(message.getLeft(), message.getRight()));
  }

  @Test
  public void shouldNotDeleteIfNotOpen() {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("log storage is not open");

    fsLogStorage.delete(0);
  }

  @Test
  public void shouldNotDeleteIfClosed() throws IOException {
    fsLogStorage.open();

    fsLogStorage.append(ByteBuffer.wrap(MSG));

    fsLogStorage.close();

    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("log storage is already closed");

    fsLogStorage.delete(0);
  }

  @Test
  public void shouldDoNothingIfAddressIsNegative() throws IOException {
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
  public void shouldDeleteUpToAddress() throws IOException {
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
  public void shouldDoNothingOnDeleteSameAddress() throws IOException {
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
  public void shouldDeleteMultipleTimes() throws IOException {
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
  public void shouldAppendAfterDeleteSegments() throws IOException {
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
  public void shouldNotDeleteHigherThenExistingSegmentIds() throws IOException {
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
  public void shouldNotDeleteInitialSegment() throws IOException {
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

  @Test
  public void shouldIgnoreDeletedSegmentsOnFlush() throws Exception {
    // given
    fsLogStorage.open();
    final int remainingCapacity = SEGMENT_SIZE - FsLogSegmentDescriptor.METADATA_LENGTH;
    final byte[] largeBlock = new byte[remainingCapacity];

    fsLogStorage.append(ByteBuffer.wrap(largeBlock));
    final long address = fsLogStorage.append(ByteBuffer.wrap(largeBlock));

    // when
    fsLogStorage.delete(address);

    // then
    fsLogStorage.flush();
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

  private long appendLargeBlockWithMsgAfterwards(int msgLength) throws IOException {
    final byte[] largeBlockBeforeMessage =
        new byte[SEGMENT_SIZE - FsLogSegmentDescriptor.METADATA_LENGTH - (msgLength)];
    new Random().nextBytes(largeBlockBeforeMessage);
    fsLogStorage.append(ByteBuffer.wrap(largeBlockBeforeMessage));
    return fsLogStorage.append(ByteBuffer.wrap(MSG));
  }
}
