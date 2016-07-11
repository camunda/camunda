package org.camunda.tngp.broker.taskqueue;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.mockito.Matchers.longThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;

import org.camunda.tngp.broker.test.util.FluentMock;
import org.camunda.tngp.broker.transport.worker.spi.ResourceContextProvider;
import org.camunda.tngp.broker.util.mocks.BpmnEventMocks;
import org.camunda.tngp.broker.wf.runtime.bpmn.event.BpmnActivityEventReader;
import org.camunda.tngp.broker.wf.runtime.bpmn.event.BpmnEventReader;
import org.camunda.tngp.broker.wf.runtime.bpmn.event.BpmnFlowElementEventReader;
import org.camunda.tngp.broker.wf.runtime.bpmn.event.BpmnProcessEventReader;
import org.camunda.tngp.graph.bpmn.ExecutionEventType;
import org.camunda.tngp.log.LogWriter;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import uk.co.real_logic.agrona.DirectBuffer;
import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

public class TaskQueueBpmnEventHandlerTest
{

    @Mock
    protected BpmnEventReader bpmnEventReader;

    @Mock
    protected BpmnActivityEventReader activityEventReader;

    @Mock
    protected BpmnFlowElementEventReader flowElementEventReader;

    @Mock
    protected BpmnProcessEventReader processEventReader;

    @Mock
    protected ResourceContextProvider<TaskQueueContext> taskQueueContextProvider;

    @FluentMock
    protected TaskInstanceWriter taskInstanceWriter;

    protected MockTaskQueueContext taskQueueContext;

    protected DirectBuffer taskTypeBuffer = new UnsafeBuffer("foo".getBytes(StandardCharsets.UTF_8));

    @Before
    public void setUp()
    {
        MockitoAnnotations.initMocks(this);
        taskQueueContext = new MockTaskQueueContext();

        when(taskQueueContextProvider.getContextForResource(3)).thenReturn(taskQueueContext);
    }

    @Test
    public void shouldHandleActivityInstanceCreateEvent()
    {
        // given
        BpmnEventMocks.mockActivityInstanceEvent(bpmnEventReader, activityEventReader, ExecutionEventType.ACT_INST_CREATED);

        final TaskQueueBpmnEventHandler handler = new TaskQueueBpmnEventHandler(taskQueueContextProvider);
        handler.setTaskInstanceWriter(taskInstanceWriter);

        final LogWriter logWriter = taskQueueContext.getLogWriter();

        // when
        handler.handle(0, bpmnEventReader);

        // then
        final InOrder inOrder = inOrder(taskInstanceWriter, logWriter);
        inOrder.verify(taskInstanceWriter).id(longThat(greaterThanOrEqualTo(0L)));
        inOrder.verify(taskInstanceWriter).taskType(taskTypeBuffer, 0, 3);
        inOrder.verify(taskInstanceWriter).wfRuntimeResourceId(6);
        inOrder.verify(taskInstanceWriter).wfActivityInstanceEventKey(23456789L);
        inOrder.verify(logWriter).write(taskInstanceWriter);
    }

    @Test
    public void shouldIgnoreFlowElementEvent()
    {
        // given
        BpmnEventMocks.mockFlowElementEvent(bpmnEventReader, flowElementEventReader);

        final TaskQueueBpmnEventHandler handler = new TaskQueueBpmnEventHandler(taskQueueContextProvider);
        handler.setTaskInstanceWriter(taskInstanceWriter);

        final LogWriter logWriter = taskQueueContext.getLogWriter();

        // when
        handler.handle(0, bpmnEventReader);

        // then
        verifyZeroInteractions(taskInstanceWriter, logWriter);
    }

    @Test
    public void shouldIgnoreProcessEvent()
    {
        // given
        BpmnEventMocks.mockProcessEvent(bpmnEventReader, processEventReader);

        final TaskQueueBpmnEventHandler handler = new TaskQueueBpmnEventHandler(taskQueueContextProvider);
        handler.setTaskInstanceWriter(taskInstanceWriter);

        final LogWriter logWriter = taskQueueContext.getLogWriter();

        // when
        handler.handle(0, bpmnEventReader);

        // then
        verifyZeroInteractions(taskInstanceWriter, logWriter);
    }

}
