/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.tngp.logstreams.impl.fs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.camunda.tngp.dispatcher.impl.PositionUtil.partitionOffset;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Random;

import org.camunda.tngp.logstreams.spi.LogStorage;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

public class FsLogStorageTest
{
    private static final int SEGMENT_SIZE = 1024 * 16;

    private static final byte[] MSG = "test".getBytes();

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private String logPath;
    private File logDirectory;

    private FsStorageConfiguration fsStorageConfig;

    private FsLogStorage fsLogStorage;

    @Before
    public void init()
    {
        logPath = tempFolder.getRoot().getAbsolutePath();
        logDirectory = new File(logPath);

        fsStorageConfig = new FsStorageConfiguration(SEGMENT_SIZE, logPath, 0, false);

        fsLogStorage = new FsLogStorage(fsStorageConfig);
    }

    @Test
    public void shouldGetConfig()
    {
        assertThat(fsLogStorage.getConfig()).isEqualTo(fsStorageConfig);
    }

    @Test
    public void shouldBeByteAddressable()
    {
        assertThat(fsLogStorage.isByteAddressable()).isTrue();
    }

    @Test
    public void shouldGetFirstBlockAddressIfEmpty()
    {
        fsLogStorage.open();

        assertThat(fsLogStorage.getFirstBlockAddress()).isEqualTo(-1);
    }

    @Test
    public void shouldGetFirstBlockAddressIfExists()
    {
        fsLogStorage.open();

        final long address = fsLogStorage.append(ByteBuffer.wrap(MSG));

        assertThat(fsLogStorage.getFirstBlockAddress()).isEqualTo(address);
    }

    @Test
    public void shouldNotGetFirstBlockAddressIfNotOpen()
    {
        thrown.expect(IllegalStateException.class);
        thrown.expectMessage("log storage is not open");

        fsLogStorage.getFirstBlockAddress();
    }

    @Test
    public void shouldNotGetFirstBlockAddressIfClosed()
    {
        fsLogStorage.open();
        fsLogStorage.close();

        thrown.expect(IllegalStateException.class);
        thrown.expectMessage("log storage is already closed");

        fsLogStorage.getFirstBlockAddress();
    }

    @Test
    public void shouldCreateLogOnOpenStorage()
    {
        final String initialSegmentFilePath = fsStorageConfig.fileName(fsStorageConfig.getInitialSegmentId());

        fsLogStorage.open();

        final File[] files = logDirectory.listFiles();

        assertThat(files).hasSize(1);
        assertThat(files[0].getAbsolutePath()).isEqualTo(initialSegmentFilePath);
    }

    @Test
    public void shouldNotDeleteLogOnCloseStorage()
    {
        fsLogStorage.open();

        fsLogStorage.close();

        assertThat(logDirectory).exists();
    }

    @Test
    public void shouldDeleteLogOnCloseStorage()
    {
        fsStorageConfig = new FsStorageConfiguration(SEGMENT_SIZE, logPath, 0, true);
        fsLogStorage = new FsLogStorage(fsStorageConfig);

        fsLogStorage.open();

        fsLogStorage.close();

        assertThat(logDirectory).doesNotExist();
    }

    @Test
    public void shouldAppendBlock()
    {
        fsLogStorage.open();

        final long address = fsLogStorage.append(ByteBuffer.wrap(MSG));

        assertThat(address).isGreaterThan(0);

        final byte[] writtenBytes = readLogFile(fsStorageConfig.fileName(0), address, MSG.length);
        assertThat(writtenBytes).isEqualTo(MSG);
    }

    @Test
    public void shouldAppendBlockOnNextSegment()
    {
        fsLogStorage.open();
        fsLogStorage.append(ByteBuffer.wrap(MSG));

        assertThat(logDirectory.listFiles().length).isEqualTo(1);

        final int remainingCapacity = SEGMENT_SIZE - FsLogSegmentDescriptor.METADATA_LENGTH - MSG.length;
        final byte[] largeBlock = new byte[remainingCapacity + 1];
        new Random().nextBytes(largeBlock);

        final long address = fsLogStorage.append(ByteBuffer.wrap(largeBlock));

        assertThat(address).isGreaterThan(0);
        assertThat(logDirectory.listFiles().length).isEqualTo(2);

        final byte[] writtenBytes = readLogFile(fsStorageConfig.fileName(1), partitionOffset(address), largeBlock.length);
        assertThat(writtenBytes).isEqualTo(largeBlock);

        fsLogStorage.close();
    }

    @Test
    public void shouldNotAppendBlockIfSizeIsGreaterThanSegment()
    {
        final byte[] largeBlock = new byte[SEGMENT_SIZE + 1];
        new Random().nextBytes(largeBlock);

        fsLogStorage.open();

        final long result = fsLogStorage.append(ByteBuffer.wrap(largeBlock));

        assertThat(result).isEqualTo(LogStorage.OP_RESULT_BLOCK_SIZE_TOO_BIG);
    }

    @Test
    public void shouldNotAppendBlockIfNotOpen()
    {
        thrown.expect(IllegalStateException.class);
        thrown.expectMessage("log storage is not open");

        fsLogStorage.append(ByteBuffer.wrap(MSG));
    }

    @Test
    public void shouldNotAppendBlockIfClosed()
    {
        fsLogStorage.open();
        fsLogStorage.close();

        thrown.expect(IllegalStateException.class);
        thrown.expectMessage("log storage is already closed");

        fsLogStorage.append(ByteBuffer.wrap(MSG));
    }

    @Test
    public void shouldReadAppendedBlock()
    {
        final ByteBuffer readBuffer = ByteBuffer.allocate(MSG.length);

        fsLogStorage.open();

        final long address = fsLogStorage.append(ByteBuffer.wrap(MSG));

        final long result = fsLogStorage.read(readBuffer, address);

        assertThat(result).isEqualTo(address + MSG.length);
        assertThat(readBuffer.array()).isEqualTo(MSG);
    }

    @Test
    public void shouldNotReadBlockIfAddressIsInvalid()
    {
        final ByteBuffer readBuffer = ByteBuffer.allocate(MSG.length);

        fsLogStorage.open();

        final long result = fsLogStorage.read(readBuffer, -1);

        assertThat(result).isEqualTo(LogStorage.OP_RESULT_INVALID_ADDR);
    }

    @Test
    public void shouldNotReadBlockIfNotAvailable()
    {
        final ByteBuffer readBuffer = ByteBuffer.allocate(MSG.length);

        fsLogStorage.open();

        final long address = fsLogStorage.append(ByteBuffer.wrap(MSG));
        final long nextAddress = fsLogStorage.read(readBuffer, address);

        final long result = fsLogStorage.read(readBuffer, nextAddress);

        assertThat(result).isEqualTo(LogStorage.OP_RESULT_NO_DATA);
    }

    @Test
    public void shouldNotReadBlockIfNotOpen()
    {
        final ByteBuffer readBuffer = ByteBuffer.allocate(MSG.length);

        thrown.expect(IllegalStateException.class);
        thrown.expectMessage("log storage is not open");

        fsLogStorage.read(readBuffer, 0);
    }

    @Test
    public void shouldNotReadBlockIfClosed()
    {
        final ByteBuffer readBuffer = ByteBuffer.allocate(MSG.length);

        fsLogStorage.open();

        final long address = fsLogStorage.append(ByteBuffer.wrap(MSG));

        fsLogStorage.close();

        thrown.expect(IllegalStateException.class);
        thrown.expectMessage("log storage is already closed");

        fsLogStorage.read(readBuffer, address);
    }

    @Test
    public void shouldRestoreLogOnReOpenedStorage()
    {
        final ByteBuffer readBuffer = ByteBuffer.allocate(MSG.length);

        fsLogStorage.open();

        final long address = fsLogStorage.append(ByteBuffer.wrap(MSG));

        fsLogStorage.close();

        fsLogStorage.open();

        assertThat(fsLogStorage.getFirstBlockAddress()).isEqualTo(address);

        fsLogStorage.read(readBuffer, address);

        assertThat(readBuffer.array()).isEqualTo(MSG);
    }

    protected byte[] readLogFile(final String logFilePath, final long address, final int capacity)
    {
        final ByteBuffer buffer = ByteBuffer.allocate(capacity);

        final FileChannel fileChannel = FileChannelUtil.openChannel(logFilePath, false);

        try
        {
            fileChannel.read(buffer, address);
        }
        catch (IOException e)
        {
            fail("fail to read from log file: " + logFilePath, e);
        }

        return buffer.array();
    }

}
