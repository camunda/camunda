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
package org.camunda.tngp.logstreams.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.util.buffer.BufferUtil.wrapString;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.Agent;
import org.camunda.tngp.logstreams.log.LogStream;
import org.camunda.tngp.logstreams.log.LogStreamFailureListener;
import org.camunda.tngp.logstreams.log.LogStreamReader;
import org.camunda.tngp.logstreams.log.LogStreamWriter;
import org.camunda.tngp.logstreams.log.LoggedEvent;
import org.camunda.tngp.logstreams.spi.ReadableSnapshot;
import org.camunda.tngp.logstreams.spi.SnapshotPolicy;
import org.camunda.tngp.logstreams.spi.SnapshotPositionProvider;
import org.camunda.tngp.logstreams.spi.SnapshotStorage;
import org.camunda.tngp.logstreams.spi.SnapshotSupport;
import org.camunda.tngp.logstreams.spi.SnapshotWriter;
import org.camunda.tngp.util.DeferredCommandContext;
import org.camunda.tngp.util.agent.AgentRunnerService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class StreamProcessorControllerTest
{
    private static final String STREAM_PROCESSOR_NAME = "name";
    private static final int STREAM_PROCESSOR_ID = 1;

    private static final DirectBuffer SOURCE_LOG_STREAM_TOPIC_NAME = wrapString("source-topic");
    private static final int SOURCE_LOG_STREAM_PARTITION_ID = 1;
    private static final DirectBuffer TARGET_LOG_STREAM_TOPIC_NAME = wrapString("target-topic");
    private static final int TARGET_LOG_STREAM_PARTITION_ID = 2;

    private StreamProcessorController controller;

    @Mock
    private StreamProcessor mockStreamProcessor;

    @Mock
    private EventProcessor mockEventProcessor;

    @Mock
    private AgentRunnerService mockAgentRunnerService;

    @Mock
    private LogStream mockSourceLogStream;

    @Mock
    private LogStream mockTargetLogStream;

    private MockLogStreamReader mockSourceLogStreamReader;

    @Mock
    private LogStreamReader mockTargetLogStreamReader;

    @Mock
    private LogStreamWriter mockLogStreamWriter;

    @Mock
    private SnapshotPolicy mockSnapshotPolicy;

    @Mock
    private SnapshotStorage mockSnapshotStorage;

    @Mock
    private SnapshotPositionProvider mockSnapshotPositionProvider;

    @Mock
    private SnapshotWriter mockSnapshotWriter;

    @Mock
    private ReadableSnapshot mockReadableSnapshot;

    @Mock
    private SnapshotSupport mockStateResource;

    @Mock
    private LoggedEvent mockSourceEvent;

    @Mock
    private LoggedEvent mockTargetEvent;

    private LogStreamFailureListener targetLogStreamFailureListener;

    protected ControllableEventFilter eventFilter;
    protected ControllableEventFilter reprocessingEventFilter;

    private final DeferredCommandContext streamProcessorCmdQueue = new DeferredCommandContext(100);

    protected TestStreamProcessorBuilder builder;

    @Before
    public void init() throws Exception
    {

        MockitoAnnotations.initMocks(this);

        eventFilter = new ControllableEventFilter();
        reprocessingEventFilter = new ControllableEventFilter();

        mockSourceLogStreamReader = new MockLogStreamReader();
        builder = new TestStreamProcessorBuilder(STREAM_PROCESSOR_ID, STREAM_PROCESSOR_NAME, mockStreamProcessor)
                .agentRunnerService(mockAgentRunnerService)
                .sourceStream(mockSourceLogStream)
                .targetStream(mockTargetLogStream)
                .sourceLogStreamReader(mockSourceLogStreamReader)
                .targetLogStreamReader(mockTargetLogStreamReader)
                .logStreamWriter(mockLogStreamWriter)
                .snapshotPolicy(mockSnapshotPolicy)
                .snapshotStorage(mockSnapshotStorage)
                .snapshotPositionProvider(mockSnapshotPositionProvider)
                .streamProcessorCmdQueue(streamProcessorCmdQueue)
                .eventFilter(eventFilter)
                .reprocessingEventFilter(reprocessingEventFilter);

        when(mockStreamProcessor.onEvent(any(LoggedEvent.class))).thenReturn(mockEventProcessor);

        when(mockStreamProcessor.getStateResource()).thenReturn(mockStateResource);
        when(mockSnapshotStorage.createSnapshot(anyString(), anyLong())).thenReturn(mockSnapshotWriter);

        when(mockSourceLogStream.getTopicName()).thenReturn(SOURCE_LOG_STREAM_TOPIC_NAME);
        when(mockSourceLogStream.getPartitionId()).thenReturn(SOURCE_LOG_STREAM_PARTITION_ID);

        when(mockTargetLogStream.getTopicName()).thenReturn(TARGET_LOG_STREAM_TOPIC_NAME);
        when(mockTargetLogStream.getPartitionId()).thenReturn(TARGET_LOG_STREAM_PARTITION_ID);

        when(mockLogStreamWriter.producerId(anyInt())).thenReturn(mockLogStreamWriter);
        when(mockLogStreamWriter.sourceEvent(any(DirectBuffer.class), anyInt(), anyLong())).thenReturn(mockLogStreamWriter);

        controller = builder.build();

        doAnswer(invocation ->
        {
            // this is invoked while opening
            targetLogStreamFailureListener = (LogStreamFailureListener) invocation.getArguments()[0];
            return null;
        }).when(mockTargetLogStream).registerFailureListener(any(LogStreamFailureListener.class));
    }

    @Test
    public void shouldGetRoleName()
    {
        assertThat(controller.roleName()).isEqualTo(STREAM_PROCESSOR_NAME);
    }

    @Test
    public void shouldOpen()
    {
        assertThat(controller.isOpen()).isFalse();

        final CompletableFuture<Void> future = controller.openAsync();

        // -> opening
        controller.doWork();

        assertThat(future).isNotCompleted();
        assertThat(controller.isOpen()).isFalse();

        // -> recovery
        controller.doWork();
        // -> re-processing
        controller.doWork();

        assertThat(future).isCompleted();
        assertThat(controller.isOpen()).isTrue();

        verify(mockAgentRunnerService).run(any(Agent.class));

        assertThat(mockSourceLogStreamReader.getMockingLog()).isEqualTo(mockSourceLogStream);
        verify(mockTargetLogStreamReader).wrap(mockTargetLogStream);
        verify(mockLogStreamWriter).wrap(mockTargetLogStream);
        verify(mockStreamProcessor).onOpen(any(StreamProcessorContext.class));
    }

    @Test
    public void shouldNotOpenIfNotClosed()
    {
        open();
        mockSourceLogStreamReader.addEvent(mockSourceEvent);
        mockSourceLogStreamReader.seek(1L);

        assertThat(controller.isOpen()).isTrue();

        // try to open again
        final CompletableFuture<Void> future = controller.openAsync();

        controller.doWork();

        assertThat(future).isCompletedExceptionally();
        assertThat(controller.isOpen()).isTrue();

        verify(mockAgentRunnerService, times(1)).run(any(Agent.class));

        assertThat(mockSourceLogStreamReader.getMockingLog()).isEqualTo(mockSourceLogStream);
        assertThat(mockSourceLogStreamReader.getPosition()).isEqualTo(1L);
        verify(mockLogStreamWriter, times(1)).wrap(mockTargetLogStream);
        verify(mockStreamProcessor, times(1)).onOpen(any(StreamProcessorContext.class));
    }

    @Test
    public void shouldCloseWhilePollEvents()
    {
        open();

        assertThat(controller.isClosed()).isFalse();

        final CompletableFuture<Void> future = controller.closeAsync();

        // -> closingSnapshotting
        controller.doWork();
        // -> closing
        controller.doWork();
        // -> closed
        controller.doWork();

        assertThat(future).isCompleted();
        assertThat(controller.isClosed()).isTrue();

        verify(mockStreamProcessor).onClose();

        verify(mockAgentRunnerService).remove(any(Agent.class));
    }

    @Test
    public void shouldCloseWhileProcessing()
    {
        mockSourceLogStreamReader.addEvent(mockSourceEvent);

        when(mockEventProcessor.executeSideEffects()).thenReturn(false);

        open();

        // -> open
        controller.doWork();
        // -> processing
        controller.doWork();

        assertThat(controller.isOpen()).isTrue();

        final CompletableFuture<Void> future = controller.closeAsync();

        // -> closingSnapshotting
        controller.doWork();
        // -> closing
        controller.doWork();
        // -> closed
        controller.doWork();

        assertThat(future).isCompleted();
        assertThat(controller.isClosed()).isTrue();
    }

    @Test
    public void shouldCloseWhileSnapshotting()
    {
        mockSourceLogStreamReader.addEvent(mockSourceEvent);

        when(mockEventProcessor.executeSideEffects()).thenReturn(true);
        when(mockEventProcessor.writeEvent(mockLogStreamWriter)).thenReturn(2L);

        when(mockSnapshotPolicy.apply(anyLong())).thenReturn(true);
        when(mockTargetLogStream.getCurrentAppenderPosition()).thenReturn(3L);

        open();

        // -> open
        controller.doWork();
        // -> processing
        controller.doWork();
        // -> snapshotting
        controller.doWork();

        assertThat(controller.isOpen()).isTrue();

        final CompletableFuture<Void> future = controller.closeAsync();

        // -> closingSnapshotting
        controller.doWork();
        // -> closing
        controller.doWork();
        // -> closed
        controller.doWork();

        assertThat(future).isCompleted();
        assertThat(controller.isClosed()).isTrue();
    }

    @Test
    public void shouldCloseWhileSnapshottingAndWaitForLogAppender()
    {
        mockSourceLogStreamReader.addEvent(mockSourceEvent);

        when(mockEventProcessor.executeSideEffects()).thenReturn(true);
        when(mockEventProcessor.writeEvent(mockLogStreamWriter)).thenReturn(2L);

        when(mockSnapshotPolicy.apply(anyLong())).thenReturn(true);
        when(mockTargetLogStream.getCurrentAppenderPosition()).thenReturn(1L);

        open();

        // -> open
        controller.doWork();
        // -> processing
        controller.doWork();
        // -> snapshotting
        controller.doWork();

        assertThat(controller.isOpen()).isTrue();

        final CompletableFuture<Void> future = controller.closeAsync();

        // -> closingSnapshotting
        controller.doWork();

        assertThat(controller.isClosing()).isTrue();

        // -> closingSnapshotting
        controller.doWork();

        when(mockTargetLogStream.getCurrentAppenderPosition()).thenReturn(2L);

        // -> closingSnapshotting
        controller.doWork();

        assertThat(controller.isClosing()).isTrue();

        // -> closing
        controller.doWork();
        // -> closed
        controller.doWork();

        assertThat(future).isCompleted();
        assertThat(controller.isClosed()).isTrue();
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
    public void shouldPollEventsWhenOpen()
    {
        open();

        // -> open
        controller.doWork();

        // event becomes available
        mockSourceLogStreamReader.addEvent(mockSourceEvent);

        // -> open
        controller.doWork();
        // -> process
        controller.doWork();

        verify(mockStreamProcessor).onEvent(mockSourceEvent);
    }

    @Test
    public void shouldDrainStreamProcessorCmdQueueWhenOpen()
    {
        final AtomicBoolean isExecuted = new AtomicBoolean(false);
        streamProcessorCmdQueue.runAsync(() -> isExecuted.set(true));

        open();

        // -> open
        controller.doWork();

        assertThat(isExecuted.get()).isTrue();
    }

    @Test
    public void shouldProcessPolledEvent()
    {
        mockSourceLogStreamReader.addEvent(mockSourceEvent);

        when(mockEventProcessor.executeSideEffects()).thenReturn(true);
        when(mockEventProcessor.writeEvent(mockLogStreamWriter)).thenReturn(1L);

        open();

        // -> open
        controller.doWork();
        // -> processing
        controller.doWork();

        assertThat(controller.isOpen()).isTrue();

        final InOrder inOrder = inOrder(mockStreamProcessor, mockEventProcessor);

        inOrder.verify(mockStreamProcessor).onOpen(any(StreamProcessorContext.class));
        inOrder.verify(mockStreamProcessor).onEvent(mockSourceEvent);

        inOrder.verify(mockEventProcessor).processEvent();
        inOrder.verify(mockEventProcessor).executeSideEffects();
        inOrder.verify(mockEventProcessor).writeEvent(mockLogStreamWriter);
        inOrder.verify(mockEventProcessor).updateState();

        inOrder.verify(mockStreamProcessor).afterEvent();
    }

    @Test
    public void shouldNotPollEventsIfSuspended()
    {
        final AtomicBoolean isExecuted = new AtomicBoolean(false);
        streamProcessorCmdQueue.runAsync(() -> isExecuted.set(true));

        when(mockStreamProcessor.isSuspended()).thenReturn(true);

        open();

        // -> open
        controller.doWork();

        assertThat(isExecuted.get()).isTrue();
        assertThat(mockSourceLogStreamReader.getHasNextInvocations()).isEqualTo(0);
    }

    @Test
    public void shouldSkipProcessingEventIfNoProcessorIsAvailable()
    {
        mockSourceLogStreamReader.addEvent(mockSourceEvent);

        when(mockStreamProcessor.onEvent(mockSourceEvent)).thenReturn(null);

        open();

        // -> open
        controller.doWork();
        // -> processing
        controller.doWork();

        assertThat(controller.isOpen()).isTrue();

        verify(mockStreamProcessor).onEvent(mockSourceEvent);

        verify(mockEventProcessor, never()).processEvent();
        verify(mockEventProcessor, never()).executeSideEffects();
        verify(mockEventProcessor, never()).writeEvent(mockLogStreamWriter);
        verify(mockEventProcessor, never()).updateState();
        verify(mockStreamProcessor, never()).afterEvent();
    }

    @Test
    public void shouldRetryExecuteSideEffectsIfFail()
    {
        mockSourceLogStreamReader.addEvent(mockSourceEvent);

        when(mockEventProcessor.executeSideEffects()).thenReturn(false, true);
        when(mockEventProcessor.writeEvent(mockLogStreamWriter)).thenReturn(1L);

        open();

        // -> open
        controller.doWork();
        // -> process (fail)
        controller.doWork();
        // -> retry process
        controller.doWork();

        verify(mockEventProcessor, times(1)).processEvent();
        verify(mockEventProcessor, times(2)).executeSideEffects();
        verify(mockEventProcessor, times(1)).writeEvent(mockLogStreamWriter);
        verify(mockEventProcessor, times(1)).updateState();
    }

    @Test
    public void shouldRetryWriteEventIfFail()
    {
        mockSourceLogStreamReader.addEvent(mockSourceEvent);

        when(mockEventProcessor.executeSideEffects()).thenReturn(true);
        when(mockEventProcessor.writeEvent(mockLogStreamWriter)).thenReturn(-1L, 1L);

        open();

        // -> open
        controller.doWork();
        // -> process (fail)
        controller.doWork();
        // -> retry process
        controller.doWork();

        verify(mockEventProcessor, times(1)).processEvent();
        verify(mockEventProcessor, times(1)).executeSideEffects();
        verify(mockEventProcessor, times(2)).writeEvent(mockLogStreamWriter);
        verify(mockEventProcessor, times(1)).updateState();
    }

    @Test
    public void shouldAddProducerIdToWrittenEvent()
    {
        mockSourceLogStreamReader.addEvent(mockSourceEvent);

        when(mockEventProcessor.executeSideEffects()).thenReturn(true);
        when(mockEventProcessor.writeEvent(mockLogStreamWriter)).thenReturn(1L);

        open();

        // -> open
        controller.doWork();
        // -> processing
        controller.doWork();

        verify(mockLogStreamWriter).producerId(STREAM_PROCESSOR_ID);
    }

    @Test
    public void shouldAddSourceEventToWrittenEvent()
    {
        mockSourceLogStreamReader.addEvent(mockSourceEvent);

        when(mockEventProcessor.executeSideEffects()).thenReturn(true);
        when(mockEventProcessor.writeEvent(mockLogStreamWriter)).thenReturn(1L);

        when(mockSourceLogStream.getTopicName()).thenReturn(SOURCE_LOG_STREAM_TOPIC_NAME);
        when(mockSourceLogStream.getPartitionId()).thenReturn(SOURCE_LOG_STREAM_PARTITION_ID);
        when(mockSourceEvent.getPosition()).thenReturn(4L);

        open();

        // -> open
        controller.doWork();
        // -> processing
        controller.doWork();

        verify(mockLogStreamWriter).sourceEvent(SOURCE_LOG_STREAM_TOPIC_NAME, SOURCE_LOG_STREAM_PARTITION_ID, 4L);
    }

    @Test
    public void shouldCreateSnapshotAtProvidedPosition() throws Exception
    {
        mockSourceLogStreamReader.addEvent(mockSourceEvent);

        when(mockEventProcessor.executeSideEffects()).thenReturn(true);
        when(mockEventProcessor.writeEvent(mockLogStreamWriter)).thenReturn(2L);

        when(mockSnapshotPolicy.apply(anyLong())).thenReturn(true);
        when(mockTargetLogStream.getCurrentAppenderPosition()).thenReturn(2L);

        when(mockSnapshotPositionProvider.getSnapshotPosition(any(), anyLong())).thenReturn(5L);

        open();

        // -> open
        controller.doWork();
        // -> processing
        controller.doWork();
        // -> snapshotting
        controller.doWork();

        assertThat(controller.isOpen()).isTrue();

        verify(mockSnapshotStorage).createSnapshot(STREAM_PROCESSOR_NAME, 5L);
        verify(mockSnapshotWriter).writeSnapshot(mockStateResource);
        verify(mockSnapshotWriter).commit();
    }

    @Test
    public void shouldNotCreateSnapshotIfPolicyNotApplied() throws Exception
    {
        mockSourceLogStreamReader.addEvent(mockSourceEvent);

        when(mockEventProcessor.executeSideEffects()).thenReturn(true);
        when(mockEventProcessor.writeEvent(mockLogStreamWriter)).thenReturn(1L);

        when(mockSnapshotPolicy.apply(anyLong())).thenReturn(false);
        when(mockTargetLogStream.getCurrentAppenderPosition()).thenReturn(1L);

        open();

        // -> open
        controller.doWork();
        // -> processing
        controller.doWork();
        // -> open
        controller.doWork();

        assertThat(controller.isOpen()).isTrue();

        verify(mockSnapshotStorage, never()).createSnapshot(anyString(), anyLong());
    }

    @Test
    public void shouldNotCreateSnapshotIfNoEventIsWritten() throws Exception
    {
        mockSourceLogStreamReader.addEvent(mockSourceEvent);

        when(mockEventProcessor.executeSideEffects()).thenReturn(true);
        when(mockEventProcessor.writeEvent(mockLogStreamWriter)).thenReturn(0L);

        when(mockSnapshotPolicy.apply(anyLong())).thenReturn(true);
        when(mockTargetLogStream.getCurrentAppenderPosition()).thenReturn(1L);

        open();

        // -> open
        controller.doWork();
        // -> processing
        controller.doWork();
        // -> open
        controller.doWork();

        assertThat(controller.isOpen()).isTrue();

        verify(mockSnapshotStorage, never()).createSnapshot(anyString(), anyLong());
    }

    @Test
    public void shouldCreateSnapshotWhenLogAppenderWroteNewlyCreatedEvent() throws Exception
    {
        mockSourceLogStreamReader.addEvent(mockSourceEvent);

        when(mockEventProcessor.executeSideEffects()).thenReturn(true);
        when(mockEventProcessor.writeEvent(mockLogStreamWriter)).thenReturn(2L);

        when(mockSnapshotPolicy.apply(anyLong())).thenReturn(true);
        when(mockTargetLogStream.getCurrentAppenderPosition()).thenReturn(1L, 2L);

        when(mockSnapshotPositionProvider.getSnapshotPosition(any(), anyLong())).thenReturn(2L);

        open();

        // -> open
        controller.doWork();
        // -> processing
        controller.doWork();
        // -> snapshotting
        controller.doWork();
        // -> stay in snapshotting state until log appender wrote the newly created event
        controller.doWork();

        assertThat(controller.isOpen()).isTrue();

        assertThat(mockSourceLogStreamReader.getHasNextInvocations()).isEqualTo(1);
        verify(mockTargetLogStream, times(2)).getCurrentAppenderPosition();

        verify(mockSnapshotStorage, times(1)).createSnapshot(STREAM_PROCESSOR_NAME, 2L);
        verify(mockSnapshotWriter, times(1)).writeSnapshot(mockStateResource);
        verify(mockSnapshotWriter, times(1)).commit();
    }

    @Test
    public void shouldFailLogStreamWhileProcessing() throws Exception
    {
        mockSourceLogStreamReader.addEvent(mockSourceEvent);

        when(mockEventProcessor.executeSideEffects()).thenReturn(true);
        when(mockEventProcessor.writeEvent(mockLogStreamWriter)).thenReturn(-1L);

        open();

        // -> open
        controller.doWork();
        // -> processing
        controller.doWork();

        targetLogStreamFailureListener.onFailed(2L);

        // -> failed
        controller.doWork();

        assertThat(controller.isFailed()).isTrue();

        verify(mockEventProcessor, never()).updateState();
    }

    @Test
    public void shouldFailLogStreamWhileSnapshotting() throws Exception
    {
        mockSourceLogStreamReader.addEvent(mockSourceEvent);

        when(mockEventProcessor.executeSideEffects()).thenReturn(true);
        when(mockEventProcessor.writeEvent(mockLogStreamWriter)).thenReturn(2L);

        when(mockSnapshotPolicy.apply(anyLong())).thenReturn(true);
        when(mockTargetLogStream.getCurrentAppenderPosition()).thenReturn(1L);

        open();

        // -> open
        controller.doWork();
        // -> processing
        controller.doWork();
        // -> snapshotting
        controller.doWork();

        targetLogStreamFailureListener.onFailed(2L);

        // -> failed
        controller.doWork();

        assertThat(controller.isFailed()).isTrue();

        assertThat(mockSourceLogStreamReader.getHasNextInvocations()).isEqualTo(1);
        verify(mockSnapshotStorage, never()).createSnapshot(STREAM_PROCESSOR_NAME, 2L);
    }

    @Test
    public void shouldFailLogStreamWhilePollEvents() throws Exception
    {
        open();

        // -> open
        controller.doWork();

        targetLogStreamFailureListener.onFailed(1L);

        // -> failed
        controller.doWork();

        assertThat(controller.isFailed()).isTrue();

        assertThat(mockSourceLogStreamReader.getHasNextInvocations()).isEqualTo(1);
    }

    @Test
    public void shouldRecoverAfterFailLogStream() throws Exception
    {
        mockSourceLogStreamReader.addEvent(mockSourceEvent);
        final LoggedEvent secondEvent = eventAt(1L);
        mockSourceLogStreamReader.addEvent(secondEvent);

        when(mockEventProcessor.executeSideEffects()).thenReturn(true);
        when(mockEventProcessor.writeEvent(mockLogStreamWriter)).thenReturn(2L);

        open();

        // -> open
        controller.doWork();
        // -> processing
        controller.doWork();

        targetLogStreamFailureListener.onFailed(2L);
        // -> failed
        controller.doWork();

        assertThat(controller.isFailed()).isTrue();

        targetLogStreamFailureListener.onRecovered();
        // -> recover
        controller.doWork();
        // -> re-process - no more events
        controller.doWork();

        assertThat(controller.isOpen()).isTrue();

        assertThat(mockSourceLogStreamReader.next()).isEqualTo(secondEvent);

        verify(mockSnapshotStorage, times(2)).getLastSnapshot(STREAM_PROCESSOR_NAME);
        verify(mockStreamProcessor, times(2)).onOpen(any(StreamProcessorContext.class));

        verify(mockStateResource, times(2)).reset();
    }

    @Test
    public void shouldNotRecoverIfFailedEventPositionIsAfterWrittenEventPosition() throws Exception
    {
        mockSourceLogStreamReader.addEvent(mockSourceEvent);
        final LoggedEvent secondEvent = eventAt(1L);
        mockSourceLogStreamReader.addEvent(secondEvent);

        when(mockEventProcessor.executeSideEffects()).thenReturn(true);
        when(mockEventProcessor.writeEvent(mockLogStreamWriter)).thenReturn(2L);

        open();

        // -> open
        controller.doWork();
        // -> processing
        controller.doWork();

        targetLogStreamFailureListener.onFailed(3L);
        // -> failed
        controller.doWork();

        assertThat(controller.isFailed()).isTrue();

        targetLogStreamFailureListener.onRecovered();
        // -> open
        controller.doWork();

        assertThat(controller.isOpen()).isTrue();

        assertThat(mockSourceLogStreamReader.getHasNextInvocations()).isEqualTo(2);
        assertThat(mockSourceLogStreamReader.getPosition()).isEqualTo(1L);

        verify(mockSnapshotStorage, times(1)).getLastSnapshot(STREAM_PROCESSOR_NAME);
        verify(mockStreamProcessor, times(1)).onOpen(any(StreamProcessorContext.class));

        verify(mockStateResource, times(1)).reset();
    }

    @Test
    public void shouldRegisterFailureListener() throws Exception
    {
        open();

        controller.closeAsync();

        // -> closingSnapshotting
        controller.doWork();
        // -> closing
        controller.doWork();

        verify(mockTargetLogStream, times(1)).registerFailureListener(targetLogStreamFailureListener);
        verify(mockTargetLogStream, times(2)).removeFailureListener(targetLogStreamFailureListener);
    }

    @Test
    public void shouldRecoverFromSnapshot() throws Exception
    {
        when(mockSnapshotStorage.getLastSnapshot(STREAM_PROCESSOR_NAME)).thenReturn(mockReadableSnapshot);
        when(mockReadableSnapshot.getPosition()).thenReturn(5L);

        when(mockTargetLogStreamReader.seek(5L)).thenReturn(true);
        when(mockTargetLogStreamReader.hasNext()).thenReturn(true, false);
        when(mockTargetLogStreamReader.next()).thenReturn(mockTargetEvent);

        when(mockTargetEvent.getSourceEventPosition()).thenReturn(3L);

        when(mockSourceEvent.getPosition()).thenReturn(3L);
        mockSourceLogStreamReader.addEvent(mockSourceEvent);

        open();

        assertThat(controller.isOpen()).isTrue();

        verify(mockSnapshotStorage).getLastSnapshot(STREAM_PROCESSOR_NAME);

        verify(mockReadableSnapshot).recoverFromSnapshot(mockStateResource);

        verify(mockTargetLogStreamReader).seek(5L);
        verify(mockTargetLogStreamReader, times(2)).hasNext();
        verify(mockTargetLogStreamReader, times(1)).next();

        assertThat(mockSourceLogStreamReader.getPosition()).isEqualTo(4L);
    }

    @Test
    public void shouldRecoverFromSnapshotIfSourceEqualsTargetStream() throws Exception
    {
        when(mockSourceLogStream.getTopicName()).thenReturn(SOURCE_LOG_STREAM_TOPIC_NAME);
        when(mockTargetLogStream.getPartitionId()).thenReturn(SOURCE_LOG_STREAM_PARTITION_ID);

        when(mockSnapshotStorage.getLastSnapshot(STREAM_PROCESSOR_NAME)).thenReturn(mockReadableSnapshot);
        when(mockReadableSnapshot.getPosition()).thenReturn(5L);

        when(mockTargetLogStreamReader.seek(5L)).thenReturn(true);
        when(mockTargetLogStreamReader.hasNext()).thenReturn(true, false);
        when(mockTargetLogStreamReader.next()).thenReturn(mockTargetEvent);

        when(mockTargetEvent.getSourceEventPosition()).thenReturn(3L);
        when(mockSourceEvent.getPosition()).thenReturn(3L);
        mockSourceLogStreamReader.addEvent(mockSourceEvent);

        open();

        assertThat(controller.isOpen()).isTrue();

        verify(mockSnapshotStorage).getLastSnapshot(STREAM_PROCESSOR_NAME);

        verify(mockReadableSnapshot).recoverFromSnapshot(mockStateResource);

        verify(mockTargetLogStreamReader).seek(5L);
        verify(mockTargetLogStreamReader, times(2)).hasNext();
        verify(mockTargetLogStreamReader, times(1)).next();

        assertThat(mockSourceLogStreamReader.getPosition()).isEqualTo(6L);
    }

    @Test
    public void shouldReprocessEventsOnRecovery() throws Exception
    {
        reprocessingEventFilter.doFilter = true;

        when(mockTargetLogStreamReader.hasNext()).thenReturn(true, false);
        when(mockTargetLogStreamReader.next()).thenReturn(mockTargetEvent);

        when(mockTargetEvent.getProducerId()).thenReturn(STREAM_PROCESSOR_ID);
        when(mockTargetEvent.getSourceEventPosition()).thenReturn(3L);

        when(mockSourceEvent.getPosition()).thenReturn(3L);
        mockSourceLogStreamReader.addEvent(mockSourceEvent);

        open();
        // -> recovery - no more events
        controller.doWork();

        assertThat(controller.isOpen()).isTrue();

        verify(mockSnapshotStorage).getLastSnapshot(STREAM_PROCESSOR_NAME);
        verify(mockTargetLogStreamReader, never()).seek(anyLong());

        verify(mockTargetLogStreamReader, times(2)).hasNext();
        verify(mockTargetLogStreamReader, times(1)).next();

        final InOrder inOrder = inOrder(mockStreamProcessor, mockEventProcessor);

        inOrder.verify(mockStreamProcessor).onEvent(mockSourceEvent);
        inOrder.verify(mockEventProcessor).processEvent();
        inOrder.verify(mockEventProcessor).updateState();
        inOrder.verify(mockStreamProcessor).afterEvent();

        inOrder.verify(mockStreamProcessor).onOpen(any(StreamProcessorContext.class));

        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void shouldIgnoreEventsFromOtherProducerOnRecovery() throws Exception
    {
        when(mockTargetLogStreamReader.hasNext()).thenReturn(true, true, false);
        when(mockTargetLogStreamReader.next()).thenReturn(mockTargetEvent);

        when(mockTargetEvent.getProducerId()).thenReturn(99, STREAM_PROCESSOR_ID);
        when(mockTargetEvent.getSourceEventPosition()).thenReturn(4L);

        mockSourceLogStreamReader.addEvent(mockSourceEvent);

        when(mockSourceEvent.getPosition()).thenReturn(4L);

        open();
        // -> recovery - re-process event
        controller.doWork();
        // -> recovery - no more events
        controller.doWork();

        assertThat(controller.isOpen()).isTrue();

        verify(mockTargetLogStreamReader, times(3)).hasNext();
        verify(mockTargetLogStreamReader, times(2)).next();

        verify(mockStreamProcessor, times(1)).onEvent(mockSourceEvent);
        verify(mockEventProcessor, times(1)).processEvent();
        verify(mockEventProcessor, times(1)).updateState();
        verify(mockStreamProcessor, times(1)).afterEvent();
        verify(mockStreamProcessor, times(1)).onOpen(any(StreamProcessorContext.class));
    }

    @Test
    public void shouldIgnoreEventsBeforeSnapshotOnRecoveryIfSourceEqualsTargetStream() throws Exception
    {
        when(mockSourceLogStream.getTopicName()).thenReturn(SOURCE_LOG_STREAM_TOPIC_NAME);
        when(mockTargetLogStream.getPartitionId()).thenReturn(SOURCE_LOG_STREAM_PARTITION_ID);

        when(mockSnapshotStorage.getLastSnapshot(STREAM_PROCESSOR_NAME)).thenReturn(mockReadableSnapshot);
        when(mockReadableSnapshot.getPosition()).thenReturn(5L);

        when(mockTargetLogStreamReader.seek(5L)).thenReturn(true);
        when(mockTargetLogStreamReader.hasNext()).thenReturn(true, true, true, false);
        when(mockTargetLogStreamReader.next()).thenReturn(mockTargetEvent);

        when(mockTargetEvent.getProducerId()).thenReturn(STREAM_PROCESSOR_ID);
        when(mockTargetEvent.getSourceEventPosition()).thenReturn(4L, 6L);

        when(mockSourceEvent.getPosition()).thenReturn(6L);
        mockSourceLogStreamReader.addEvent(mockSourceEvent);

        open();
        // -> recovery - ignore first event
        controller.doWork();
        // -> recovery - re-process event
        controller.doWork();
        // -> recovery - no more events
        controller.doWork();

        assertThat(controller.isOpen()).isTrue();

        verify(mockTargetLogStreamReader, times(4)).hasNext();
        verify(mockTargetLogStreamReader, times(3)).next();

        verify(mockStreamProcessor, times(1)).onEvent(mockSourceEvent);
        verify(mockEventProcessor, times(1)).processEvent();
        verify(mockEventProcessor, times(1)).updateState();
        verify(mockStreamProcessor, times(1)).afterEvent();
        verify(mockStreamProcessor, times(1)).onOpen(any(StreamProcessorContext.class));
    }

    @Test
    public void shouldSkipEventOnRecoveryIfFilterReject() throws Exception
    {
        reprocessingEventFilter.doFilter = false;

        when(mockTargetLogStreamReader.hasNext()).thenReturn(true, false);
        when(mockTargetLogStreamReader.next()).thenReturn(mockTargetEvent);

        when(mockTargetEvent.getProducerId()).thenReturn(STREAM_PROCESSOR_ID);
        when(mockTargetEvent.getSourceEventPosition()).thenReturn(3L);

        when(mockSourceEvent.getPosition()).thenReturn(3L);
        mockSourceLogStreamReader.addEvent(mockSourceEvent);

        open();
        // -> recovery - re-process event
        controller.doWork();
        // -> recovery - no more events
        controller.doWork();

        assertThat(controller.isOpen()).isTrue();

        verify(mockTargetLogStreamReader, times(2)).hasNext();
        verify(mockTargetLogStreamReader, times(1)).next();

        verify(mockStreamProcessor, never()).onEvent(mockSourceEvent);
        verify(mockStreamProcessor, times(1)).onOpen(any(StreamProcessorContext.class));
    }

    @Test
    public void shouldReprocessAllEventsOnRecoveryIfFilterIsNull() throws Exception
    {
        controller = builder.reprocessingEventFilter(null).build();

        when(mockTargetLogStreamReader.hasNext()).thenReturn(true, false);
        when(mockTargetLogStreamReader.next()).thenReturn(mockTargetEvent);

        when(mockTargetEvent.getProducerId()).thenReturn(STREAM_PROCESSOR_ID);
        when(mockTargetEvent.getSourceEventPosition()).thenReturn(3L);

        when(mockSourceEvent.getPosition()).thenReturn(3L);
        mockSourceLogStreamReader.addEvent(mockSourceEvent);

        open();
        // -> recovery - re-process event
        controller.doWork();
        // -> recovery - no more events
        controller.doWork();

        assertThat(controller.isOpen()).isTrue();

        verify(mockTargetLogStreamReader, times(2)).hasNext();
        verify(mockTargetLogStreamReader, times(1)).next();

        verify(mockStreamProcessor).onEvent(mockSourceEvent);
        verify(mockEventProcessor).processEvent();
        verify(mockStreamProcessor).onOpen(any(StreamProcessorContext.class));
    }

    @Test
    public void shouldFailToRecoverFromSnapshot() throws Exception
    {
        when(mockSnapshotStorage.getLastSnapshot(STREAM_PROCESSOR_NAME)).thenReturn(mockReadableSnapshot);

        doThrow(new RuntimeException("expected excetion")).when(mockReadableSnapshot).validateAndClose();

        final CompletableFuture<Void> future = controller.openAsync();
        // -> opening
        controller.doWork();
        // -> recover
        controller.doWork();

        assertThat(controller.isFailed()).isTrue();

        assertThat(future).isCompletedExceptionally();
    }

    @Test
    public void shouldFailToRecoverIfSouceEventNotFound() throws Exception
    {
        when(mockTargetLogStreamReader.hasNext()).thenReturn(true, false);
        when(mockTargetLogStreamReader.next()).thenReturn(mockTargetEvent);

        when(mockTargetEvent.getProducerId()).thenReturn(STREAM_PROCESSOR_ID);
        when(mockTargetEvent.getSourceEventPosition()).thenReturn(3L);

        open();

        assertThat(controller.isFailed()).isTrue();
    }

    @Test
    public void shouldFailToCreateSnapshot() throws Exception
    {
        mockSourceLogStreamReader.addEvent(mockSourceEvent);

        when(mockEventProcessor.executeSideEffects()).thenReturn(true);
        when(mockEventProcessor.writeEvent(mockLogStreamWriter)).thenReturn(1L);

        when(mockSnapshotPolicy.apply(anyLong())).thenReturn(true);
        when(mockTargetLogStream.getCurrentAppenderPosition()).thenReturn(1L);

        doThrow(new RuntimeException("expected exception")).when(mockSnapshotWriter).commit();

        open();

        // -> open
        controller.doWork();
        // -> processing
        controller.doWork();
        // -> snapshotting
        controller.doWork();

        // just continue if fails to create the snapshot
        assertThat(controller.isOpen()).isTrue();

        verify(mockSnapshotWriter).abort();
    }

    /**
     * <p>This behavior is actually important for certain error cases in the broker
     * where a stream processor can throw an exception and stream processing should
     * not continue or be recoverable unless the controller is restarted (cf https://github.com/camunda-tngp/camunda-tngp/issues/109).
     *
     * <p>If you extend/change the behavior, please make sure the current behavior is maintained
     */
    @Test
    public void shouldFailToProcessEvent()
    {
        mockSourceLogStreamReader.addEvent(mockSourceEvent);

        doThrow(new RuntimeException("expected exception")).when(mockEventProcessor).executeSideEffects();

        open();

        // -> open
        controller.doWork();
        // -> processing
        controller.doWork();

        assertThat(controller.isFailed()).isTrue();

        // verify that it can't be recovered
        targetLogStreamFailureListener.onFailed(2L);
        targetLogStreamFailureListener.onRecovered();

        controller.doWork();

        assertThat(controller.isFailed()).isTrue();
    }

    @Test
    public void shouldFailToExecuteCommands() throws Exception
    {
        streamProcessorCmdQueue.runAsync(() ->
        {
            throw new RuntimeException("expected failure");
        });

        open();

        controller.doWork();

        assertThat(controller.isFailed()).isTrue();
    }

    @Test
    public void shouldProcessEventsOnAcceptingFilter()
    {
        // given
        eventFilter.doFilter = true;

        mockSourceLogStreamReader.addEvent(mockSourceEvent);
        open();

        // when
        // -> open
        controller.doWork();

        // then
        // next state transition (either processing or open)
        controller.doWork();

        verify(mockStreamProcessor).onEvent(mockSourceEvent);
    }

    @Test
    public void shouldSkipEventsOnRejectingFilter()
    {
        // given
        eventFilter.doFilter = false;

        mockSourceLogStreamReader.addEvent(mockSourceEvent);
        open();

        // when
        // -> open
        controller.doWork();

        // then
        // next state transition (either processing or open)
        controller.doWork();

        verify(mockStreamProcessor, never()).onEvent(any());
    }

    @Test
    public void shouldProcessAllEventsOnNullFilter()
    {
        // given
        controller = builder.eventFilter(null).build();

        mockSourceLogStreamReader.addEvent(mockSourceEvent);
        open();

        // when
        // -> open
        controller.doWork();

        // then
        // next state transition (either processing or open)
        controller.doWork();

        verify(mockStreamProcessor).onEvent(mockSourceEvent);
    }

    @Test
    public void shouldWriteSnapshotOnClose() throws Exception
    {
        mockSourceLogStreamReader.addEvent(mockSourceEvent);

        when(mockEventProcessor.executeSideEffects()).thenReturn(true);
        when(mockEventProcessor.writeEvent(mockLogStreamWriter)).thenReturn(2L);

        when(mockSnapshotPolicy.apply(anyLong())).thenReturn(false);
        when(mockTargetLogStream.getCurrentAppenderPosition()).thenReturn(3L);

        when(mockSnapshotPositionProvider.getSnapshotPosition(any(), anyLong())).thenReturn(2L);

        open();

        // -> open
        controller.doWork();
        // -> processing
        controller.doWork();

        // when
        controller.closeAsync();

        // then
        // closing snapshotting
        controller.doWork();

        verify(mockSnapshotStorage).createSnapshot(STREAM_PROCESSOR_NAME, 2L);
        verify(mockSnapshotWriter).writeSnapshot(mockStateResource);
        verify(mockSnapshotWriter).commit();
    }

    @Test
    public void shouldCancelSnapshottingOnCloseInCaseOfLogFailure() throws Exception
    {

        mockSourceLogStreamReader.addEvent(mockSourceEvent);

        when(mockEventProcessor.executeSideEffects()).thenReturn(true);
        when(mockEventProcessor.writeEvent(mockLogStreamWriter)).thenReturn(2L);

        when(mockSnapshotPolicy.apply(anyLong())).thenReturn(false);
        when(mockTargetLogStream.getCurrentAppenderPosition()).thenReturn(1L);

        open();

        // -> open
        controller.doWork();
        // -> processing
        controller.doWork();

        final CompletableFuture<Void> closeFuture = controller.closeAsync();

        // -> closing snapshotting
        controller.doWork(); // does not write snapshot as appender hasn't caught up yet

        // when
        targetLogStreamFailureListener.onFailed(2L);

        // then
        // -> closing
        controller.doWork();

        // -> closed
        controller.doWork();

        assertThat(controller.isClosed());
        assertThat(closeFuture).isCompleted();
        verify(mockSnapshotWriter, never()).writeSnapshot(mockStateResource);
    }

    @Test
    public void shouldWriteSnapshotOnCloseIfNewEventsWrittenSinceLastSnapshot() throws Exception
    {
        mockSourceLogStreamReader.addEvent(mockSourceEvent);
        final LoggedEvent secondEvent = mock(LoggedEvent.class);
        when(secondEvent.getPosition()).thenReturn(1L);
        mockSourceLogStreamReader.addEvent(secondEvent);

        when(mockEventProcessor.executeSideEffects()).thenReturn(true);
        when(mockEventProcessor.writeEvent(mockLogStreamWriter)).thenReturn(2L, 3L);

        when(mockTargetLogStream.getCurrentAppenderPosition()).thenReturn(2L);
        when(mockSnapshotPolicy.apply(anyLong())).thenReturn(true);

        open();

        // -> open
        controller.doWork();
        // -> processing
        controller.doWork();
        // -> snapshotting
        controller.doWork();
        // -> open
        controller.doWork();
        // -> processing
        controller.doWork();

        when(mockTargetLogStream.getCurrentAppenderPosition()).thenReturn(3L);
        when(mockSnapshotPositionProvider.getSnapshotPosition(any(), anyLong())).thenReturn(3L);

        // when
        controller.closeAsync();

        // then
        // -> closing snapshotting
        controller.doWork();
        // -> closing
        controller.doWork();
        // -> closed
        controller.doWork();

        verify(mockSnapshotWriter, times(2)).writeSnapshot(mockStateResource);
        assertThat(controller.isClosed());
    }

    @Test
    public void shouldSkipSnapshotOnCloseIfNoNewEventsWrittenSinceLastSnapshot() throws Exception
    {
        mockSourceLogStreamReader.addEvent(mockSourceEvent);

        when(mockEventProcessor.executeSideEffects()).thenReturn(true);
        when(mockEventProcessor.writeEvent(mockLogStreamWriter)).thenReturn(2L);

        when(mockTargetLogStream.getCurrentAppenderPosition()).thenReturn(3L);
        when(mockSnapshotPolicy.apply(anyLong())).thenReturn(true);

        open();

        // -> open
        controller.doWork();
        // -> processing
        controller.doWork();
        // -> snapshotting
        controller.doWork();

        // when
        controller.closeAsync();

        // then
        // -> closing snapshotting
        controller.doWork();
        // -> closing
        controller.doWork();
        // -> closed
        controller.doWork();

        verify(mockSnapshotWriter, times(1)).writeSnapshot(mockStateResource);
        assertThat(controller.isClosed());
    }

    @Test
    public void shouldSkipReprocessingIfProcessorIsReadOnly()
    {
        // given
        controller = builder.readOnly(true).build();

        when(mockTargetLogStreamReader.hasNext()).thenReturn(true, false);
        when(mockTargetLogStreamReader.next()).thenReturn(mockSourceEvent);

        when(mockSourceEvent.getPosition()).thenReturn(3L);
        mockSourceLogStreamReader.addEvent(mockSourceEvent);

        open();

        // when we spin a couple of times
        final int numWaitOperations = 10;
        for (int i = 0; i < numWaitOperations; i++)
        {
            controller.doWork();
        }

        // then the target stream reader was not used for reading events during reprocessing
        verify(mockTargetLogStreamReader, never()).next();

    }

    protected void open()
    {
        controller.openAsync();
        // -> opening
        controller.doWork();
        // -> recovery
        controller.doWork();
        // -> re-processing
        controller.doWork();
    }

    protected LoggedEvent eventAt(long position)
    {
        final LoggedEvent event = mock(LoggedEvent.class);
        when(event.getPosition()).thenReturn(position);
        return event;
    }

    protected static class ControllableEventFilter implements EventFilter
    {

        protected boolean doFilter = true;

        @Override
        public boolean applies(LoggedEvent event)
        {
            return doFilter;
        }

    }

}
