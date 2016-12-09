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
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;

import org.agrona.concurrent.Agent;
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

    @Mock
    private LogStreamReader mockLogStreamReader;

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
    private LoggedEvent mockEvent;

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
        context.setLogStreamReader(mockLogStreamReader);
        context.setLogStreamWriter(mockLogStreamWriter);
        context.setSnapshotPolicy(mockSnapshotPolicy);
        context.setSnapshotStorage(mockSnapshotStorage);
        context.setStateResource(mockStateResource);

        when(mockStreamProcessor.onEvent(any(LoggedEvent.class))).thenReturn(mockEventProcessor);

        when(mockSnapshotStorage.createSnapshot(anyString(), anyLong())).thenReturn(mockSnapshotWriter);

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

        controller.doWork();

        assertThat(future).isCompleted();
        assertThat(controller.isOpen()).isTrue();

        verify(mockAgentRunnerService).run(any(Agent.class));

        verify(mockLogStreamReader).wrap(mockSourceLogStream);
        verify(mockLogStreamWriter).wrap(mockTargetLogStream);
        verify(mockStreamProcessor).onOpen(any(StreamProcessorContext.class));
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

        verify(mockLogStreamReader, times(1)).wrap(mockSourceLogStream);
        verify(mockLogStreamWriter, times(1)).wrap(mockTargetLogStream);
        verify(mockStreamProcessor, times(1)).onOpen(any(StreamProcessorContext.class));
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

        verify(mockStreamProcessor).onClose();

        verify(mockAgentRunnerService).remove(any(Agent.class));
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
        when(mockLogStreamReader.hasNext()).thenReturn(false, true);
        when(mockLogStreamReader.next()).thenReturn(mockEvent);

        controller.openAsync();
        controller.doWork();

        // -> open
        controller.doWork();
        // -> open
        controller.doWork();

        verify(mockLogStreamReader, times(2)).hasNext();
        verify(mockLogStreamReader, times(1)).next();
    }

    @Test
    public void shouldProcessPolledEvent()
    {
        when(mockLogStreamReader.hasNext()).thenReturn(true);
        when(mockLogStreamReader.next()).thenReturn(mockEvent);

        when(mockEventProcessor.executeSideEffects()).thenReturn(true);
        when(mockEventProcessor.writeEvent(mockLogStreamWriter)).thenReturn(1L);

        controller.openAsync();
        controller.doWork();

        // -> open
        controller.doWork();
        // -> processing
        controller.doWork();

        assertThat(controller.isOpen()).isTrue();

        final InOrder inOrder = inOrder(mockStreamProcessor, mockEventProcessor);

        inOrder.verify(mockStreamProcessor).onOpen(any(StreamProcessorContext.class));
        inOrder.verify(mockStreamProcessor).onEvent(mockEvent);

        inOrder.verify(mockEventProcessor).processEvent();
        inOrder.verify(mockEventProcessor).executeSideEffects();
        inOrder.verify(mockEventProcessor).writeEvent(mockLogStreamWriter);
        inOrder.verify(mockEventProcessor).updateState();

        inOrder.verify(mockStreamProcessor).afterEvent();
    }

    @Test
    public void shouldRetryExecuteSideEffectsIfFail()
    {
        when(mockLogStreamReader.hasNext()).thenReturn(true);
        when(mockLogStreamReader.next()).thenReturn(mockEvent);

        when(mockEventProcessor.executeSideEffects()).thenReturn(false, true);
        when(mockEventProcessor.writeEvent(mockLogStreamWriter)).thenReturn(1L);

        controller.openAsync();
        controller.doWork();

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
        when(mockLogStreamReader.hasNext()).thenReturn(true);
        when(mockLogStreamReader.next()).thenReturn(mockEvent);

        when(mockEventProcessor.executeSideEffects()).thenReturn(true);
        when(mockEventProcessor.writeEvent(mockLogStreamWriter)).thenReturn(-1L, 1L);

        controller.openAsync();
        controller.doWork();

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
    public void shouldAddStreamProcessorIdToWrittenEvent()
    {
        when(mockLogStreamReader.hasNext()).thenReturn(true);
        when(mockLogStreamReader.next()).thenReturn(mockEvent);

        when(mockEventProcessor.executeSideEffects()).thenReturn(true);
        when(mockEventProcessor.writeEvent(mockLogStreamWriter)).thenReturn(1L);

        controller.openAsync();
        controller.doWork();

        // -> open
        controller.doWork();
        // -> processing
        controller.doWork();

        verify(mockLogStreamWriter).streamProcessorId(STREAM_PROCESSOR_ID);
    }

    @Test
    public void shouldCreateSnapshot() throws Exception
    {
        when(mockLogStreamReader.hasNext()).thenReturn(true);
        when(mockLogStreamReader.next()).thenReturn(mockEvent);

        when(mockEventProcessor.executeSideEffects()).thenReturn(true);
        when(mockEventProcessor.writeEvent(mockLogStreamWriter)).thenReturn(1L);

        when(mockSnapshotPolicy.apply(anyLong())).thenReturn(true);
        when(mockTargetLogStream.getCurrentAppenderPosition()).thenReturn(1L);

        controller.openAsync();
        controller.doWork();

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
        when(mockLogStreamReader.hasNext()).thenReturn(true);
        when(mockLogStreamReader.next()).thenReturn(mockEvent);

        when(mockEventProcessor.executeSideEffects()).thenReturn(true);
        when(mockEventProcessor.writeEvent(mockLogStreamWriter)).thenReturn(1L);

        when(mockSnapshotPolicy.apply(anyLong())).thenReturn(false);
        when(mockTargetLogStream.getCurrentAppenderPosition()).thenReturn(1L);

        controller.openAsync();
        controller.doWork();

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
        when(mockLogStreamReader.hasNext()).thenReturn(true);
        when(mockLogStreamReader.next()).thenReturn(mockEvent);

        when(mockEventProcessor.executeSideEffects()).thenReturn(true);
        when(mockEventProcessor.writeEvent(mockLogStreamWriter)).thenReturn(2L);

        when(mockSnapshotPolicy.apply(anyLong())).thenReturn(true);
        when(mockTargetLogStream.getCurrentAppenderPosition()).thenReturn(1L, 2L);

        controller.openAsync();
        controller.doWork();

        // -> open
        controller.doWork();
        // -> processing
        controller.doWork();
        // -> snapshotting
        controller.doWork();
        // -> stay in snapshotting state until log appender wrote the newly created event
        controller.doWork();

        assertThat(controller.isOpen()).isTrue();

        verify(mockLogStreamReader, times(1)).hasNext();
        verify(mockTargetLogStream, times(2)).getCurrentAppenderPosition();

        verify(mockSnapshotStorage, times(1)).createSnapshot(STREAM_PROCESSOR_NAME, 2L);
        verify(mockSnapshotWriter, times(1)).writeSnapshot(mockStateResource);
        verify(mockSnapshotWriter, times(1)).commit();
    }

    @Test
    public void shouldFailWhileSnapshotting() throws Exception
    {
        when(mockLogStreamReader.hasNext()).thenReturn(true);
        when(mockLogStreamReader.next()).thenReturn(mockEvent);

        when(mockEventProcessor.executeSideEffects()).thenReturn(true);
        when(mockEventProcessor.writeEvent(mockLogStreamWriter)).thenReturn(2L);

        when(mockSnapshotPolicy.apply(anyLong())).thenReturn(true);
        when(mockTargetLogStream.getCurrentAppenderPosition()).thenReturn(1L);

        controller.openAsync();
        controller.doWork();

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

        verify(mockLogStreamReader, times(1)).hasNext();
        verify(mockSnapshotStorage, never()).createSnapshot(STREAM_PROCESSOR_NAME, 2L);
    }

    @Test
    public void shouldFailWhilePollEvents() throws Exception
    {
        controller.openAsync();
        controller.doWork();

        // -> open
        controller.doWork();

        targetLogStreamFailureListener.onFailed(1L);

        // -> failed
        controller.doWork();

        assertThat(controller.isFailed()).isTrue();

        verify(mockLogStreamReader, times(1)).hasNext();
    }

}
