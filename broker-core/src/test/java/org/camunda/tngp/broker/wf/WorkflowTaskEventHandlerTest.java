package org.camunda.tngp.broker.wf;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;

import org.camunda.tngp.broker.taskqueue.log.TaskInstanceReader;
import org.camunda.tngp.broker.test.util.FluentMock;
import org.camunda.tngp.broker.transport.worker.spi.ResourceContextProvider;
import org.camunda.tngp.broker.wf.runtime.MockWfRuntimeContext;
import org.camunda.tngp.broker.wf.runtime.WfRuntimeContext;
import org.camunda.tngp.broker.wf.runtime.bpmn.event.BpmnActivityEventWriter;
import org.camunda.tngp.broker.wf.runtime.bpmn.handler.TaskEventHandler;
import org.camunda.tngp.taskqueue.data.TaskInstanceState;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import uk.co.real_logic.agrona.DirectBuffer;
import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

public class WorkflowTaskEventHandlerTest
{


    @Mock
    protected ResourceContextProvider<WfRuntimeContext> wfRuntimeContextProvider;

    @Mock
    protected TaskInstanceReader taskInstanceReader;

    @FluentMock
    protected BpmnActivityEventWriter eventWriter;

    protected WfRuntimeContext runtimeContext;

    protected DirectBuffer payloadBuffer = new UnsafeBuffer("PingPong".getBytes(StandardCharsets.UTF_8));
    protected DirectBuffer taskTypeBuffer = new UnsafeBuffer("Foobar".getBytes(StandardCharsets.UTF_8));

    @Before
    public void setUp()
    {
        MockitoAnnotations.initMocks(this);

        runtimeContext = new MockWfRuntimeContext();
        when(wfRuntimeContextProvider.getContextForResource(7)).thenReturn(runtimeContext);
    }

    protected void mockTaskInstanceEvent(TaskInstanceState taskState)
    {
        when(taskInstanceReader.getPayload()).thenReturn(payloadBuffer);
        when(taskInstanceReader.getTaskType()).thenReturn(taskTypeBuffer);
        when(taskInstanceReader.id()).thenReturn(1234L);
        when(taskInstanceReader.lockOwnerId()).thenReturn(987L);
        when(taskInstanceReader.lockTime()).thenReturn(890L);
        when(taskInstanceReader.prevVersionPosition()).thenReturn(583L);
        when(taskInstanceReader.resourceId()).thenReturn(98);
        when(taskInstanceReader.shardId()).thenReturn(67);
        when(taskInstanceReader.state()).thenReturn(taskState);
        when(taskInstanceReader.taskTypeHash()).thenReturn(1235L);
        when(taskInstanceReader.version()).thenReturn(9876);
        when(taskInstanceReader.wfActivityInstanceEventKey()).thenReturn(23456789L);
        when(taskInstanceReader.wfRuntimeResourceId()).thenReturn(7);
    }

    @Test
    public void shouldHandleCompleteEvent()
    {
        // given
        final WorkflowTaskEventHandler handler = new WorkflowTaskEventHandler(wfRuntimeContextProvider);

        mockTaskInstanceEvent(TaskInstanceState.COMPLETED);

        final TaskEventHandler eventHandler = runtimeContext.getTaskEventHandler();

        // when
        handler.handle(65, taskInstanceReader);

        // then
        verify(eventHandler).onComplete(taskInstanceReader);
        verifyNoMoreInteractions(eventHandler);
    }

    @Test
    public void shouldNotHandleLockedEvent()
    {
        // given
        final WorkflowTaskEventHandler handler = new WorkflowTaskEventHandler(wfRuntimeContextProvider);

        mockTaskInstanceEvent(TaskInstanceState.LOCKED);

        final TaskEventHandler eventHandler = runtimeContext.getTaskEventHandler();

        // when
        handler.handle(65, taskInstanceReader);

        // then
        verifyZeroInteractions(eventHandler);
    }
}
