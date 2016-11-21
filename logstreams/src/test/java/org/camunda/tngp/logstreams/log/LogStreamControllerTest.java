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
package org.camunda.tngp.logstreams.log;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.flagFailed;
import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.flagsOffset;
import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.lengthOffset;
import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.messageOffset;
import static org.camunda.tngp.logstreams.impl.LogEntryDescriptor.positionOffset;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

import org.agrona.concurrent.Agent;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.status.AtomicLongPosition;
import org.camunda.tngp.dispatcher.BlockPeek;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.dispatcher.Subscription;
import org.camunda.tngp.logstreams.LogStreamFailureListener;
import org.camunda.tngp.logstreams.StreamContext;
import org.camunda.tngp.logstreams.impl.LogBlockIndex;
import org.camunda.tngp.logstreams.impl.LogStreamController;
import org.camunda.tngp.logstreams.spi.LogStorage;
import org.camunda.tngp.logstreams.spi.ReadableSnapshot;
import org.camunda.tngp.logstreams.spi.SnapshotPolicy;
import org.camunda.tngp.logstreams.spi.SnapshotStorage;
import org.camunda.tngp.logstreams.spi.SnapshotWriter;
import org.camunda.tngp.util.agent.AgentRunnerService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;

public class LogStreamControllerTest
{
    private static final String LOG_NAME = "test";

    private static final long LOG_POSITION = 100L;
    private static final long LOG_ADDRESS = 456L;

    private static final int MAX_APPEND_BLOCK_SIZE = 1024 * 1024 * 6;
    private static final int INDEX_BLOCK_SIZE = 1024 * 1024 * 2;

    private LogStreamController controller;

    @Mock
    private AgentRunnerService mockAgentRunnerService;

    @Mock
    private Dispatcher mockWriteBuffer;
    @Mock
    private Subscription mockWriteBufferSubscription;

    @Mock
    private LogBlockIndex mockBlockIndex;

    @Mock
    private LogStorage mockLogStorage;

    @Mock
    private LogStreamFailureListener mockFailureListener;

    @Mock
    private SnapshotStorage mockSnapshotStorage;
    @Mock
    private ReadableSnapshot mockSnapshot;
    @Mock
    private SnapshotWriter mockSnapshotWriter;
    @Mock
    private SnapshotPolicy mockSnapshotPolicy;

    private ByteBuffer writeBuffer;

    @Before
    public void init() throws Exception
    {
        MockitoAnnotations.initMocks(this);

        final StreamContext streamContext = new StreamContext();

        streamContext.setAgentRunnerService(mockAgentRunnerService);
        streamContext.setWriteBuffer(mockWriteBuffer);
        streamContext.setLogStorage(mockLogStorage);
        streamContext.setSnapshotStorage(mockSnapshotStorage);
        streamContext.setSnapshotPolicy(mockSnapshotPolicy);
        streamContext.setBlockIndex(mockBlockIndex);
        streamContext.setMaxAppendBlockSize(MAX_APPEND_BLOCK_SIZE);
        streamContext.setIndexBlockSize(INDEX_BLOCK_SIZE);

        when(mockWriteBuffer.getSubscriptionByName("log-appender")).thenReturn(mockWriteBufferSubscription);

        when(mockSnapshotStorage.createSnapshot(anyString(), anyLong())).thenReturn(mockSnapshotWriter);

        controller = new LogStreamController(LOG_NAME, streamContext);

        controller.registerFailureListener(mockFailureListener);

        controller.doWork();
    }

    @Test
    public void shouldGetRoleName()
    {
        assertThat(controller.roleName()).isEqualTo(LOG_NAME);
    }

    @Test
    public void shouldOpen()
    {
        assertThat(controller.isOpen()).isFalse();

        final CompletableFuture<Void> future = controller.openAsync();

        controller.doWork();

        assertThat(future).isCompleted();
        assertThat(controller.isOpen()).isTrue();

        verify(mockAgentRunnerService).run(any(Agent.class));
    }

    @Test
    public void shouldNotOpenIfNotClosed()
    {
        controller.openAsync();
        controller.doWork();

        assertThat(controller.isOpen()).isTrue();

        // try to open again
        final CompletableFuture<Void> future = controller.openAsync();

        controller.doWork();

        assertThat(future).isCompletedExceptionally();
        assertThat(controller.isOpen()).isTrue();

        verify(mockAgentRunnerService, times(1)).run(any(Agent.class));
    }

    @Test
    public void shouldClose()
    {
        controller.openAsync();
        controller.doWork();

        assertThat(controller.isClosed()).isFalse();

        final CompletableFuture<Void> future = controller.closeAsync();

        // -> closing
        controller.doWork();
        // -> closed
        controller.doWork();

        assertThat(future).isCompleted();
        assertThat(controller.isClosed()).isTrue();

        verify(mockAgentRunnerService).remove(any(Agent.class));
        verify(mockLogStorage).close();
    }

    @Test
    public void shouldNotCloseIfNotOpen()
    {
        assertThat(controller.isClosed()).isTrue();

        // try to close again
        final CompletableFuture<Void> future = controller.closeAsync();

        controller.doWork();

        assertThat(future).isCompletedExceptionally();
        assertThat(controller.isClosed()).isTrue();
    }

    @Test
    public void shouldRecoverBlockIndexFromLogStorageWhileOpening() throws Exception
    {
        when(mockSnapshotStorage.getLastSnapshot(LOG_NAME)).thenReturn(null);

        final CompletableFuture<Void> future = controller.openAsync();

        controller.doWork();

        assertThat(future).isCompleted();

        verify(mockBlockIndex).recover(mockLogStorage, INDEX_BLOCK_SIZE);
    }

    @Test
    public void shouldRecoverBlockIndexFromSnapshotWhileOpening() throws Exception
    {
        when(mockSnapshotStorage.getLastSnapshot(LOG_NAME)).thenReturn(mockSnapshot);
        when(mockSnapshot.getPosition()).thenReturn(100L);

        final CompletableFuture<Void> future = controller.openAsync();

        controller.doWork();

        assertThat(future).isCompleted();

        verify(mockSnapshot).recoverFromSnapshot(mockBlockIndex);
        verify(mockSnapshot).validateAndClose();

        verify(mockBlockIndex).recover(mockLogStorage, 100L, INDEX_BLOCK_SIZE);
    }

    @Test
    public void shouldNotOpenIfFailToRecoverBlockIndexFromSnapshot() throws Exception
    {
        when(mockSnapshotStorage.getLastSnapshot(LOG_NAME)).thenReturn(mockSnapshot);
        when(mockSnapshot.getPosition()).thenReturn(100L);

        doThrow(new RuntimeException()).when(mockSnapshot).validateAndClose();

        final CompletableFuture<Void> future = controller.openAsync();

        controller.doWork();

        assertThat(future).isCompletedExceptionally();
        assertThat(controller.isOpen()).isFalse();
    }

    @Test
    public void shouldCreateSnapshot() throws Exception
    {
        when(mockWriteBufferSubscription.peekBlock(any(BlockPeek.class), anyInt(), anyBoolean())).thenAnswer(peekBlock(LOG_POSITION, 64));
        when(mockWriteBufferSubscription.getPosition()).thenReturn(LOG_POSITION);
        when(mockLogStorage.append(any(ByteBuffer.class))).thenReturn(LOG_ADDRESS);

        when(mockSnapshotPolicy.apply(LOG_POSITION)).thenReturn(true);

        controller.openAsync();
        // -> opening
        controller.doWork();
        // -> open
        controller.doWork();
        // -> snapshotting
        controller.doWork();

        assertThat(controller.isOpen()).isTrue();

        verify(mockLogStorage).flush();

        verify(mockSnapshotStorage).createSnapshot(LOG_NAME, LOG_POSITION);
        verify(mockSnapshotWriter).writeSnapshot(mockBlockIndex);
        verify(mockSnapshotWriter).commit();
    }

    @Test
    public void shouldNotCreateSnapshotIfSnapshotPolicyNotApplies() throws Exception
    {
        when(mockWriteBufferSubscription.peekBlock(any(BlockPeek.class), anyInt(), anyBoolean())).thenAnswer(peekBlock(LOG_POSITION, 64));
        when(mockWriteBufferSubscription.getPosition()).thenReturn(LOG_POSITION);
        when(mockLogStorage.append(any(ByteBuffer.class))).thenReturn(LOG_ADDRESS);

        when(mockSnapshotPolicy.apply(anyLong())).thenReturn(false);

        controller.openAsync();
        // -> opening
        controller.doWork();
        // -> open
        controller.doWork();
        // -> snapshotting
        controller.doWork();

        assertThat(controller.isOpen()).isTrue();

        verify(mockSnapshotStorage, never()).createSnapshot(LOG_NAME, LOG_POSITION);
    }

    @Test
    public void shouldRefuseSnapshotAndInvokeFailureListenerIfFailToWriteSnapshot() throws Exception
    {
        when(mockWriteBufferSubscription.peekBlock(any(BlockPeek.class), anyInt(), anyBoolean())).thenAnswer(peekBlock(LOG_POSITION, 64));
        when(mockLogStorage.append(any(ByteBuffer.class))).thenReturn(LOG_ADDRESS);

        when(mockSnapshotPolicy.apply(anyLong())).thenReturn(true);

        doThrow(new RuntimeException("expected exception")).when(mockSnapshotWriter).writeSnapshot(mockBlockIndex);

        controller.openAsync();
        // -> opening
        controller.doWork();
        // -> open
        controller.doWork();
        // -> snapshotting
        controller.doWork();
        // -> failing
        controller.doWork();

        assertThat(controller.isFailed()).isTrue();

        verify(mockSnapshotWriter).abort();

        verify(mockFailureListener).onFailed(LOG_POSITION);
    }

    @Test
    public void shouldWriteBlockFromBufferToLogStorage() throws Exception
    {
        when(mockWriteBufferSubscription.peekBlock(any(BlockPeek.class), anyInt(), anyBoolean())).thenAnswer(peekBlock(LOG_POSITION, 64));
        when(mockLogStorage.append(any(ByteBuffer.class))).thenReturn(LOG_ADDRESS);

        controller.openAsync();
        // -> opening
        controller.doWork();
        // -> open
        final int result = controller.doWork();

        assertThat(result).isEqualTo(1);

        verify(mockLogStorage).append(writeBuffer);
    }

    @Test
    public void shouldAddBlockToIndexIfLimitIsReached() throws Exception
    {
        when(mockWriteBufferSubscription.peekBlock(any(BlockPeek.class), anyInt(), anyBoolean())).thenAnswer(peekBlock(LOG_POSITION, INDEX_BLOCK_SIZE));
        when(mockLogStorage.append(any(ByteBuffer.class))).thenReturn(LOG_ADDRESS);

        controller.openAsync();
        // -> opening
        controller.doWork();
        // -> open
        controller.doWork();

        verify(mockBlockIndex).addBlock(LOG_POSITION, LOG_ADDRESS);
    }

    @Test
    public void shouldNotAddBlockToIndexIfLimitIsNotReached() throws Exception
    {
        when(mockWriteBufferSubscription.peekBlock(any(BlockPeek.class), anyInt(), anyBoolean()))
            .thenAnswer(peekBlock(LOG_POSITION, INDEX_BLOCK_SIZE / 2))
            .thenAnswer(peekBlock(LOG_POSITION + 1, INDEX_BLOCK_SIZE / 2))
            .thenAnswer(peekBlock(LOG_POSITION + 2, INDEX_BLOCK_SIZE / 2));

        when(mockLogStorage.append(any(ByteBuffer.class))).thenReturn(LOG_ADDRESS, LOG_ADDRESS + 1, LOG_ADDRESS + 2);

        controller.openAsync();
        controller.doWork();
        // peek 1. block
        controller.doWork();
        // peek 2. block
        controller.doWork();
        // peek 3. block
        controller.doWork();

        verify(mockBlockIndex).addBlock(LOG_POSITION, LOG_ADDRESS);
        verify(mockBlockIndex).addBlock(LOG_POSITION + 2, LOG_ADDRESS + 2);
        verify(mockBlockIndex, never()).addBlock(LOG_POSITION + 1, LOG_ADDRESS + 1);
    }

    @Test
    public void shouldSpinIfWriteBufferHasNoMoreBytes() throws Exception
    {
        when(mockWriteBufferSubscription.peekBlock(any(BlockPeek.class), anyInt(), anyBoolean())).thenReturn(0);

        controller.openAsync();
        // -> opening
        controller.doWork();
        // -> open
        final int result = controller.doWork();

        assertThat(controller.isOpen()).isTrue();
        assertThat(result).isEqualTo(0);

        verify(mockLogStorage, never()).append(any(ByteBuffer.class));
        verify(mockBlockIndex, never()).addBlock(anyLong(), anyLong());
        verify(mockSnapshotPolicy, never()).apply(anyLong());
    }

    @Test
    public void shouldInvokeFailureListenerIfFailToWriteTheBlock() throws Exception
    {
        when(mockWriteBufferSubscription.peekBlock(any(BlockPeek.class), anyInt(), anyBoolean())).thenAnswer(peekBlock(LOG_POSITION, 64));
        when(mockLogStorage.append(any(ByteBuffer.class))).thenReturn(-1L);

        controller.openAsync();
        // -> opening
        controller.doWork();
        // -> open
        controller.doWork();
        // -> failing
        controller.doWork();

        assertThat(controller.isFailed()).isTrue();

        verify(mockFailureListener).onFailed(LOG_POSITION);

        verify(mockBlockIndex, never()).addBlock(anyLong(), anyLong());
        verify(mockSnapshotPolicy, never()).apply(anyLong());

        final byte flags = writeBuffer.get(flagsOffset(0));
        assertThat(flagFailed(flags)).isTrue();
    }

    @Test
    public void shouldMarkBlocksAsFailedWhileInFailedState() throws Exception
    {
        when(mockWriteBufferSubscription.peekBlock(any(BlockPeek.class), anyInt(), anyBoolean()))
            .thenAnswer(peekBlock(LOG_POSITION, 64))
            .thenAnswer(peekBlock(LOG_POSITION + 1, 86));

        when(mockLogStorage.append(any(ByteBuffer.class))).thenReturn(-1L);

        controller.openAsync();
        // -> opening
        controller.doWork();
        // -> open
        controller.doWork();
        // -> failing
        controller.doWork();
        // -> failed
        controller.doWork();

        assertThat(controller.isFailed()).isTrue();

        verify(mockWriteBufferSubscription, times(2)).peekBlock(any(BlockPeek.class), anyInt(), anyBoolean());

        final byte flags = writeBuffer.get(flagsOffset(0));
        assertThat(flagFailed(flags)).isTrue();
    }

    @Test
    public void shouldRemoveFailureListener() throws Exception
    {
        controller.removeFailureListener(mockFailureListener);

        when(mockWriteBufferSubscription.peekBlock(any(BlockPeek.class), anyInt(), anyBoolean())).thenAnswer(peekBlock(LOG_POSITION, 64));
        when(mockLogStorage.append(any(ByteBuffer.class))).thenReturn(-1L);

        controller.openAsync();
        // -> opening
        controller.doWork();
        // -> open
        controller.doWork();
        // -> failing
        controller.doWork();

        assertThat(controller.isFailed()).isTrue();

        verify(mockFailureListener, never()).onFailed(anyLong());
    }

    protected Answer<Integer> peekBlock(long logPosition, int bytesRead)
    {
        return invocation ->
        {
            final BlockPeek blockPeek = (BlockPeek) invocation.getArguments()[0];

            final UnsafeBuffer buffer = new UnsafeBuffer(new byte[bytesRead]);
            writeBuffer = ByteBuffer.wrap(buffer.byteArray());

            final int positionOffset = positionOffset(messageOffset(0));
            buffer.putLong(positionOffset, logPosition);

            buffer.putLong(lengthOffset(0), bytesRead);

            blockPeek.setBlock(writeBuffer, new AtomicLongPosition(), 0, 0, bytesRead, 0, bytesRead);

            return bytesRead;
        };
    }

}
