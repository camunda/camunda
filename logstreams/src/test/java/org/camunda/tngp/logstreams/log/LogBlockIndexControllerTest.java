package org.camunda.tngp.logstreams.log;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.alignedLength;
import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.lengthOffset;
import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.messageOffset;
import static org.camunda.tngp.logstreams.impl.LogEntryDescriptor.positionOffset;
import static org.camunda.tngp.util.buffer.BufferUtil.wrapString;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.logstreams.fs.FsLogStreamBuilder;
import org.camunda.tngp.logstreams.impl.LogBlockIndexController;
import org.camunda.tngp.logstreams.impl.log.index.LogBlockIndex;
import org.camunda.tngp.logstreams.spi.LogStorage;
import org.camunda.tngp.logstreams.spi.ReadResultProcessor;
import org.camunda.tngp.logstreams.spi.ReadableSnapshot;
import org.camunda.tngp.logstreams.spi.SnapshotPolicy;
import org.camunda.tngp.logstreams.spi.SnapshotStorage;
import org.camunda.tngp.logstreams.spi.SnapshotSupport;
import org.camunda.tngp.logstreams.spi.SnapshotWriter;
import org.camunda.tngp.util.agent.AgentRunnerService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

/**
 * @author Christopher Zell <christopher.zell@camunda.com>
 */
public class LogBlockIndexControllerTest
{
    public static final String TOPIC_NAME = "test-topic";
    private static final DirectBuffer TOPIC_NAME_BUFFER = wrapString(TOPIC_NAME);
    private static final int PARTITION_ID = 0;
    private static final String LOG_NAME = String.format("%s.%d", TOPIC_NAME, PARTITION_ID);

    private static final long LOG_POSITION = 100L;
    private static final long LOG_ADDRESS = 456L;

    private static final int INDEX_BLOCK_SIZE = 1024 * 2;
    private static final int READ_BLOCK_SIZE = 1024;
    private static final long TRUNCATE_ADDRESS = 12345L;
    private static final long TRUNCATE_POSITION = 101L;

    private LogBlockIndexController blockIdxController;

    @Mock
    private AgentRunnerService mockAgentRunnerService;

    @Mock
    private LogBlockIndex mockBlockIndex;

    @Mock
    private LogStorage mockLogStorage;

    @Mock
    private SnapshotStorage mockSnapshotStorage;
    @Mock
    private ReadableSnapshot mockSnapshot;
    @Mock
    private SnapshotWriter mockSnapshotWriter;
    @Mock
    private SnapshotPolicy mockSnapshotPolicy;

    @Before
    public void init() throws Exception
    {
        MockitoAnnotations.initMocks(this);

        final FsLogStreamBuilder builder = new FsLogStreamBuilder(TOPIC_NAME_BUFFER, PARTITION_ID)
            .agentRunnerService(mockAgentRunnerService)
            .logStreamControllerDisabled(true)
            .logStorage(mockLogStorage)
            .snapshotStorage(mockSnapshotStorage)
            .snapshotPolicy(mockSnapshotPolicy)
            .logBlockIndex(mockBlockIndex)
            .readBlockSize(READ_BLOCK_SIZE)
            .indexBlockSize(INDEX_BLOCK_SIZE);

        when(mockLogStorage.getFirstBlockAddress()).thenReturn(LOG_ADDRESS);
        when(mockBlockIndex.lookupBlockAddress(anyLong())).thenReturn(LOG_ADDRESS);
        when(mockSnapshotStorage.createSnapshot(anyString(), anyLong())).thenReturn(mockSnapshotWriter);

        blockIdxController = new LogBlockIndexController(builder);

        blockIdxController.doWork();
    }

    @Test
    public void shouldRecoverBlockIndexFromLogStorageWhileOpening() throws Exception
    {
        when(mockSnapshotStorage.getLastSnapshot(LOG_NAME)).thenReturn(null);

        final CompletableFuture<Void> future = blockIdxController.openAsync();

        blockIdxController.doWork();

        assertThat(future).isCompleted();

        verify(mockLogStorage).getFirstBlockAddress();
        assertThat(blockIdxController.getNextAddress()).isEqualTo(LOG_ADDRESS);
    }

    @Test
    public void shouldRecoverBlockIndexFromSnapshotWhileOpening() throws Exception
    {
        when(mockSnapshotStorage.getLastSnapshot(LOG_NAME)).thenReturn(mockSnapshot);
        when(mockSnapshot.getPosition()).thenReturn(100L);

        final CompletableFuture<Void> future = blockIdxController.openAsync();

        blockIdxController.doWork();

        assertThat(future).isCompleted();

        verify(mockSnapshot).recoverFromSnapshot(mockBlockIndex);

        verify(mockBlockIndex).lookupBlockAddress(mockSnapshot.getPosition());
    }

    @Test
    public void shouldOpenIfFailToRecoverBlockIndexFromSnapshot() throws Exception
    {
        when(mockSnapshotStorage.getLastSnapshot(LOG_NAME)).thenReturn(mockSnapshot);
        when(mockSnapshot.getPosition()).thenReturn(100L);

        doThrow(new RuntimeException()).when(mockSnapshot).recoverFromSnapshot(any(SnapshotSupport.class));

        final CompletableFuture<Void> future = blockIdxController.openAsync();

        // when recovery failed
        blockIdxController.doWork();

        // then we open either way
        assertThat(future).isCompleted();
        assertThat(blockIdxController.isOpen()).isTrue();
    }

    @Test
    public void shouldCreateSnapshot() throws Exception
    {
        when(mockBlockIndex.getLogPosition(anyInt())).thenReturn(LOG_POSITION);

        final ByteBuffer buffer = ByteBuffer.allocate(READ_BLOCK_SIZE);
        when(mockLogStorage.read(eq(buffer), eq(LOG_ADDRESS), any(ReadResultProcessor.class)))
            .thenAnswer(readTwoEvents(READ_BLOCK_SIZE + LOG_ADDRESS, READ_BLOCK_SIZE));

        when(mockLogStorage.read(any(ByteBuffer.class), eq(READ_BLOCK_SIZE + LOG_ADDRESS), any(ReadResultProcessor.class)))
            .thenAnswer(readTwoEvents(2 * READ_BLOCK_SIZE + LOG_ADDRESS, READ_BLOCK_SIZE));

        when(mockSnapshotPolicy.apply(LOG_POSITION)).thenReturn(true);

        blockIdxController.openAsync();
        // -> opening
        blockIdxController.doWork();
        // -> open
        blockIdxController.doWork();
        blockIdxController.doWork();
        // -> snapshotting
        blockIdxController.doWork();

        assertThat(blockIdxController.isOpen()).isTrue();

        verify(mockSnapshotStorage).createSnapshot(LOG_NAME, LOG_POSITION);
        verify(mockSnapshotWriter).writeSnapshot(mockBlockIndex);
        verify(mockSnapshotWriter).commit();
    }

    @Test
    public void shouldRefuseSnapshotAndReturnToOpenStateIfFailToWriteSnapshot() throws Exception
    {
        final ByteBuffer buffer = ByteBuffer.allocate(READ_BLOCK_SIZE);
        when(mockLogStorage.read(eq(buffer), eq(LOG_ADDRESS), any(ReadResultProcessor.class)))
            .thenAnswer(readTwoEvents(READ_BLOCK_SIZE + LOG_ADDRESS, READ_BLOCK_SIZE));

        when(mockLogStorage.read(any(ByteBuffer.class), eq(READ_BLOCK_SIZE + LOG_ADDRESS), any(ReadResultProcessor.class)))
            .thenAnswer(readTwoEvents(INDEX_BLOCK_SIZE + LOG_ADDRESS, READ_BLOCK_SIZE));

        when(mockSnapshotPolicy.apply(anyLong())).thenReturn(true);

        doThrow(new RuntimeException("expected exception")).when(mockSnapshotWriter).writeSnapshot(mockBlockIndex);

        blockIdxController.openAsync();
        // -> opening
        blockIdxController.doWork();
        // -> open
        blockIdxController.doWork();
        blockIdxController.doWork();
        // -> snapshotting
        blockIdxController.doWork();

        assertThat(blockIdxController.isOpen()).isTrue();

        verify(mockSnapshotWriter).abort();
    }

    @Test
    public void shouldNotAddBlockForLessThenHalfFullBlock() throws Exception
    {
        final ByteBuffer buffer = ByteBuffer.allocate(READ_BLOCK_SIZE);
        when(mockLogStorage.read(eq(buffer), eq(LOG_ADDRESS), any(ReadResultProcessor.class)))
            .thenAnswer(readTwoEvents(READ_BLOCK_SIZE - 1 + LOG_ADDRESS, READ_BLOCK_SIZE - 1));

        blockIdxController.openAsync();
        // -> opening
        blockIdxController.doWork();
        // -> open
        blockIdxController.doWork();

        // idx for block should be created
        verify(mockBlockIndex, never()).addBlock(anyLong(), anyLong());
    }

    @Test
    public void shouldAddBlockForFullBlock() throws Exception
    {
        final ByteBuffer buffer = ByteBuffer.allocate(READ_BLOCK_SIZE);
        when(mockLogStorage.read(eq(buffer), eq(LOG_ADDRESS), any(ReadResultProcessor.class)))
            .thenAnswer(readTwoEvents(READ_BLOCK_SIZE + LOG_ADDRESS, READ_BLOCK_SIZE));

        when(mockLogStorage.read(any(ByteBuffer.class), eq(READ_BLOCK_SIZE + LOG_ADDRESS), any(ReadResultProcessor.class)))
            .thenAnswer(readTwoEvents(INDEX_BLOCK_SIZE + LOG_ADDRESS, READ_BLOCK_SIZE));

        blockIdxController.openAsync();
        // -> opening
        blockIdxController.doWork();
        // -> open
        blockIdxController.doWork();
        blockIdxController.doWork();

        // idx for block should be created
        verify(mockBlockIndex).addBlock(LOG_POSITION, LOG_ADDRESS);
    }

    @Test
    public void shouldAddHalfFullBlockForHalfDeviation() throws Exception
    {
        // given log block index controller with half deviation
        final FsLogStreamBuilder builder = new FsLogStreamBuilder(TOPIC_NAME_BUFFER, PARTITION_ID)
            .agentRunnerService(mockAgentRunnerService)
            .logStreamControllerDisabled(true)
            .logStorage(mockLogStorage)
            .snapshotStorage(mockSnapshotStorage)
            .snapshotPolicy(mockSnapshotPolicy)
            .logBlockIndex(mockBlockIndex)
            .readBlockSize(READ_BLOCK_SIZE)
            .indexBlockSize(INDEX_BLOCK_SIZE)
            .deviation(0.5f);

        final LogBlockIndexController blockIndexController = new LogBlockIndexController(builder);

        final ByteBuffer buffer = ByteBuffer.allocate(READ_BLOCK_SIZE);
        when(mockLogStorage.read(eq(buffer), eq(LOG_ADDRESS), any(ReadResultProcessor.class)))
            .thenAnswer(readTwoEvents(READ_BLOCK_SIZE + LOG_ADDRESS, READ_BLOCK_SIZE));

        blockIndexController.openAsync();
        // -> opening
        blockIndexController.doWork();
        // -> open
        blockIndexController.doWork();
        blockIndexController.doWork();

        // idx for block should be created
        verify(mockBlockIndex).addBlock(LOG_POSITION, LOG_ADDRESS);
    }

    @Test
    public void shouldNotAddHalfFullBlockForQuarterDeviation() throws Exception
    {
        // given log block index controller with half deviation
        final FsLogStreamBuilder builder = new FsLogStreamBuilder(TOPIC_NAME_BUFFER, PARTITION_ID)
            .agentRunnerService(mockAgentRunnerService)
            .logStreamControllerDisabled(true)
            .logStorage(mockLogStorage)
            .snapshotStorage(mockSnapshotStorage)
            .snapshotPolicy(mockSnapshotPolicy)
            .logBlockIndex(mockBlockIndex)
            .readBlockSize(READ_BLOCK_SIZE)
            .indexBlockSize(INDEX_BLOCK_SIZE)
            .deviation(0.25f);

        final LogBlockIndexController blockIndexController = new LogBlockIndexController(builder);

        final ByteBuffer buffer = ByteBuffer.allocate(READ_BLOCK_SIZE);
        when(mockLogStorage.read(eq(buffer), eq(LOG_ADDRESS), any(ReadResultProcessor.class)))
            .thenAnswer(readTwoEvents(READ_BLOCK_SIZE + LOG_ADDRESS, READ_BLOCK_SIZE));

        blockIndexController.openAsync();
        // -> opening
        blockIndexController.doWork();
        // -> open
        blockIndexController.doWork();
        blockIndexController.doWork();

        // idx for block should be created
        verify(mockBlockIndex, never()).addBlock(LOG_POSITION, LOG_ADDRESS);
    }

    @Test
    public void shouldNotAddBlockToIndexIfLimitIsNotReached() throws Exception
    {
        // given
        final ByteBuffer buffer = ByteBuffer.allocate(READ_BLOCK_SIZE);
        when(mockLogStorage.read(eq(buffer), eq(LOG_ADDRESS), any(ReadResultProcessor.class)))
            .thenAnswer(readTwoEvents(READ_BLOCK_SIZE + LOG_ADDRESS, READ_BLOCK_SIZE));

        when(mockLogStorage.read(any(ByteBuffer.class), eq(READ_BLOCK_SIZE + LOG_ADDRESS), any(ReadResultProcessor.class)))
            .thenAnswer(readTwoEvents(INDEX_BLOCK_SIZE + LOG_ADDRESS, READ_BLOCK_SIZE));

        when(mockLogStorage.read(any(ByteBuffer.class), eq(INDEX_BLOCK_SIZE + LOG_ADDRESS), any(ReadResultProcessor.class)))
            .thenAnswer(readTwoEvents(INDEX_BLOCK_SIZE + 2 * LOG_ADDRESS, (int) LOG_ADDRESS));

        blockIdxController.openAsync();
        // opening
        blockIdxController.doWork();
        // read first block
        blockIdxController.doWork();
        blockIdxController.doWork();
        // read second small block
        blockIdxController.doWork();

        // idx for first block is created
        verify(mockBlockIndex).addBlock(LOG_POSITION, LOG_ADDRESS);
        // second idx is not created since block is not full enough
        verify(mockBlockIndex, never()).addBlock(LOG_POSITION, READ_BLOCK_SIZE + LOG_ADDRESS);
    }

    @Test
    public void shouldTruncateLogStorageFromStart()
    {
        // read block which should be truncated
        when(mockLogStorage.read(any(ByteBuffer.class), eq(LOG_ADDRESS)))
            .thenAnswer(readTwoEvents(READ_BLOCK_SIZE + LOG_ADDRESS, READ_BLOCK_SIZE));

        when(mockLogStorage.getFirstBlockAddress()).thenReturn(LOG_ADDRESS);

        // given open block index controller and empty block index
        blockIdxController.openAsync();
        // -> opening
        blockIdxController.doWork();

        // when truncate is called
        final CompletableFuture<Void> truncateFuture = blockIdxController.truncate(TRUNCATE_POSITION);

        // truncate state
        blockIdxController.doWork();

        // then truncate was successful
        assertThat(truncateFuture.isDone()).isTrue();
        // and controller is again in open state
        assertThat(blockIdxController.isOpen()).isTrue();

        // at first, address is looked up and correct address is resolved
        verify(mockLogStorage, atLeast(2)).getFirstBlockAddress();

        // read is called once, since position is found in first block
        verify(mockLogStorage, times(1)).read(any(ByteBuffer.class), anyLong());

        // at second, block index and log storage from resolved address is truncated
        verify(mockBlockIndex).truncate(TRUNCATE_POSITION);
        verify(mockLogStorage).truncate(LOG_ADDRESS + alignedLength((911)));

        // snapshot is deleted if exist, since no block index exist anymore
        verify(mockSnapshotStorage).purgeSnapshot(LOG_NAME);
    }

    @Test
    public void shouldTruncateLogStorageFromStartAndRepeatRead()
    {
        // read two single block's
        // first one contains not the event
        final int alignedLength = alignedLength(911);
        when(mockLogStorage.read(any(ByteBuffer.class), eq(LOG_ADDRESS)))
            .thenAnswer(readSingleEvent(LOG_ADDRESS + alignedLength, alignedLength(911)));

        // second one contains event on which the truncation should be started
        when(mockLogStorage.read(any(ByteBuffer.class), eq(LOG_ADDRESS + alignedLength)))
            .thenAnswer(readTruncateLog(LOG_ADDRESS + 2 * alignedLength, alignedLength(911)));

        when(mockLogStorage.getFirstBlockAddress()).thenReturn(LOG_ADDRESS);

        // given open block index controller and empty block index
        blockIdxController.openAsync();
        // -> opening
        blockIdxController.doWork();

        // when truncate is called
        final CompletableFuture<Void> truncateFuture = blockIdxController.truncate(TRUNCATE_POSITION);

        // truncate state
        blockIdxController.doWork();

        // then truncate was successful
        assertThat(truncateFuture.isDone()).isTrue();
        // and controller is again in open state
        assertThat(blockIdxController.isOpen()).isTrue();

        // at first, address is looked up and correct address is resolved
        verify(mockLogStorage, atLeast(2)).getFirstBlockAddress();

        // read is called two times since position is searched and found in second block
        verify(mockLogStorage, times(2)).read(any(ByteBuffer.class), anyLong());

        // at second, block index and log storage from resolved address is truncated
        verify(mockBlockIndex).truncate(TRUNCATE_POSITION);
        verify(mockLogStorage).truncate(LOG_ADDRESS + alignedLength((911)));

        // snapshot is deleted if exist, since no block index exist anymore
        verify(mockSnapshotStorage).purgeSnapshot(LOG_NAME);
    }

    @Test
    public void shouldTruncateBlockIndexAndLogStorage()
    {
        // read block which should be truncated
        when(mockLogStorage.read(any(ByteBuffer.class), eq(TRUNCATE_ADDRESS)))
            .thenAnswer(readTwoEvents(READ_BLOCK_SIZE + TRUNCATE_ADDRESS, READ_BLOCK_SIZE));

        when(mockBlockIndex.lookupBlockAddress(TRUNCATE_POSITION)).thenReturn(TRUNCATE_ADDRESS);
        when(mockBlockIndex.size()).thenReturn(1, 0);

        // given open block index controller and block index
        blockIdxController.openAsync();
        // -> opening
        blockIdxController.doWork();

        // when truncate is called
        final CompletableFuture<Void> truncateFuture = blockIdxController.truncate(TRUNCATE_POSITION);

        // truncate state
        blockIdxController.doWork();

        // then truncate was successful
        assertThat(truncateFuture.isDone()).isTrue();
        // and controller is again in open state
        assertThat(blockIdxController.isOpen()).isTrue();

        // at first, address is looked up and correct address is resolved
        verify(mockBlockIndex).lookupBlockAddress(TRUNCATE_POSITION);

        // at second, block index and log storage from resolved address is truncated
        verify(mockBlockIndex).truncate(TRUNCATE_POSITION);
        verify(mockLogStorage).truncate(TRUNCATE_ADDRESS + alignedLength((911)));

        // snapshot is deleted if exist, since no block index exist anymore
        verify(mockSnapshotStorage).purgeSnapshot(LOG_NAME);
    }

    @Test
    public void shouldTruncateAndWriteSnapshot() throws Exception
    {
        final int alignedLength = alignedLength(911);
        when(mockBlockIndex.size()).thenReturn(2, 1);
        when(mockLogStorage.read(any(ByteBuffer.class), eq(TRUNCATE_ADDRESS)))
            .thenAnswer(readSingleEvent(TRUNCATE_ADDRESS + alignedLength, alignedLength));

        when(mockLogStorage.read(any(ByteBuffer.class), eq(TRUNCATE_ADDRESS + alignedLength)))
            .thenAnswer(readTruncateLog(TRUNCATE_ADDRESS + 2 * alignedLength, alignedLength));

        when(mockBlockIndex.lookupBlockAddress(TRUNCATE_POSITION)).thenReturn(TRUNCATE_ADDRESS);

        // given open block index controller with written block index
        blockIdxController.openAsync();
        // -> opening
        blockIdxController.doWork();

        blockIdxController.truncate(TRUNCATE_POSITION);

        // truncate state
        blockIdxController.doWork();

        // when next doWork is called (snapshotting)
        assertThat(blockIdxController.isOpen()).isFalse();
        blockIdxController.doWork();

        // then snapshot is created and controller is again in open state
        assertThat(blockIdxController.isOpen()).isTrue();

        verify(mockSnapshotStorage).createSnapshot(LOG_NAME, 0);
        verify(mockSnapshotWriter).writeSnapshot(mockBlockIndex);
        verify(mockSnapshotWriter).commit();
    }

    @Test
    public void shouldNotTruncateIfNotInOpenState() throws Exception
    {
        // given not open block index controller
        assertThat(blockIdxController.isOpen()).isFalse();

        // when truncate is called
        final CompletableFuture<Void> truncateFuture = blockIdxController.truncate(LOG_POSITION);
        // -> try to truncate
        blockIdxController.doWork();

        // then future is completed exceptionally and controller is not open
        assertThat(truncateFuture.isCompletedExceptionally()).isTrue();
        assertThat(blockIdxController.isOpen()).isFalse();
    }

    @Test
    public void shouldNotTruncateIfPositionWasNotFound()
    {
        final long wrongTruncatePosition = TRUNCATE_POSITION + 1;
        // given custom log block index controller
        // which ioBuffer can only contain two event's
        final int alignedLength = 2 * alignedLength(911);
        final FsLogStreamBuilder builder = new FsLogStreamBuilder(TOPIC_NAME_BUFFER, PARTITION_ID);
        builder.agentRunnerService(mockAgentRunnerService)
            .logStreamControllerDisabled(true)
            .logStorage(mockLogStorage)
            .snapshotStorage(mockSnapshotStorage)
            .snapshotPolicy(mockSnapshotPolicy)
            .logBlockIndex(mockBlockIndex)
            .readBlockSize(alignedLength)
            .indexBlockSize(2 * alignedLength);

        final LogBlockIndexController controller = new LogBlockIndexController(builder);

        when(mockLogStorage.read(any(ByteBuffer.class), eq(TRUNCATE_ADDRESS)))
            .thenAnswer(readTwoEvents(TRUNCATE_ADDRESS + alignedLength, alignedLength));
        when(mockBlockIndex.lookupBlockAddress(wrongTruncatePosition)).thenReturn(TRUNCATE_ADDRESS);
        when(mockBlockIndex.size()).thenReturn(1);

        // given open custom block index controller
        controller.openAsync();
        // -> opening
        controller.doWork();

        // when truncate is called
        final CompletableFuture<Void> truncateFuture = controller.truncate(wrongTruncatePosition);

        // truncate state
        controller.doWork();

        // then truncate was completed exceptionally
        assertThat(truncateFuture.isCompletedExceptionally()).isTrue();
        assertThat(truncateFuture).hasFailedWithThrowableThat().hasMessage("Truncation failed! Position " + wrongTruncatePosition + " was not found.");

        // and controller is again in open state
        assertThat(controller.isOpen()).isTrue();

        // read is called two times, since truncation tries to find the event with the given position
        verify(mockLogStorage, times(2)).read(any(ByteBuffer.class), anyLong());
    }

    private Answer<Object> readTwoEvents(long nextAddr, int blockSize)
    {
        return (InvocationOnMock invocationOnMock) ->
        {
            final ByteBuffer argBuffer = (ByteBuffer) invocationOnMock.getArguments()[0];
            final UnsafeBuffer unsafeBuffer = new UnsafeBuffer(0, 0);
            unsafeBuffer.wrap(argBuffer);

            // set position
            // first event
            unsafeBuffer.putLong(lengthOffset(0), 911);
            unsafeBuffer.putLong(positionOffset(messageOffset(0)), LOG_POSITION);

            // second event
            final int alignedLength = alignedLength(911);
            unsafeBuffer.putLong(lengthOffset(alignedLength), 911);
            unsafeBuffer.putLong(positionOffset(messageOffset(alignedLength)), TRUNCATE_POSITION);

            argBuffer.position(blockSize);
            return nextAddr;
        };
    }

    private Answer<Object> readSingleEvent(long nextAddr, int blockSize)
    {
        return (InvocationOnMock invocationOnMock) ->
        {
            final ByteBuffer argBuffer = (ByteBuffer) invocationOnMock.getArguments()[0];
            putEventToBuffer(blockSize, argBuffer, LOG_POSITION);
            return nextAddr;
        };
    }

    private Answer<Object> readTruncateLog(long nextAddr, int blockSize)
    {
        return (InvocationOnMock invocationOnMock) ->
        {
            final ByteBuffer argBuffer = (ByteBuffer) invocationOnMock.getArguments()[0];
            putEventToBuffer(blockSize, argBuffer, TRUNCATE_POSITION);
            return nextAddr;
        };
    }

    private void putEventToBuffer(int blockSize, ByteBuffer argBuffer, long logPosition)
    {
        final UnsafeBuffer unsafeBuffer = new UnsafeBuffer(0, 0);
        unsafeBuffer.wrap(argBuffer);

        unsafeBuffer.putLong(lengthOffset(0), 911);
        unsafeBuffer.putLong(positionOffset(messageOffset(0)), logPosition);

        argBuffer.position(blockSize);
    }
}
