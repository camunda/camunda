package org.camunda.tngp.logstreams.log;

import org.agrona.concurrent.Agent;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.dispatcher.Subscription;
import org.camunda.tngp.logstreams.fs.FsLogStreamBuilder;
import org.camunda.tngp.logstreams.impl.LogBlockIndexController;
import org.camunda.tngp.logstreams.impl.log.index.LogBlockIndex;
import org.camunda.tngp.logstreams.spi.*;
import org.camunda.tngp.util.agent.AgentRunnerService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.messageOffset;
import static org.camunda.tngp.logstreams.impl.LogEntryDescriptor.positionOffset;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

/**
 * @author Christopher Zell <christopher.zell@camunda.com>
 */
public class LogBlockIndexControllerTest
{
    private static final String LOG_NAME = "test";

    private static final long LOG_POSITION = 100L;
    private static final long LOG_ADDRESS = 456L;

    private static final int INDEX_BLOCK_SIZE = 1024 * 1024 * 2;

    private LogBlockIndexController blockIdxController;

    @Mock
    private AgentRunnerService mockAgentRunnerService;

    @Mock
    private Dispatcher mockWriteBuffer;
    @Mock
    private Subscription mockWriteBufferSubscription;
    @Mock
    private Agent mockWriteBufferConductorAgent;

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

        final FsLogStreamBuilder builder = new FsLogStreamBuilder(LOG_NAME, 0);

        builder.agentRunnerService(mockAgentRunnerService)
            .withoutLogStreamController(true)
            .logStorage(mockLogStorage)
            .snapshotStorage(mockSnapshotStorage)
            .snapshotPolicy(mockSnapshotPolicy)
            .logBlockIndex(mockBlockIndex)
            .indexBlockSize(INDEX_BLOCK_SIZE);

        when(mockLogStorage.getFirstBlockAddress()).thenReturn(LOG_ADDRESS);
        when(mockBlockIndex.lookupBlockAddress(anyLong())).thenReturn(LOG_ADDRESS);
        when(mockWriteBuffer.getSubscriptionByName("log-appender")).thenReturn(mockWriteBufferSubscription);
        when(mockWriteBuffer.getConductorAgent()).thenReturn(mockWriteBufferConductorAgent);
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
    public void shouldNotOpenIfFailToRecoverBlockIndexFromSnapshot() throws Exception
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
        final ByteBuffer buffer = ByteBuffer.allocate(INDEX_BLOCK_SIZE);
        when(mockLogStorage.read(eq(buffer), eq(LOG_ADDRESS), any(ReadResultProcessor.class)))
            .thenAnswer(readAndProcessLog(INDEX_BLOCK_SIZE + LOG_ADDRESS, INDEX_BLOCK_SIZE));
        when(mockSnapshotPolicy.apply(LOG_POSITION)).thenReturn(true);

        blockIdxController.openAsync();
        // -> opening
        blockIdxController.doWork();
        // -> open
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
        final ByteBuffer buffer = ByteBuffer.allocate(INDEX_BLOCK_SIZE);
        when(mockLogStorage.read(eq(buffer), eq(LOG_ADDRESS), any(ReadResultProcessor.class)))
            .thenAnswer(readAndProcessLog(INDEX_BLOCK_SIZE + LOG_ADDRESS, INDEX_BLOCK_SIZE));

        when(mockSnapshotPolicy.apply(anyLong())).thenReturn(true);

        doThrow(new RuntimeException("expected exception")).when(mockSnapshotWriter).writeSnapshot(mockBlockIndex);

        blockIdxController.openAsync();
        // -> opening
        blockIdxController.doWork();
        // -> open
        blockIdxController.doWork();
        // -> snapshotting
        blockIdxController.doWork();

        assertThat(blockIdxController.isOpen()).isTrue();

        verify(mockSnapshotWriter).abort();
    }


    @Test
    public void shouldAddBlockToIndexIfLimitIsReached() throws Exception
    {
        final ByteBuffer buffer = ByteBuffer.allocate(INDEX_BLOCK_SIZE);
        when(mockLogStorage.read(eq(buffer), eq(LOG_ADDRESS), any(ReadResultProcessor.class)))
            .thenAnswer(readAndProcessLog(INDEX_BLOCK_SIZE + LOG_ADDRESS, INDEX_BLOCK_SIZE));

        blockIdxController.openAsync();
        // -> opening
        blockIdxController.doWork();
        // -> open
        blockIdxController.doWork();

        // idx for block should be created
        verify(mockBlockIndex).addBlock(LOG_POSITION, LOG_ADDRESS);
    }

    @Test
    public void shouldNotAddBlockToIndexIfLimitIsNotReached() throws Exception
    {
        // given
        final ByteBuffer buffer = ByteBuffer.allocate(INDEX_BLOCK_SIZE);
        when(mockLogStorage.read(eq(buffer), eq(LOG_ADDRESS), any(ReadResultProcessor.class)))
            .thenAnswer(readAndProcessLog(INDEX_BLOCK_SIZE + LOG_ADDRESS, INDEX_BLOCK_SIZE));
        when(mockLogStorage.read(any(ByteBuffer.class), eq(INDEX_BLOCK_SIZE + LOG_ADDRESS), any(ReadResultProcessor.class)))
            .thenAnswer(readAndProcessLog(INDEX_BLOCK_SIZE + 2 * LOG_ADDRESS, (int) LOG_ADDRESS));

        blockIdxController.openAsync();
        // opening
        blockIdxController.doWork();
        // read first block
        blockIdxController.doWork();
        // read second small block
        blockIdxController.doWork();

        // idx for first block is created
        verify(mockBlockIndex).addBlock(LOG_POSITION, LOG_ADDRESS);
        // second idx is not created since block is not full enough
        verify(mockBlockIndex, never()).addBlock(LOG_POSITION, INDEX_BLOCK_SIZE + LOG_ADDRESS);
    }

    private Answer<Object> readAndProcessLog(long nextAddr, int blockSize)
    {
        return (InvocationOnMock invocationOnMock) ->
        {
            final ByteBuffer argBuffer = (ByteBuffer) invocationOnMock.getArguments()[0];
            final UnsafeBuffer unsafeBuffer = new UnsafeBuffer(0, 0);
            unsafeBuffer.wrap(argBuffer);
            // set position
            unsafeBuffer.putLong(positionOffset(messageOffset(0)), LOG_POSITION);

            argBuffer.position(blockSize);
            return nextAddr;
        };
    }
}
