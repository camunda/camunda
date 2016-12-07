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
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;

import org.agrona.concurrent.Agent;
import org.camunda.tngp.logstreams.log.LogStream;
import org.camunda.tngp.logstreams.log.LogStreamReader;
import org.camunda.tngp.logstreams.log.LogStreamWriter;
import org.camunda.tngp.logstreams.log.LoggedEvent;
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
    private LoggedEvent mockEvent;

    @Before
    public void init()
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

        when(mockStreamProcessor.onEvent(any(LoggedEvent.class))).thenReturn(mockEventProcessor);

        controller = new StreamProcessorController(context);
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
        verify(mockStreamProcessor).open(any(StreamProcessorContext.class));
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
        verify(mockStreamProcessor, times(1)).open(any(StreamProcessorContext.class));
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

        verify(mockStreamProcessor).close();

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
        when(mockEventProcessor.writeEvent(mockLogStreamWriter)).thenReturn(true);

        controller.openAsync();
        controller.doWork();

        // -> open
        controller.doWork();
        // -> processing
        controller.doWork();

        assertThat(controller.isOpen()).isEqualTo(true);

        final InOrder inOrder = inOrder(mockStreamProcessor, mockEventProcessor);

        inOrder.verify(mockStreamProcessor).open(any(StreamProcessorContext.class));
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
        when(mockEventProcessor.writeEvent(mockLogStreamWriter)).thenReturn(true);

        controller.openAsync();
        controller.doWork();

        // -> open
        controller.doWork();
        // -> processing
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
        when(mockEventProcessor.writeEvent(mockLogStreamWriter)).thenReturn(false, true);

        controller.openAsync();
        controller.doWork();

        // -> open
        controller.doWork();
        // -> processing
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
        when(mockEventProcessor.writeEvent(mockLogStreamWriter)).thenReturn(true);

        controller.openAsync();
        controller.doWork();

        // -> open
        controller.doWork();
        // -> processing
        controller.doWork();

        verify(mockLogStreamWriter).streamProcessorId(STREAM_PROCESSOR_ID);
    }

}
