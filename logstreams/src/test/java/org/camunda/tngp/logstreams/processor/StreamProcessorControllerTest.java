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
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;

import org.agrona.concurrent.Agent;
import org.agrona.concurrent.ManyToOneConcurrentArrayQueue;
import org.camunda.tngp.logstreams.log.LogStream;
import org.camunda.tngp.logstreams.log.LogStreamFailureListener;
import org.camunda.tngp.logstreams.log.LogStreamReader;
import org.camunda.tngp.logstreams.log.LogStreamWriter;
import org.camunda.tngp.logstreams.log.LoggedEvent;
import org.camunda.tngp.logstreams.spi.ReadableSnapshot;
import org.camunda.tngp.logstreams.spi.SnapshotPolicy;
import org.camunda.tngp.logstreams.spi.SnapshotStorage;
import org.camunda.tngp.logstreams.spi.SnapshotSupport;
import org.camunda.tngp.logstreams.spi.SnapshotWriter;
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

    private static final int SOURCE_LOG_STREAM_ID = 1;
    private static final int TARGET_LOG_STREAM_ID = 2;

    private StreamProcessorController controller;

    @Mock
    private StreamProcessor mockStreamProcessor;

    @Mock
    private ManyToOneConcurrentArrayQueue<StreamProcessorCommand> mockStreamProcessorCmdQueue;

    @Mock
    private EventProcessor mockEventProcessor;

    @Mock
    private AgentRunnerService mockAgentRunnerService;

    @Mock
    private LogStream mockSourceLogStream;

    @Mock
    private LogStream mockTargetLogStream;

    @Mock
    private LogStreamReader mockSourceLogStreamReader;

    @Mock
    private LogStreamReader mockTargetLogStreamReader;

    @Mock
    private LogStreamWriter mockLogStreamWriter;

    @Mock
    private SnapshotPolicy mockSnapshotPolicy;

    @Mock
    private SnapshotStorage mockSnapshotStorage;

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

    @Before
    public void init() throws Exception
    {
        MockitoAnnotations.initMocks(this);

        final StreamProcessorContext context = new StreamProcessorContext();
        context.setId(STREAM_PROCESSOR_ID);
        context.setName(STREAM_PROCESSOR_NAME);
        context.setAgentRunnerService(mockAgentRunnerService);
        context.setStreamProcessor(mockStreamProcessor);
        context.setSourceStream(mockSourceLogStream);
        context.setTargetStream(mockTargetLogStream);
        context.setSourceLogStreamReader(mockSourceLogStreamReader);
        context.setTargetLogStreamReader(mockTargetLogStreamReader);
        context.setLogStreamWriter(mockLogStreamWriter);
        context.setSnapshotPolicy(mockSnapshotPolicy);
        context.setSnapshotStorage(mockSnapshotStorage);
        context.setStreamProcessorCmdQueue(mockStreamProcessorCmdQueue);

        when(mockStreamProcessor.onEvent(any(LoggedEvent.class))).thenReturn(mockEventProcessor);

        when(mockStreamProcessor.getStateResource()).thenReturn(mockStateResource);
        when(mockSnapshotStorage.createSnapshot(anyString(), anyLong())).thenReturn(mockSnapshotWriter);

        when(mockSourceLogStream.getId()).thenReturn(SOURCE_LOG_STREAM_ID);
        when(mockTargetLogStream.getId()).thenReturn(TARGET_LOG_STREAM_ID);

        when(mockLogStreamWriter.producerId(anyInt())).thenReturn(mockLogStreamWriter);
        when(mockLogStreamWriter.sourceEvent(anyInt(), anyLong())).thenReturn(mockLogStreamWriter);

        controller = new StreamProcessorController(context);

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

        verify(mockSourceLogStreamReader).wrap(mockSourceLogStream);
        verify(mockTargetLogStreamReader).wrap(mockTargetLogStream);
        verify(mockLogStreamWriter).wrap(mockTargetLogStream);
        verify(mockStreamProcessor).onOpen(any(StreamProcessorContext.class));
    }

    @Test
    public void shouldNotOpenIfNotClosed()
    {
        open();

        assertThat(controller.isOpen()).isTrue();

        // try to open again
        final CompletableFuture<Void> future = controller.openAsync();

        controller.doWork();

        assertThat(future).isCompletedExceptionally();
        assertThat(controller.isOpen()).isTrue();

        verify(mockAgentRunnerService, times(1)).run(any(Agent.class));

        verify(mockSourceLogStreamReader, times(1)).wrap(mockSourceLogStream);
        verify(mockLogStreamWriter, times(1)).wrap(mockTargetLogStream);
        verify(mockStreamProcessor, times(1)).onOpen(any(StreamProcessorContext.class));
    }

    @Test
    public void shouldCloseWhilePollEvents()
    {
        open();

        assertThat(controller.isClosed()).isFalse();

        final CompletableFuture<Void> future = controller.closeAsync();

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
        when(mockSourceLogStreamReader.hasNext()).thenReturn(true);
        when(mockSourceLogStreamReader.next()).thenReturn(mockSourceEvent);

        when(mockEventProcessor.executeSideEffects()).thenReturn(false);

        open();

        // -> open
        controller.doWork();
        // -> processing
        controller.doWork();

        assertThat(controller.isOpen()).isTrue();

        final CompletableFuture<Void> future = controller.closeAsync();

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
        when(mockSourceLogStreamReader.hasNext()).thenReturn(true);
        when(mockSourceLogStreamReader.next()).thenReturn(mockSourceEvent);

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
        when(mockSourceLogStreamReader.hasNext()).thenReturn(false, true);
        when(mockSourceLogStreamReader.next()).thenReturn(mockSourceEvent);

        open();

        // -> open
        controller.doWork();
        // -> open
        controller.doWork();

        verify(mockSourceLogStreamReader, times(2)).hasNext();
        verify(mockSourceLogStreamReader, times(1)).next();
    }

    @Test
    public void shouldDrainStreamProcessorCmdQueueWhenOpen()
    {
        open();

        // -> open
        controller.doWork();

        verify(mockStreamProcessorCmdQueue).drain(any());
    }

    @Test
    public void shouldProcessPolledEvent()
    {
        when(mockSourceLogStreamReader.hasNext()).thenReturn(true);
        when(mockSourceLogStreamReader.next()).thenReturn(mockSourceEvent);

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
        when(mockStreamProcessor.isSuspended()).thenReturn(true);

        open();

        // -> open
        controller.doWork();

        verify(mockStreamProcessorCmdQueue).drain(any());

        verify(mockSourceLogStreamReader, never()).hasNext();
    }

    @Test
    public void shouldSkipProcessingEventIfNoProcessorIsAvailable()
    {
        when(mockSourceLogStreamReader.hasNext()).thenReturn(true);
        when(mockSourceLogStreamReader.next()).thenReturn(mockSourceEvent);

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
        when(mockSourceLogStreamReader.hasNext()).thenReturn(true);
        when(mockSourceLogStreamReader.next()).thenReturn(mockSourceEvent);

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
        when(mockSourceLogStreamReader.hasNext()).thenReturn(true);
        when(mockSourceLogStreamReader.next()).thenReturn(mockSourceEvent);

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
        when(mockSourceLogStreamReader.hasNext()).thenReturn(true);
        when(mockSourceLogStreamReader.next()).thenReturn(mockSourceEvent);

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
        when(mockSourceLogStreamReader.hasNext()).thenReturn(true);
        when(mockSourceLogStreamReader.next()).thenReturn(mockSourceEvent);

        when(mockEventProcessor.executeSideEffects()).thenReturn(true);
        when(mockEventProcessor.writeEvent(mockLogStreamWriter)).thenReturn(1L);

        when(mockSourceLogStream.getId()).thenReturn(3);
        when(mockSourceEvent.getPosition()).thenReturn(4L);

        open();

        // -> open
        controller.doWork();
        // -> processing
        controller.doWork();

        verify(mockLogStreamWriter).sourceEvent(3, 4L);
    }

    @Test
    public void shouldCreateSnapshot() throws Exception
    {
        when(mockSourceLogStreamReader.hasNext()).thenReturn(true);
        when(mockSourceLogStreamReader.next()).thenReturn(mockSourceEvent);

        when(mockEventProcessor.executeSideEffects()).thenReturn(true);
        when(mockEventProcessor.writeEvent(mockLogStreamWriter)).thenReturn(2L);

        when(mockSnapshotPolicy.apply(anyLong())).thenReturn(true);
        when(mockTargetLogStream.getCurrentAppenderPosition()).thenReturn(2L);

        open();

        // -> open
        controller.doWork();
        // -> processing
        controller.doWork();
        // -> snapshotting
        controller.doWork();

        assertThat(controller.isOpen()).isTrue();

        verify(mockSnapshotStorage).createSnapshot(STREAM_PROCESSOR_NAME, 2L);
        verify(mockSnapshotWriter).writeSnapshot(mockStateResource);
        verify(mockSnapshotWriter).commit();
    }

    @Test
    public void shouldCreateSnapshotIfSourceEqualsTargetStream() throws Exception
    {
        when(mockTargetLogStream.getId()).thenReturn(SOURCE_LOG_STREAM_ID);

        when(mockSourceLogStreamReader.hasNext()).thenReturn(true);
        when(mockSourceLogStreamReader.next()).thenReturn(mockSourceEvent);

        when(mockSourceEvent.getPosition()).thenReturn(1L);

        when(mockEventProcessor.executeSideEffects()).thenReturn(true);
        when(mockEventProcessor.writeEvent(mockLogStreamWriter)).thenReturn(2L);

        when(mockSnapshotPolicy.apply(anyLong())).thenReturn(true);
        when(mockTargetLogStream.getCurrentAppenderPosition()).thenReturn(2L);

        open();

        // -> open
        controller.doWork();
        // -> processing
        controller.doWork();
        // -> snapshotting
        controller.doWork();

        assertThat(controller.isOpen()).isTrue();

        verify(mockSnapshotStorage).createSnapshot(STREAM_PROCESSOR_NAME, 1L);
        verify(mockSnapshotWriter).writeSnapshot(mockStateResource);
        verify(mockSnapshotWriter).commit();
    }

    @Test
    public void shouldNotCreateSnapshotIfPolicyNotApplied() throws Exception
    {
        when(mockSourceLogStreamReader.hasNext()).thenReturn(true);
        when(mockSourceLogStreamReader.next()).thenReturn(mockSourceEvent);

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

        verify(mockSnapshotStorage, never()).createSnapshot(STREAM_PROCESSOR_NAME, 1L);
    }

    @Test
    public void shouldCreateSnapshotWhenLogAppenderWroteNewlyCreatedEvent() throws Exception
    {
        when(mockSourceLogStreamReader.hasNext()).thenReturn(true);
        when(mockSourceLogStreamReader.next()).thenReturn(mockSourceEvent);

        when(mockEventProcessor.executeSideEffects()).thenReturn(true);
        when(mockEventProcessor.writeEvent(mockLogStreamWriter)).thenReturn(2L);

        when(mockSnapshotPolicy.apply(anyLong())).thenReturn(true);
        when(mockTargetLogStream.getCurrentAppenderPosition()).thenReturn(1L, 2L);

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

        verify(mockSourceLogStreamReader, times(1)).hasNext();
        verify(mockTargetLogStream, times(2)).getCurrentAppenderPosition();

        verify(mockSnapshotStorage, times(1)).createSnapshot(STREAM_PROCESSOR_NAME, 2L);
        verify(mockSnapshotWriter, times(1)).writeSnapshot(mockStateResource);
        verify(mockSnapshotWriter, times(1)).commit();
    }

    @Test
    public void shouldFailLogStreamWhileProcessing() throws Exception
    {
        when(mockSourceLogStreamReader.hasNext()).thenReturn(true);
        when(mockSourceLogStreamReader.next()).thenReturn(mockSourceEvent);

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
        when(mockSourceLogStreamReader.hasNext()).thenReturn(true);
        when(mockSourceLogStreamReader.next()).thenReturn(mockSourceEvent);

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

        verify(mockSourceLogStreamReader, times(1)).hasNext();
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

        verify(mockSourceLogStreamReader, times(1)).hasNext();
    }

    @Test
    public void shouldRecoverAfterFailLogStream() throws Exception
    {
        when(mockSourceLogStreamReader.hasNext()).thenReturn(true);
        when(mockSourceLogStreamReader.next()).thenReturn(mockSourceEvent);

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

        verify(mockSourceLogStreamReader, times(1)).hasNext();
        verify(mockSourceLogStreamReader, never()).seek(anyLong());

        verify(mockSnapshotStorage, times(2)).getLastSnapshot(STREAM_PROCESSOR_NAME);
        verify(mockStreamProcessor, times(2)).onOpen(any(StreamProcessorContext.class));

        verify(mockStateResource, times(2)).reset();
    }

    @Test
    public void shouldNotRecoverIfFailedEventPositionIsAfterWrittenEventPosition() throws Exception
    {
        when(mockSourceLogStreamReader.hasNext()).thenReturn(true);
        when(mockSourceLogStreamReader.next()).thenReturn(mockSourceEvent);

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

        verify(mockSourceLogStreamReader, times(2)).hasNext();
        verify(mockSnapshotStorage, times(1)).getLastSnapshot(STREAM_PROCESSOR_NAME);
        verify(mockStreamProcessor, times(1)).onOpen(any(StreamProcessorContext.class));

        verify(mockStateResource, times(1)).reset();
    }

    @Test
    public void shouldRegisterFailureListener() throws Exception
    {
        open();

        controller.closeAsync();
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

        open();

        assertThat(controller.isOpen()).isTrue();

        verify(mockSnapshotStorage).getLastSnapshot(STREAM_PROCESSOR_NAME);

        verify(mockReadableSnapshot).recoverFromSnapshot(mockStateResource);

        verify(mockTargetLogStreamReader).seek(5L);
        verify(mockTargetLogStreamReader, times(2)).hasNext();
        verify(mockTargetLogStreamReader, times(1)).next();

        verify(mockSourceLogStreamReader).seek(4L);
    }

    @Test
    public void shouldRecoverFromSnapshotIfSourceEqualsTargetStream() throws Exception
    {
        when(mockTargetLogStream.getId()).thenReturn(SOURCE_LOG_STREAM_ID);

        when(mockSnapshotStorage.getLastSnapshot(STREAM_PROCESSOR_NAME)).thenReturn(mockReadableSnapshot);
        when(mockReadableSnapshot.getPosition()).thenReturn(5L);

        when(mockTargetLogStreamReader.seek(5L)).thenReturn(true);
        when(mockTargetLogStreamReader.hasNext()).thenReturn(true, false);
        when(mockTargetLogStreamReader.next()).thenReturn(mockTargetEvent);

        when(mockTargetEvent.getSourceEventPosition()).thenReturn(3L);

        open();

        assertThat(controller.isOpen()).isTrue();

        verify(mockSnapshotStorage).getLastSnapshot(STREAM_PROCESSOR_NAME);

        verify(mockReadableSnapshot).recoverFromSnapshot(mockStateResource);

        verify(mockTargetLogStreamReader).seek(5L);
        verify(mockTargetLogStreamReader, times(2)).hasNext();
        verify(mockTargetLogStreamReader, times(1)).next();

        verify(mockSourceLogStreamReader).seek(6L);
    }

    @Test
    public void shouldReprocessEventsOnRecovery() throws Exception
    {
        when(mockTargetLogStreamReader.hasNext()).thenReturn(true, false);
        when(mockTargetLogStreamReader.next()).thenReturn(mockTargetEvent);

        when(mockTargetEvent.getProducerId()).thenReturn(STREAM_PROCESSOR_ID);
        when(mockTargetEvent.getSourceEventPosition()).thenReturn(3L);

        when(mockSourceLogStreamReader.hasNext()).thenReturn(true);
        when(mockSourceLogStreamReader.next()).thenReturn(mockSourceEvent);

        when(mockSourceEvent.getPosition()).thenReturn(3L);

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

        when(mockSourceLogStreamReader.hasNext()).thenReturn(true);
        when(mockSourceLogStreamReader.next()).thenReturn(mockSourceEvent);

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
        when(mockTargetLogStream.getId()).thenReturn(SOURCE_LOG_STREAM_ID);

        when(mockSnapshotStorage.getLastSnapshot(STREAM_PROCESSOR_NAME)).thenReturn(mockReadableSnapshot);
        when(mockReadableSnapshot.getPosition()).thenReturn(5L);

        when(mockTargetLogStreamReader.seek(5L)).thenReturn(true);
        when(mockTargetLogStreamReader.hasNext()).thenReturn(true, true, true, false);
        when(mockTargetLogStreamReader.next()).thenReturn(mockTargetEvent);

        when(mockTargetEvent.getProducerId()).thenReturn(STREAM_PROCESSOR_ID);
        when(mockTargetEvent.getSourceEventPosition()).thenReturn(4L, 6L);

        when(mockSourceLogStreamReader.hasNext()).thenReturn(true);
        when(mockSourceLogStreamReader.next()).thenReturn(mockSourceEvent);

        when(mockSourceEvent.getPosition()).thenReturn(6L);

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

        when(mockSourceLogStreamReader.seek(3L)).thenReturn(false);
        when(mockSourceLogStreamReader.hasNext()).thenReturn(false);

        open();

        assertThat(controller.isFailed()).isTrue();
    }


    @Test
    public void shouldFailToCreateSnapshot() throws Exception
    {
        when(mockSourceLogStreamReader.hasNext()).thenReturn(true);
        when(mockSourceLogStreamReader.next()).thenReturn(mockSourceEvent);

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

    @Test
    public void shouldFailToProcessEvent() throws Exception
    {
        when(mockSourceLogStreamReader.hasNext()).thenReturn(true);
        when(mockSourceLogStreamReader.next()).thenReturn(mockSourceEvent);

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

}
