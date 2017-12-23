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
package io.zeebe.logstreams.processor;

import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LogStreamFailureListener;
import io.zeebe.logstreams.log.LogStreamWriter;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.logstreams.spi.*;
import io.zeebe.test.util.FluentMock;
import io.zeebe.util.DeferredCommandContext;
import io.zeebe.util.actor.ActorReference;
import io.zeebe.util.actor.ActorScheduler;
import org.agrona.DirectBuffer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class StreamProcessorControllerTest
{
    private static final String STREAM_PROCESSOR_NAME = "mock-processor";
    private static final int STREAM_PROCESSOR_ID = 1;

    private static final DirectBuffer SOURCE_LOG_STREAM_TOPIC_NAME = wrapString("source-topic");
    private static final int SOURCE_LOG_STREAM_PARTITION_ID = 1;

    private StreamProcessorController controller;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Mock
    private StreamProcessor mockStreamProcessor;

    @Mock
    private EventProcessor mockEventProcessor;

    @Mock
    private ActorScheduler mockTaskScheduler;

    @Mock
    private ActorReference mockActorRef;

    @Mock
    private LogStream mockLogStream;

    private MockLogStreamReader mockLogStreamReader;

    @FluentMock
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

        mockLogStreamReader = new MockLogStreamReader();
        builder = new TestStreamProcessorBuilder(STREAM_PROCESSOR_ID, STREAM_PROCESSOR_NAME, mockStreamProcessor)
                .actorScheduler(mockTaskScheduler)
                .logStream(mockLogStream)
                .logStreamReader(mockLogStreamReader)
                .logStreamWriter(mockLogStreamWriter)
                .snapshotPolicy(mockSnapshotPolicy)
                .snapshotStorage(mockSnapshotStorage)
                .streamProcessorCmdQueue(streamProcessorCmdQueue)
                .eventFilter(eventFilter)
                .reprocessingEventFilter(reprocessingEventFilter);

        when(mockTaskScheduler.schedule(any())).thenReturn(mockActorRef);

        when(mockStreamProcessor.onEvent(any(LoggedEvent.class))).thenReturn(mockEventProcessor);

        when(mockStreamProcessor.getStateResource()).thenReturn(mockStateResource);
        when(mockSnapshotStorage.createSnapshot(anyString(), anyLong())).thenReturn(mockSnapshotWriter);

        when(mockLogStream.getTopicName()).thenReturn(SOURCE_LOG_STREAM_TOPIC_NAME);
        when(mockLogStream.getPartitionId()).thenReturn(SOURCE_LOG_STREAM_PARTITION_ID);

        controller = builder.build();

        doAnswer(invocation ->
        {
            // this is invoked while opening
            targetLogStreamFailureListener = (LogStreamFailureListener) invocation.getArguments()[0];
            return null;
        }).when(mockLogStream).registerFailureListener(any(LogStreamFailureListener.class));
    }

    @Test
    public void shouldGetRoleName()
    {
        assertThat(controller.name()).isEqualTo(STREAM_PROCESSOR_NAME);
    }

    @Test
    public void shouldOpen()
    {
        // given
        assertThat(controller.isOpen()).isFalse();

        final CompletableFuture<Void> future = controller.openAsync();

        // when
        // -> opening
        controller.doWork();

        assertThat(future).isNotCompleted();
        assertThat(controller.isOpen()).isFalse();

        // -> recovery
        controller.doWork();
        // -> re-processing
        controller.doWork();

        // then
        assertThat(future).isCompleted();
        assertThat(controller.isOpen()).isTrue();

        verify(mockTaskScheduler).schedule(any(StreamProcessorController.class));

        assertThat(mockLogStreamReader.getMockingLog()).isEqualTo(mockLogStream);
        verify(mockLogStreamWriter).wrap(mockLogStream);
        verify(mockStreamProcessor).onOpen(any(StreamProcessorContext.class));
    }

    @Test
    public void shouldNotOpenIfNotClosed()
    {
        // given
        open();
        when(mockSourceEvent.getPosition()).thenReturn(0xFFL);
        mockLogStreamReader.addEvent(mockSourceEvent);
        mockLogStreamReader.seek(1L);

        assertThat(controller.isOpen()).isTrue();

        // try to open again
        final CompletableFuture<Void> future = controller.openAsync();

        controller.doWork();

        assertThat(future).isCompletedExceptionally();
        assertThat(controller.isOpen()).isTrue();

        verify(mockTaskScheduler, times(1)).schedule(any(StreamProcessorController.class));

        assertThat(mockLogStreamReader.getMockingLog()).isEqualTo(mockLogStream);
        assertThat(mockLogStreamReader.getPosition()).isEqualTo(0xFFL);
        verify(mockLogStreamWriter, times(1)).wrap(mockLogStream);
        verify(mockStreamProcessor, times(1)).onOpen(any(StreamProcessorContext.class));
    }

    @Test
    public void shouldCloseReader()
    {
        // given
        open();

        assertThat(controller.isClosed()).isFalse();

        // when
        final CompletableFuture<Void> future = controller.closeAsync();

        // -> closingSnapshotting
        controller.doWork();
        // -> closing
        controller.doWork();
        // -> closed
        controller.doWork();

        // then
        assertThat(future).isCompleted();
        assertThat(controller.isClosed()).isTrue();
        assertThat(controller.logStreamReader.isClosed());
        assertThat(controller.logStreamReader.isClosed());
    }

    @Test
    public void shouldReopenReader()
    {
        // given
        open();

        assertThat(controller.isClosed()).isFalse();

        // when
        final CompletableFuture<Void> future = controller.closeAsync();

        // -> closing Snapshotting
        controller.doWork();
        // -> closing
        controller.doWork();
        // -> closed
        controller.doWork();

        // then
        assertThat(future).isCompleted();
        assertThat(controller.isClosed()).isTrue();
        assertThat(mockLogStreamReader.isClosed()).isTrue();

        // when
        open();

        // then
        assertThat(mockLogStreamReader.isClosed()).isFalse();
    }

    @Test
    public void shouldCloseWhilePollEvents()
    {
        // given
        open();

        assertThat(controller.isClosed()).isFalse();

        // when
        final CompletableFuture<Void> future = controller.closeAsync();

        // -> closingSnapshotting
        controller.doWork();
        // -> closing
        controller.doWork();
        // -> closed
        controller.doWork();

        // then
        assertThat(future).isCompleted();
        assertThat(controller.isClosed()).isTrue();

        verify(mockStreamProcessor).onClose();

        verify(mockActorRef).close();
    }

    @Test
    public void shouldCloseWhileProcessing()
    {
        // given
        when(mockEventProcessor.executeSideEffects()).thenReturn(false);

        open();
        mockLogStreamReader.addEvent(mockSourceEvent);

        // when
        // -> open
        controller.doWork();
        // -> processing
        controller.doWork();

        // then
        assertThat(controller.isOpen()).isTrue();

        // when
        final CompletableFuture<Void> future = controller.closeAsync();

        // -> closingSnapshotting
        controller.doWork();
        // -> closing
        controller.doWork();
        // -> closed
        controller.doWork();

        // then
        assertThat(future).isCompleted();
        assertThat(controller.isClosed()).isTrue();
    }

    @Test
    public void shouldCloseWhileSnapshotting()
    {
        // given
        when(mockEventProcessor.executeSideEffects()).thenReturn(true);
        when(mockEventProcessor.writeEvent(mockLogStreamWriter)).thenReturn(2L);

        when(mockSnapshotPolicy.apply(anyLong())).thenReturn(true);
        when(mockLogStream.getCommitPosition()).thenReturn(3L);

        open();
        mockLogStreamReader.addEvent(mockSourceEvent);

        // when
        // -> open
        controller.doWork();
        // -> processing
        controller.doWork();
        // -> snapshotting
        controller.doWork();

        // then
        assertThat(controller.isOpen()).isTrue();

        // when
        final CompletableFuture<Void> future = controller.closeAsync();

        // -> closingSnapshotting
        controller.doWork();
        // -> closing
        controller.doWork();
        // -> closed
        controller.doWork();

        // then
        assertThat(future).isCompleted();
        assertThat(controller.isClosed()).isTrue();
    }

    @Test
    public void shouldCloseWhileSnapshottingAndWaitForLogAppender()
    {
        // given
        when(mockEventProcessor.executeSideEffects()).thenReturn(true);
        when(mockEventProcessor.writeEvent(mockLogStreamWriter)).thenReturn(2L);

        when(mockSnapshotPolicy.apply(anyLong())).thenReturn(true);
        when(mockLogStream.getCommitPosition()).thenReturn(1L);

        open();
        mockLogStreamReader.addEvent(mockSourceEvent);

        // when
        // -> open
        controller.doWork();
        // -> processing
        controller.doWork();
        // -> snapshotting
        controller.doWork();

        // then
        assertThat(controller.isOpen()).isTrue();

        final CompletableFuture<Void> future = controller.closeAsync();

        // -> closingSnapshotting
        controller.doWork();

        assertThat(controller.isClosing()).isTrue();

        // -> closing
        controller.doWork();

        assertThat(controller.isClosing()).isFalse();

        // -> closed
        controller.doWork();

        assertThat(future).isCompleted();
        assertThat(controller.isClosed()).isTrue();
    }

    @Test
    public void shouldNotCloseIfNotOpen()
    {
        // given
        assertThat(controller.isClosed()).isTrue();

        // when
        // try to close again
        final CompletableFuture<Void> future = controller.closeAsync();

        controller.doWork();

        // then
        assertThat(future).isCompletedExceptionally();
        assertThat(controller.isClosed()).isTrue();
    }

    @Test
    public void shouldPollEventsWhenOpen()
    {
        // given
        open();

        // when
        // -> open
        controller.doWork();

        // event becomes available
        mockLogStreamReader.addEvent(mockSourceEvent);

        // -> open
        controller.doWork();
        // -> process
        controller.doWork();

        // then
        verify(mockStreamProcessor).onEvent(mockSourceEvent);
    }

    @Test
    public void shouldDrainStreamProcessorCmdQueueWhenOpen()
    {
        // given
        final AtomicBoolean isExecuted = new AtomicBoolean(false);
        streamProcessorCmdQueue.runAsync(() -> isExecuted.set(true));
        open();

        // when
        // -> open
        controller.doWork();

        // then
        assertThat(isExecuted.get()).isTrue();
    }

    @Test
    public void shouldProcessPolledEvent()
    {
        // given
        when(mockEventProcessor.executeSideEffects()).thenReturn(true);
        when(mockEventProcessor.writeEvent(mockLogStreamWriter)).thenReturn(1L);

        open();
        mockLogStreamReader.addEvent(mockSourceEvent);

        // when
        // -> open
        controller.doWork();
        // -> processing
        controller.doWork();

        // then
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
        // given
        final AtomicBoolean isExecuted = new AtomicBoolean(false);
        streamProcessorCmdQueue.runAsync(() -> isExecuted.set(true));

        when(mockStreamProcessor.isSuspended()).thenReturn(true);

        open();

        // when
        // -> open
        controller.doWork();

        // then
        assertThat(isExecuted.get()).isTrue();
        assertThat(mockLogStreamReader.getHasNextInvocations()).isEqualTo(1);
    }

    @Test
    public void shouldSkipProcessingEventIfNoProcessorIsAvailable()
    {
        // given
        when(mockStreamProcessor.onEvent(mockSourceEvent)).thenReturn(null);
        open();
        mockLogStreamReader.addEvent(mockSourceEvent);

        // when
        // -> open
        controller.doWork();
        // -> processing
        controller.doWork();

        // then
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
        // given
        when(mockEventProcessor.executeSideEffects()).thenReturn(false, true);
        when(mockEventProcessor.writeEvent(mockLogStreamWriter)).thenReturn(1L);

        open();
        mockLogStreamReader.addEvent(mockSourceEvent);

        // when
        // -> open
        controller.doWork();
        // -> process (fail)
        controller.doWork();
        // -> retry process
        controller.doWork();

        // then
        verify(mockEventProcessor, times(1)).processEvent();
        verify(mockEventProcessor, times(2)).executeSideEffects();
        verify(mockEventProcessor, times(1)).writeEvent(mockLogStreamWriter);
        verify(mockEventProcessor, times(1)).updateState();
    }

    @Test
    public void shouldRetryWriteEventIfFail()
    {
        // given
        when(mockEventProcessor.executeSideEffects()).thenReturn(true);
        when(mockEventProcessor.writeEvent(mockLogStreamWriter)).thenReturn(-1L, 1L);

        open();
        mockLogStreamReader.addEvent(mockSourceEvent);

        // when
        // -> open
        controller.doWork();
        // -> process (fail)
        controller.doWork();
        // -> retry process
        controller.doWork();

        // then
        verify(mockEventProcessor, times(1)).processEvent();
        verify(mockEventProcessor, times(1)).executeSideEffects();
        verify(mockEventProcessor, times(2)).writeEvent(mockLogStreamWriter);
        verify(mockEventProcessor, times(1)).updateState();
    }

    @Test
    public void shouldAddProducerIdToWrittenEvent()
    {
        // given
        when(mockEventProcessor.executeSideEffects()).thenReturn(true);
        when(mockEventProcessor.writeEvent(mockLogStreamWriter)).thenReturn(1L);
        open();
        mockLogStreamReader.addEvent(mockSourceEvent);

        // when
        // -> open
        controller.doWork();
        // -> processing
        controller.doWork();

        // then
        verify(mockLogStreamWriter).producerId(STREAM_PROCESSOR_ID);
    }

    @Test
    public void shouldAddSourceEventToWrittenEvent()
    {
        // given
        when(mockEventProcessor.executeSideEffects()).thenReturn(true);
        when(mockEventProcessor.writeEvent(mockLogStreamWriter)).thenReturn(1L);
        when(mockLogStream.getTopicName()).thenReturn(SOURCE_LOG_STREAM_TOPIC_NAME);
        when(mockLogStream.getPartitionId()).thenReturn(SOURCE_LOG_STREAM_PARTITION_ID);
        when(mockSourceEvent.getPosition()).thenReturn(4L);

        open();
        mockLogStreamReader.addEvent(mockSourceEvent);

        // when
        // -> open
        controller.doWork();
        // -> processing
        controller.doWork();

        // then
        verify(mockLogStreamWriter).sourceEvent(SOURCE_LOG_STREAM_PARTITION_ID, 4L);
    }

    @Test
    public void shouldCreateSnapshotAtLastSuccessProcessedEventPosition() throws Exception
    {
        // given
        when(mockEventProcessor.executeSideEffects()).thenReturn(true);
        when(mockEventProcessor.writeEvent(mockLogStreamWriter)).thenReturn(2L);
        when(mockSnapshotPolicy.apply(anyLong())).thenReturn(true);
        when(mockLogStream.getCommitPosition()).thenReturn(2L);

        open();
        final LoggedEvent loggedEvent = eventAt(1L);
        mockLogStreamReader.addEvent(loggedEvent);

        // when
        // -> open
        controller.doWork();
        // -> processing
        controller.doWork();
        // -> snapshotting
        controller.doWork();

        // then
        assertThat(controller.isOpen()).isTrue();

        verify(mockSnapshotStorage).createSnapshot(STREAM_PROCESSOR_NAME, 1L);
        verify(mockSnapshotWriter).writeSnapshot(mockStateResource);
        verify(mockSnapshotWriter).commit();
    }

    @Test
    public void shouldNotCreateSnapshotIfPolicyNotApplied() throws Exception
    {
        // given
        when(mockEventProcessor.executeSideEffects()).thenReturn(true);
        when(mockEventProcessor.writeEvent(mockLogStreamWriter)).thenReturn(1L);
        when(mockSnapshotPolicy.apply(anyLong())).thenReturn(false);
        when(mockLogStream.getCurrentAppenderPosition()).thenReturn(1L);

        open();
        mockLogStreamReader.addEvent(mockSourceEvent);

        // when
        // -> open
        controller.doWork();
        // -> processing
        controller.doWork();
        // -> open
        controller.doWork();

        // then
        assertThat(controller.isOpen()).isTrue();

        verify(mockSnapshotStorage, never()).createSnapshot(anyString(), anyLong());
    }

    @Test
    public void shouldNotCreateSnapshotIfNoEventIsWritten() throws Exception
    {
        // given
        when(mockEventProcessor.executeSideEffects()).thenReturn(true);
        when(mockEventProcessor.writeEvent(mockLogStreamWriter)).thenReturn(0L);
        when(mockSnapshotPolicy.apply(anyLong())).thenReturn(true);
        when(mockLogStream.getCurrentAppenderPosition()).thenReturn(1L);

        open();
        mockLogStreamReader.addEvent(mockSourceEvent);

        // when
        // -> open
        controller.doWork();
        // -> processing
        controller.doWork();
        // -> open
        controller.doWork();

        // then
        assertThat(controller.isOpen()).isTrue();

        verify(mockSnapshotStorage, never()).createSnapshot(anyString(), anyLong());
    }

    @Test
    public void shouldCreateSnapshotWhenLogAppenderWroteNewlyCreatedEvent() throws Exception
    {
        // given
        when(mockEventProcessor.executeSideEffects()).thenReturn(true);
        when(mockEventProcessor.writeEvent(mockLogStreamWriter)).thenReturn(2L);
        when(mockSnapshotPolicy.apply(anyLong())).thenReturn(true);
        when(mockLogStream.getCommitPosition()).thenReturn(1L, 255L);

        open();
        final LoggedEvent loggedEvent = eventAt(255L);
        mockLogStreamReader.addEvent(loggedEvent);

        // when
        // -> open
        controller.doWork();
        // -> processing
        controller.doWork();
        // -> snapshotting
        controller.doWork();
        // -> stay in snapshotting state until log appender wrote the newly created event
        controller.doWork();

        //then
        assertThat(controller.isOpen()).isTrue();

        assertThat(mockLogStreamReader.getHasNextInvocations()).isEqualTo(2);
        verify(mockLogStream, times(2)).getCommitPosition();

        verify(mockSnapshotStorage, times(1)).createSnapshot(STREAM_PROCESSOR_NAME, 255L);
        verify(mockSnapshotWriter, times(1)).writeSnapshot(mockStateResource);
        verify(mockSnapshotWriter, times(1)).commit();
    }

    @Test
    public void shouldFailLogStreamWhileProcessing() throws Exception
    {
        // given
        when(mockEventProcessor.executeSideEffects()).thenReturn(true);
        when(mockEventProcessor.writeEvent(mockLogStreamWriter)).thenReturn(-1L);

        open();
        mockLogStreamReader.addEvent(mockSourceEvent);

        // when
        // -> open
        controller.doWork();
        // -> processing
        controller.doWork();

        targetLogStreamFailureListener.onFailed(2L);

        // -> failed
        controller.doWork();

        // then
        assertThat(controller.isFailed()).isTrue();

        verify(mockEventProcessor, never()).updateState();
    }

    @Test
    public void shouldClosingOnFailState() throws Exception
    {
        // given
        when(mockEventProcessor.executeSideEffects()).thenReturn(true);
        when(mockEventProcessor.writeEvent(mockLogStreamWriter)).thenReturn(-1L);

        open();
        mockLogStreamReader.addEvent(mockSourceEvent);

        // when
        // -> open
        controller.doWork();
        // -> processing
        controller.doWork();

        targetLogStreamFailureListener.onFailed(2L);

        // -> failed
        controller.doWork();

        // then
        assertThat(controller.isFailed()).isTrue();
        verify(mockEventProcessor, never()).updateState();

        // when
        controller.closeAsync();

        controller.doWork();

        // then
        // -> closing
        // -> closed
        assertThat(controller.isClosed()).isTrue();
        controller.doWork();
        assertThat(mockLogStreamReader.isClosed()).isTrue();
    }

    @Test
    public void shouldFailLogStreamWhileSnapshotting() throws Exception
    {
        // given
        when(mockEventProcessor.executeSideEffects()).thenReturn(true);
        when(mockEventProcessor.writeEvent(mockLogStreamWriter)).thenReturn(2L);

        when(mockSnapshotPolicy.apply(anyLong())).thenReturn(true);
        when(mockLogStream.getCurrentAppenderPosition()).thenReturn(1L);

        open();
        mockLogStreamReader.addEvent(mockSourceEvent);

        // when
        // -> open
        controller.doWork();
        // -> processing
        controller.doWork();
        // -> snapshotting
        controller.doWork();

        targetLogStreamFailureListener.onFailed(2L);

        // -> failed
        controller.doWork();

        // then
        assertThat(controller.isFailed()).isTrue();

        assertThat(mockLogStreamReader.getHasNextInvocations()).isEqualTo(2);
        verify(mockSnapshotStorage, never()).createSnapshot(STREAM_PROCESSOR_NAME, 2L);
    }

    @Test
    public void shouldFailLogStreamWhilePollEvents() throws Exception
    {
        // given
        open();

        // when
        // -> open
        controller.doWork();

        targetLogStreamFailureListener.onFailed(1L);

        // -> failed
        controller.doWork();

        // then
        assertThat(controller.isFailed()).isTrue();

        assertThat(mockLogStreamReader.getHasNextInvocations()).isEqualTo(2);
    }

    @Test
    public void shouldRecoverAfterFailLogStream() throws Exception
    {
        // given
        when(mockEventProcessor.executeSideEffects()).thenReturn(true);
        when(mockEventProcessor.writeEvent(mockLogStreamWriter)).thenReturn(2L);

        open();

        final LoggedEvent event = eventAt(2L);
        mockLogStreamReader.addEvent(event);

        // when
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
        assertThat(controller.isOnRecover());
        // -> re-process - no more events
        controller.doWork();

        // then
        assertThat(controller.isOpen()).isTrue();

        assertThat(mockLogStreamReader.hasNext()).isFalse();

        verify(mockSnapshotStorage, times(2)).getLastSnapshot(STREAM_PROCESSOR_NAME);
        verify(mockStreamProcessor, times(2)).onOpen(any(StreamProcessorContext.class));

        verify(mockStateResource, times(2)).reset();
    }

    @Test
    public void shouldNotRecoverIfFailedEventPositionIsAfterWrittenEventPosition() throws Exception
    {
        // given
        when(mockEventProcessor.executeSideEffects()).thenReturn(true);
        when(mockEventProcessor.writeEvent(mockLogStreamWriter)).thenReturn(2L);

        open();
        mockLogStreamReader.addEvent(mockSourceEvent);
        final LoggedEvent secondEvent = eventAt(1L);
        mockLogStreamReader.addEvent(secondEvent);

        // when
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

        // then
        assertThat(controller.isOpen()).isTrue();

        assertThat(mockLogStreamReader.getHasNextInvocations()).isEqualTo(3);
        assertThat(mockLogStreamReader.getPosition()).isEqualTo(1L);

        verify(mockSnapshotStorage, times(1)).getLastSnapshot(STREAM_PROCESSOR_NAME);
        verify(mockStreamProcessor, times(1)).onOpen(any(StreamProcessorContext.class));

        verify(mockStateResource, times(1)).reset();
    }

    @Test
    public void shouldRegisterFailureListener() throws Exception
    {
        // given
        open();

        // when
        controller.closeAsync();

        // -> closingSnapshotting
        controller.doWork();
        // -> closing
        controller.doWork();

        // then
        verify(mockLogStream, times(1)).registerFailureListener(targetLogStreamFailureListener);
        verify(mockLogStream, times(2)).removeFailureListener(targetLogStreamFailureListener);
    }

    @Test
    public void shouldRecoverFromSnapshot() throws Exception
    {
        // given
        when(mockSnapshotStorage.getLastSnapshot(STREAM_PROCESSOR_NAME)).thenReturn(mockReadableSnapshot);
        when(mockReadableSnapshot.getPosition()).thenReturn(5L);

        mockLogStreamReader.addEvent(eventAt(3L));
        when(mockSourceEvent.getSourceEventPosition()).thenReturn(3L);
        when(mockSourceEvent.getPosition()).thenReturn(5L);
        mockLogStreamReader.addEvent(mockSourceEvent);

        // when
        open();
        assertThat(controller.isOpen()).isTrue();

        // then
        verify(mockSnapshotStorage).getLastSnapshot(STREAM_PROCESSOR_NAME);

        verify(mockReadableSnapshot).recoverFromSnapshot(mockStateResource);

        assertThat(mockLogStreamReader.getHasNextInvocations()).isEqualTo(4);
        assertThat(mockLogStreamReader.getPosition()).isEqualTo(6L);
    }

    @Test
    public void shouldRecoverFromSnapshotWitMoreEventsAfterSnapshot() throws Exception
    {
        // given
        when(mockSnapshotStorage.getLastSnapshot(STREAM_PROCESSOR_NAME)).thenReturn(mockReadableSnapshot);
        when(mockReadableSnapshot.getPosition()).thenReturn(5L);

        mockLogStreamReader.addEvent(eventAt(3L));
        when(mockSourceEvent.getSourceEventPosition()).thenReturn(3L);
        when(mockSourceEvent.getPosition()).thenReturn(5L);
        mockLogStreamReader.addEvent(mockSourceEvent);

        final LoggedEvent event = eventAt(6L);
        when(event.getSourceEventPosition()).thenReturn(4L); // src pos is less then snapshot pos
        mockLogStreamReader.addEvent(event);
        mockLogStreamReader.addEvent(eventAt(7L));

        // when
        open();
        controller.doWork();
        assertThat(controller.isOpen()).isTrue();

        // then
        verify(mockSnapshotStorage).getLastSnapshot(STREAM_PROCESSOR_NAME);

        verify(mockReadableSnapshot).recoverFromSnapshot(mockStateResource);

        assertThat(mockLogStreamReader.getHasNextInvocations()).isEqualTo(6);
        assertThat(mockLogStreamReader.getPosition()).isEqualTo(6L);
    }

    @Test
    public void shouldRecoverAndReprocessFromSnapshot() throws Exception
    {
        // given
        when(mockSnapshotStorage.getLastSnapshot(STREAM_PROCESSOR_NAME)).thenReturn(mockReadableSnapshot);
        when(mockReadableSnapshot.getPosition()).thenReturn(5L);

        mockLogStreamReader.addEvent(eventAt(3L));

        when(mockSourceEvent.getSourceEventPosition()).thenReturn(3L);
        when(mockSourceEvent.getPosition()).thenReturn(5L);
        mockLogStreamReader.addEvent(mockSourceEvent);

        LoggedEvent loggedEvent = eventAt(6L);
        final LoggedEvent lastSourceEvent = loggedEvent;
        when(loggedEvent.getSourceEventPosition()).thenReturn(5L);
        mockLogStreamReader.addEvent(loggedEvent);

        loggedEvent = eventAt(7L);
        when(loggedEvent.getSourceEventPosition()).thenReturn(0L);
        mockLogStreamReader.addEvent(loggedEvent);

        loggedEvent = eventAt(8L);
        when(loggedEvent.getSourceEventPosition()).thenReturn(6L);
        mockLogStreamReader.addEvent(loggedEvent);

        // when
        open();

        // reprocess 1 event
        controller.doWork();

        assertThat(controller.isOpen()).isTrue();

        // then
        verify(mockSnapshotStorage).getLastSnapshot(STREAM_PROCESSOR_NAME);
        verify(mockReadableSnapshot).recoverFromSnapshot(mockStateResource);

        final InOrder inOrder = inOrder(mockStreamProcessor, mockEventProcessor);
        inOrder.verify(mockStreamProcessor).onOpen(any(StreamProcessorContext.class));
        inOrder.verify(mockStreamProcessor).onEvent(lastSourceEvent);
        inOrder.verify(mockEventProcessor).processEvent();
        inOrder.verify(mockEventProcessor).updateState();
        inOrder.verify(mockStreamProcessor).afterEvent();

        inOrder.verifyNoMoreInteractions();

        assertThat(mockLogStreamReader.getHasNextInvocations()).isEqualTo(7);
        assertThat(mockLogStreamReader.getPosition()).isEqualTo(6L);
    }

    @Test
    public void shouldRecoverReprocessFromSnapshotAndProcessAfter() throws Exception
    {
        // given
        when(mockEventProcessor.executeSideEffects()).thenReturn(true);

        when(mockSnapshotStorage.getLastSnapshot(STREAM_PROCESSOR_NAME)).thenReturn(mockReadableSnapshot);
        when(mockReadableSnapshot.getPosition()).thenReturn(5L);

        mockLogStreamReader.addEvent(eventAt(3L));

        when(mockSourceEvent.getSourceEventPosition()).thenReturn(3L);
        when(mockSourceEvent.getPosition()).thenReturn(5L);
        mockLogStreamReader.addEvent(mockSourceEvent);

        LoggedEvent loggedEvent = eventAt(6L);
        when(loggedEvent.getSourceEventPosition()).thenReturn(5L);
        mockLogStreamReader.addEvent(loggedEvent);

        loggedEvent = eventAt(7L);
        when(loggedEvent.getSourceEventPosition()).thenReturn(0L);
        mockLogStreamReader.addEvent(loggedEvent);

        loggedEvent = eventAt(8L);
        when(loggedEvent.getSourceEventPosition()).thenReturn(6L);
        mockLogStreamReader.addEvent(loggedEvent);

        // when
        open();

        // reprocess 1 event
        controller.doWork();

        // process 7
        controller.doWork();
        controller.doWork();

        // process 8
        controller.doWork();
        controller.doWork();

        assertThat(controller.isOpen()).isTrue();

        // then
        verify(mockSnapshotStorage).getLastSnapshot(STREAM_PROCESSOR_NAME);
        verify(mockReadableSnapshot).recoverFromSnapshot(mockStateResource);

        assertThat(mockLogStreamReader.getHasNextInvocations()).isEqualTo(9);
        assertThat(mockLogStreamReader.getPosition()).isEqualTo(8L);
    }


    @Test
    public void shouldRecoverWithoutReprocessFromSnapshotAndProcessAfter() throws Exception
    {
        // given
        when(mockEventProcessor.executeSideEffects()).thenReturn(true);

        when(mockSnapshotStorage.getLastSnapshot(STREAM_PROCESSOR_NAME)).thenReturn(mockReadableSnapshot);
        when(mockReadableSnapshot.getPosition()).thenReturn(5L);

        mockLogStreamReader.addEvent(eventAt(3L));

        when(mockSourceEvent.getSourceEventPosition()).thenReturn(3L);
        when(mockSourceEvent.getPosition()).thenReturn(5L);
        mockLogStreamReader.addEvent(mockSourceEvent);

        LoggedEvent loggedEvent = eventAt(6L);
        when(loggedEvent.getSourceEventPosition()).thenReturn(5L);
        mockLogStreamReader.addEvent(loggedEvent);

        loggedEvent = eventAt(7L);
        when(loggedEvent.getSourceEventPosition()).thenReturn(0L);
        mockLogStreamReader.addEvent(loggedEvent);

        loggedEvent = eventAt(8L);
        mockLogStreamReader.addEvent(loggedEvent);

        // when
        open();

        // process 6 event
        controller.doWork();
        assertThat(controller.stateMachineAgent.getCurrentState()).isEqualTo(controller.processState);
        controller.doWork();

        // process 7
        controller.doWork();
        assertThat(controller.stateMachineAgent.getCurrentState()).isEqualTo(controller.processState);
        controller.doWork();

        // process 8
        controller.doWork();
        assertThat(controller.stateMachineAgent.getCurrentState()).isEqualTo(controller.processState);
        controller.doWork();

        assertThat(controller.isOpen()).isTrue();

        // then
        verify(mockSnapshotStorage).getLastSnapshot(STREAM_PROCESSOR_NAME);
        verify(mockReadableSnapshot).recoverFromSnapshot(mockStateResource);

        assertThat(mockLogStreamReader.getHasNextInvocations()).isEqualTo(9);
        assertThat(mockLogStreamReader.getPosition()).isEqualTo(8L);
    }

    @Test
    public void shouldReprocessEventsOnRecovery() throws Exception
    {
        // given
        reprocessingEventFilter.doFilter = true;

        final LoggedEvent firstEvent = eventAt(2L);
        mockLogStreamReader.addEvent(firstEvent);

        final LoggedEvent secondEvent = eventAt(3L);
        mockLogStreamReader.addEvent(secondEvent);

        when(mockSourceEvent.getPosition()).thenReturn(4L);
        when(mockSourceEvent.getProducerId()).thenReturn(STREAM_PROCESSOR_ID);
        when(mockSourceEvent.getSourceEventPosition()).thenReturn(3L);
        mockLogStreamReader.addEvent(mockSourceEvent);

        // when
        open();
        // -> recovery - no more events
        controller.doWork();
        controller.doWork();

        // then
        assertThat(controller.isOpen()).isTrue();

        verify(mockSnapshotStorage).getLastSnapshot(STREAM_PROCESSOR_NAME);
        final InOrder inOrder = inOrder(mockStreamProcessor, mockEventProcessor);

        inOrder.verify(mockStreamProcessor).onOpen(any(StreamProcessorContext.class));

        inOrder.verify(mockStreamProcessor).onEvent(firstEvent);
        inOrder.verify(mockEventProcessor).processEvent();
        inOrder.verify(mockEventProcessor).updateState();
        inOrder.verify(mockStreamProcessor).afterEvent();

        inOrder.verify(mockStreamProcessor).onEvent(secondEvent);
        inOrder.verify(mockEventProcessor).processEvent();
        inOrder.verify(mockEventProcessor).updateState();
        inOrder.verify(mockStreamProcessor).afterEvent();

        inOrder.verifyNoMoreInteractions();
    }


    @Test
    public void shouldReprocessEventsOnRecoveryIfFilterIsNull() throws Exception
    {
        // given
        controller = builder.reprocessingEventFilter(null).build();

        final LoggedEvent firstEvent = eventAt(2L);
        mockLogStreamReader.addEvent(firstEvent);

        final LoggedEvent secondEvent = eventAt(3L);
        mockLogStreamReader.addEvent(secondEvent);

        when(mockSourceEvent.getPosition()).thenReturn(4L);
        when(mockSourceEvent.getProducerId()).thenReturn(STREAM_PROCESSOR_ID);
        when(mockSourceEvent.getSourceEventPosition()).thenReturn(3L);
        mockLogStreamReader.addEvent(mockSourceEvent);

        // when
        open();
        // -> recovery - no more events
        controller.doWork();
        controller.doWork();

        // then
        assertThat(controller.isOpen()).isTrue();

        verify(mockSnapshotStorage).getLastSnapshot(STREAM_PROCESSOR_NAME);
        final InOrder inOrder = inOrder(mockStreamProcessor, mockEventProcessor);

        inOrder.verify(mockStreamProcessor).onOpen(any(StreamProcessorContext.class));

        inOrder.verify(mockStreamProcessor).onEvent(firstEvent);
        inOrder.verify(mockEventProcessor).processEvent();
        inOrder.verify(mockEventProcessor).updateState();
        inOrder.verify(mockStreamProcessor).afterEvent();

        inOrder.verify(mockStreamProcessor).onEvent(secondEvent);
        inOrder.verify(mockEventProcessor).processEvent();
        inOrder.verify(mockEventProcessor).updateState();
        inOrder.verify(mockStreamProcessor).afterEvent();

        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void shouldNotReprocessEventsOnRecoveryIfFilterReturnsFalse() throws Exception
    {
        // given
        reprocessingEventFilter.doFilter = false;

        final LoggedEvent firstEvent = eventAt(2L);
        mockLogStreamReader.addEvent(firstEvent);

        final LoggedEvent secondEvent = eventAt(3L);
        mockLogStreamReader.addEvent(secondEvent);

        when(mockSourceEvent.getPosition()).thenReturn(4L);
        when(mockSourceEvent.getProducerId()).thenReturn(STREAM_PROCESSOR_ID);
        when(mockSourceEvent.getSourceEventPosition()).thenReturn(3L);
        mockLogStreamReader.addEvent(mockSourceEvent);

        // when
        open();

        // then
        assertThat(controller.isOpen()).isTrue();
        verify(mockSnapshotStorage).getLastSnapshot(STREAM_PROCESSOR_NAME);

        verify(mockStreamProcessor, never()).onEvent(any());
        verify(mockStreamProcessor, times(1)).onOpen(any(StreamProcessorContext.class));
    }

    @Test
    public void shouldReprocessEventsOnRecoveryIfWrittenEventIsFromController() throws Exception
    {
        // given
        reprocessingEventFilter.doFilter = true;

        final LoggedEvent firstEvent = eventAt(2L);
        when(firstEvent.getProducerId()).thenReturn(99);
        mockLogStreamReader.addEvent(firstEvent);

        final LoggedEvent secondEvent = eventAt(3L);
        when(firstEvent.getProducerId()).thenReturn(99);
        mockLogStreamReader.addEvent(secondEvent);

        when(mockSourceEvent.getPosition()).thenReturn(4L);
        when(mockSourceEvent.getProducerId()).thenReturn(STREAM_PROCESSOR_ID);
        when(mockSourceEvent.getSourceEventPosition()).thenReturn(3L);
        mockLogStreamReader.addEvent(mockSourceEvent);

        // when
        open();

        controller.doWork();
        controller.doWork();

        // then
        assertThat(controller.isOpen()).isTrue();

        verify(mockSnapshotStorage).getLastSnapshot(STREAM_PROCESSOR_NAME);
        final InOrder inOrder = inOrder(mockStreamProcessor, mockEventProcessor);

        inOrder.verify(mockStreamProcessor).onOpen(any(StreamProcessorContext.class));

        inOrder.verify(mockStreamProcessor).onEvent(firstEvent);
        inOrder.verify(mockEventProcessor).processEvent();
        inOrder.verify(mockEventProcessor).updateState();
        inOrder.verify(mockStreamProcessor).afterEvent();

        inOrder.verify(mockStreamProcessor).onEvent(secondEvent);
        inOrder.verify(mockEventProcessor).processEvent();
        inOrder.verify(mockEventProcessor).updateState();
        inOrder.verify(mockStreamProcessor).afterEvent();

        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void shouldNotReprocessEventsOnRecoveryIfEventsAreNotFromController() throws Exception
    {
        // given
        reprocessingEventFilter.doFilter = true;

        final LoggedEvent firstEvent = eventAt(2L);
        when(firstEvent.getProducerId()).thenReturn(99);
        mockLogStreamReader.addEvent(firstEvent);

        final LoggedEvent secondEvent = eventAt(3L);
        when(firstEvent.getProducerId()).thenReturn(99);
        mockLogStreamReader.addEvent(secondEvent);

        when(mockSourceEvent.getPosition()).thenReturn(4L);
        when(mockSourceEvent.getProducerId()).thenReturn(99);
        when(mockSourceEvent.getSourceEventPosition()).thenReturn(3L);
        mockLogStreamReader.addEvent(mockSourceEvent);

        // when
        open();

        // then
        assertThat(controller.isOpen()).isTrue();
        verify(mockSnapshotStorage).getLastSnapshot(STREAM_PROCESSOR_NAME);

        verify(mockStreamProcessor, never()).onEvent(any());
        verify(mockStreamProcessor, times(1)).onOpen(any(StreamProcessorContext.class));
    }

    @Test
    public void shouldReprocessEventBatchOnRecovery() throws Exception
    {
        // given
        reprocessingEventFilter.doFilter = true;

        // read multiple events which has the same source event (i.e. event batch)
        final LoggedEvent sourceEvent = eventAt(3L);
        mockLogStreamReader.addEvent(sourceEvent);

        LoggedEvent loggedEvent = eventAt(4L);
        when(loggedEvent.getSourceEventPosition()).thenReturn(3L);
        mockLogStreamReader.addEvent(loggedEvent);

        loggedEvent = eventAt(5L);
        when(loggedEvent.getSourceEventPosition()).thenReturn(3L);
        mockLogStreamReader.addEvent(loggedEvent);

        // when
        open();

        // -> recovery - no more events
        controller.doWork();

        // then
        assertThat(controller.isOpen()).isTrue();
        assertThat(mockLogStreamReader.getHasNextInvocations()).isEqualTo(6);

        // re-process the source event only
        final InOrder inOrder = inOrder(mockStreamProcessor, mockEventProcessor);
        inOrder.verify(mockStreamProcessor).onOpen(any(StreamProcessorContext.class));
        inOrder.verify(mockStreamProcessor).onEvent(sourceEvent);
        inOrder.verify(mockEventProcessor).processEvent();
        inOrder.verify(mockEventProcessor).updateState();
        inOrder.verify(mockStreamProcessor).afterEvent();

        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void shouldIgnoreEventsFromOtherProducerOnRecovery() throws Exception
    {
        // given
        final LoggedEvent sourceEvent = eventAt(3L);
        mockLogStreamReader.addEvent(sourceEvent);

        final LoggedEvent secondSourceEvent = eventAt(4L);
        mockLogStreamReader.addEvent(secondSourceEvent);

        final LoggedEvent writtenEvent = eventAt(5L);
        when(writtenEvent.getSourceEventPosition()).thenReturn(3L);
        mockLogStreamReader.addEvent(writtenEvent);

        final LoggedEvent writtenEventFromOtherProducer = eventAt(6L);
        when(writtenEventFromOtherProducer.getSourceEventPosition()).thenReturn(4L);
        when(writtenEventFromOtherProducer.getProducerId()).thenReturn(99);
        mockLogStreamReader.addEvent(writtenEventFromOtherProducer);

        // when
        open();
        // -> recovery - re-process event
        controller.doWork();
        // -> recovery - no more events
        controller.doWork();

        // then
        assertThat(controller.isOpen()).isTrue();
        assertThat(mockLogStreamReader.getHasNextInvocations()).isEqualTo(8);

        verify(mockStreamProcessor, times(1)).onEvent(sourceEvent);
        verify(mockEventProcessor, times(1)).processEvent();
        verify(mockEventProcessor, times(1)).updateState();
        verify(mockStreamProcessor, times(1)).afterEvent();
        verify(mockStreamProcessor, times(1)).onOpen(any(StreamProcessorContext.class));
    }

    @Test
    public void shouldFailToRecoverFromSnapshot() throws Exception
    {
        // given
        when(mockSnapshotStorage.getLastSnapshot(STREAM_PROCESSOR_NAME)).thenReturn(mockReadableSnapshot);
        doThrow(new RuntimeException("expected exception")).when(mockReadableSnapshot).recoverFromSnapshot(any());

        // when
        final CompletableFuture<Void> future = controller.openAsync();
        // -> opening
        controller.doWork();
        // -> recover
        controller.doWork();

        // then
        assertThat(controller.isFailed()).isTrue();
        assertThat(future).isCompletedExceptionally();

        expectedException.expectMessage("expected exception");
        expectedException.expect(RuntimeException.class);
        future.join();
    }

    @Test
    public void shouldFailToRecoverIfSouceEventNotFound() throws Exception
    {
        // given
        final LoggedEvent loggedEvent = eventAt(5L);
        when(loggedEvent.getSourceEventPosition()).thenReturn(3L);
        mockLogStreamReader.addEvent(loggedEvent);

        // when
        open();
        controller.doWork();

        // then
        assertThat(controller.isFailed()).isTrue();
    }

    @Test
    public void shouldFailToCreateSnapshot() throws Exception
    {
        // given
        when(mockEventProcessor.executeSideEffects()).thenReturn(true);
        when(mockEventProcessor.writeEvent(mockLogStreamWriter)).thenReturn(1L);
        when(mockSnapshotPolicy.apply(anyLong())).thenReturn(true);
        when(mockLogStream.getCommitPosition()).thenReturn(1L);

        doThrow(new RuntimeException("expected exception")).when(mockSnapshotWriter).commit();
        open();
        mockLogStreamReader.addEvent(mockSourceEvent);

        // when
        // -> open
        controller.doWork();
        // -> processing
        controller.doWork();
        // -> snapshotting
        controller.doWork();

        // then
        // just continue if fails to create the snapshot
        assertThat(controller.isOpen()).isTrue();

        verify(mockSnapshotWriter).abort();
    }

    /**
     * <p>This behavior is actually important for certain error cases in the broker
     * where a stream processor can throw an exception and stream processing should
     * not continue or be recoverable unless the controller is restarted (cf https://github.com/zeebe-io/zeebe/issues/109).
     *
     * <p>If you extend/change the behavior, please make sure the current behavior is maintained
     */
    @Test
    public void shouldFailIfProcessingErrorOccurs()
    {
        // given
        doThrow(new RuntimeException("expected exception")).when(mockEventProcessor).executeSideEffects();

        open();
        mockLogStreamReader.addEvent(mockSourceEvent);

        // when
        // -> open, processing, error handling
        controller.doWork();
        controller.doWork();
        controller.doWork();

        // then
        assertThat(controller.isFailed()).isTrue();

        // verify that it can't be recovered
        targetLogStreamFailureListener.onFailed(2L);
        targetLogStreamFailureListener.onRecovered();

        controller.doWork();

        assertThat(controller.isFailed()).isTrue();
    }

    @Test
    public void shouldFailIfReprocessingErrorOccures()
    {
        final LoggedEvent sourceEvent = eventAt(3L);
        mockLogStreamReader.addEvent(sourceEvent);

        final LoggedEvent writtenEvent = eventAt(5L);
        when(writtenEvent.getSourceEventPosition()).thenReturn(3L);
        mockLogStreamReader.addEvent(writtenEvent);

        final RuntimeException expectedException = new RuntimeException("expected exception");
        doThrow(expectedException).when(mockStreamProcessor).onEvent(sourceEvent);

        open();
        // -> recovery - no more events
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

        open();
        mockLogStreamReader.addEvent(mockSourceEvent);

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

        open();
        mockLogStreamReader.addEvent(mockSourceEvent);

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

        open();
        mockLogStreamReader.addEvent(mockSourceEvent);

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
        // given
        when(mockEventProcessor.executeSideEffects()).thenReturn(true);
        when(mockEventProcessor.writeEvent(mockLogStreamWriter)).thenReturn(2L);
        when(mockSnapshotPolicy.apply(anyLong())).thenReturn(false);
        when(mockLogStream.getCommitPosition()).thenReturn(3L);

        open();
        final LoggedEvent loggedEvent = eventAt(1L);
        mockLogStreamReader.addEvent(loggedEvent);

        // -> open
        controller.doWork();
        // -> processing
        controller.doWork();

        // when
        controller.closeAsync();

        // then
        // closing snapshotting
        controller.doWork();

        verify(mockSnapshotStorage).createSnapshot(STREAM_PROCESSOR_NAME, 1L);
        verify(mockSnapshotWriter).writeSnapshot(mockStateResource);
        verify(mockSnapshotWriter).commit();
    }

    @Test
    public void shouldWriteSnapshotOnCloseOfLastSuccessfulProcessEvent() throws Exception
    {
        // given
        when(mockEventProcessor.executeSideEffects()).thenReturn(true);
        when(mockEventProcessor.writeEvent(mockLogStreamWriter)).thenReturn(2L);
        when(mockSnapshotPolicy.apply(anyLong())).thenReturn(false);
        when(mockLogStream.getCommitPosition()).thenReturn(3L);

        open();
        final LoggedEvent loggedEvent = eventAt(1L);
        mockLogStreamReader.addEvent(loggedEvent);
        final LoggedEvent secondEvent = eventAt(2L);
        mockLogStreamReader.addEvent(secondEvent);

        // -> open
        controller.doWork();
        // -> processing first
        controller.doWork();
        // -> next
        controller.doWork();

        // when
        controller.closeAsync();

        // then
        // closing snapshotting
        controller.doWork();

        verify(mockSnapshotStorage).createSnapshot(STREAM_PROCESSOR_NAME, 1L);
        verify(mockSnapshotWriter).writeSnapshot(mockStateResource);
        verify(mockSnapshotWriter).commit();
    }

    @Test
    public void shouldNotWriteSnapshotIfLastWrittenEventWasNotCommited() throws Exception
    {
        // given
        when(mockEventProcessor.executeSideEffects()).thenReturn(true);
        when(mockEventProcessor.writeEvent(mockLogStreamWriter)).thenReturn(3L, 4L);
        when(mockSnapshotPolicy.apply(anyLong())).thenReturn(false);
        when(mockLogStream.getCommitPosition()).thenReturn(2L);

        open();
        final LoggedEvent loggedEvent = eventAt(1L);
        mockLogStreamReader.addEvent(loggedEvent);
        final LoggedEvent secondEvent = eventAt(2L);
        mockLogStreamReader.addEvent(secondEvent);

        // -> open
        controller.doWork();
        // -> processing first
        controller.doWork();
        // -> next
        controller.doWork();

        // when
        controller.closeAsync();

        // then
        // closing snapshotting
        controller.doWork();

        verify(mockSnapshotStorage, never()).createSnapshot(eq(STREAM_PROCESSOR_NAME), anyLong());
        verify(mockSnapshotWriter, never()).writeSnapshot(mockStateResource);
        verify(mockSnapshotWriter, never()).commit();
    }

    @Test
    public void shouldCancelSnapshottingOnCloseInCaseOfLogFailure() throws Exception
    {
        // given
        when(mockEventProcessor.executeSideEffects()).thenReturn(true);
        when(mockEventProcessor.writeEvent(mockLogStreamWriter)).thenReturn(2L);
        when(mockSnapshotPolicy.apply(anyLong())).thenReturn(false);
        when(mockLogStream.getCurrentAppenderPosition()).thenReturn(1L);

        open();
        final LoggedEvent loggedEvent = eventAt(255L);
        mockLogStreamReader.addEvent(loggedEvent);

        // -> open
        controller.doWork();
        // -> processing
        controller.doWork();

        // when
        final CompletableFuture<Void> closeFuture = controller.closeAsync();
        // -> closing snapshotting
        controller.doWork(); // does not write snapshot as appender hasn't caught up yet

        targetLogStreamFailureListener.onFailed(255L);

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
        // given
        when(mockEventProcessor.executeSideEffects()).thenReturn(true);
        when(mockEventProcessor.writeEvent(mockLogStreamWriter)).thenReturn(2L, 3L);

        when(mockLogStream.getCommitPosition()).thenReturn(2L);
        when(mockSnapshotPolicy.apply(anyLong())).thenReturn(true);

        open();
        mockLogStreamReader.addEvent(mockSourceEvent);
        final LoggedEvent secondEvent = mock(LoggedEvent.class);
        when(secondEvent.getPosition()).thenReturn(1L);
        mockLogStreamReader.addEvent(secondEvent);

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

        when(mockLogStream.getCommitPosition()).thenReturn(3L);

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
        // given
        when(mockEventProcessor.executeSideEffects()).thenReturn(true);
        when(mockEventProcessor.writeEvent(mockLogStreamWriter)).thenReturn(2L);
        when(mockLogStream.getCommitPosition()).thenReturn(3L);
        when(mockSnapshotPolicy.apply(anyLong())).thenReturn(true);

        open();
        mockLogStreamReader.addEvent(mockSourceEvent);

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

        when(mockSourceEvent.getPosition()).thenReturn(3L);
        mockLogStreamReader.addEvent(mockSourceEvent);

        // when
        open();

        // then
        assertThat(controller.isOpen()).isTrue();
        assertThat(mockLogStreamReader.iteratorPosition).isEqualTo(0);

    }

    protected void open()
    {
        controller.openAsync();
        // -> opening
        controller.doWork();
        // -> recovery
        controller.doWork();
        // -> pre-re-processing
        controller.doWork();
    }

    protected LoggedEvent eventAt(long position)
    {
        final LoggedEvent event = mock(LoggedEvent.class);
        when(event.getPosition()).thenReturn(position);
        when(event.getProducerId()).thenReturn(STREAM_PROCESSOR_ID);
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
