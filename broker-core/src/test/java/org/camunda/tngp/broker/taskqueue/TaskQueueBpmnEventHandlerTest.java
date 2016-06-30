package org.camunda.tngp.broker.taskqueue;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.mockito.Matchers.longThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;

import org.camunda.tngp.broker.test.util.FluentMock;
import org.camunda.tngp.broker.transport.worker.spi.ResourceContextProvider;
import org.camunda.tngp.broker.wf.runtime.bpmn.event.BpmnActivityEventReader;
import org.camunda.tngp.broker.wf.runtime.bpmn.event.BpmnEventReader;
import org.camunda.tngp.broker.wf.runtime.bpmn.event.BpmnFlowElementEventReader;
import org.camunda.tngp.broker.wf.runtime.bpmn.event.BpmnProcessEventReader;
import org.camunda.tngp.graph.bpmn.ExecutionEventType;
import org.camunda.tngp.log.LogWriter;
import org.camunda.tngp.taskqueue.data.BpmnActivityEventDecoder;
import org.camunda.tngp.taskqueue.data.BpmnFlowElementEventDecoder;
import org.camunda.tngp.taskqueue.data.BpmnProcessEventDecoder;
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

    protected void mockActivityInstanceEvent()
    {
        when(activityEventReader.event()).thenReturn(ExecutionEventType.ACT_INST_CREATED);
        when(activityEventReader.flowElementId()).thenReturn(1235);
        when(activityEventReader.key()).thenReturn(6778L);
        when(activityEventReader.processId()).thenReturn(8888L);
        when(activityEventReader.processInstanceId()).thenReturn(9999L);

        when(activityEventReader.taskQueueId()).thenReturn(3);
        when(activityEventReader.getTaskType()).thenReturn(taskTypeBuffer);

        when(bpmnEventReader.templateId()).thenReturn(BpmnActivityEventDecoder.TEMPLATE_ID);
        when(bpmnEventReader.activityEvent()).thenReturn(activityEventReader);
    }

    protected void mockFlowElementEvent()
    {
        when(flowElementEventReader.event()).thenReturn(ExecutionEventType.EVT_OCCURRED);
        when(flowElementEventReader.flowElementId()).thenReturn(1235);
        when(flowElementEventReader.key()).thenReturn(6778L);
        when(flowElementEventReader.processId()).thenReturn(8888L);
        when(flowElementEventReader.processInstanceId()).thenReturn(9999L);

        when(bpmnEventReader.templateId()).thenReturn(BpmnFlowElementEventDecoder.TEMPLATE_ID);
        when(bpmnEventReader.flowElementEvent()).thenReturn(flowElementEventReader);
    }

    protected void mockProcessEvent()
    {

        when(processEventReader.event()).thenReturn(ExecutionEventType.PROC_INST_CREATED);
        when(processEventReader.initialElementId()).thenReturn(1235);
        when(processEventReader.key()).thenReturn(6778L);
        when(processEventReader.processId()).thenReturn(8888L);
        when(processEventReader.processInstanceId()).thenReturn(9999L);

        when(bpmnEventReader.templateId()).thenReturn(BpmnProcessEventDecoder.TEMPLATE_ID);
        when(bpmnEventReader.processEvent()).thenReturn(processEventReader);
    }

    @Test
    public void shouldHandleActivityInstanceCreateEvent()
    {
        // given
        mockActivityInstanceEvent();

        final TaskQueueBpmnEventHandler handler = new TaskQueueBpmnEventHandler(taskQueueContextProvider);
        handler.setTaskInstanceWriter(taskInstanceWriter);

        final LogWriter logWriter = taskQueueContext.getLogWriter();

        // when
        handler.handle(bpmnEventReader);

        // then
        final InOrder inOrder = inOrder(taskInstanceWriter, logWriter);
        inOrder.verify(taskInstanceWriter).id(longThat(greaterThanOrEqualTo(0L)));
        inOrder.verify(taskInstanceWriter).taskType(taskTypeBuffer, 0, 3);
        inOrder.verify(logWriter).write(taskInstanceWriter);
    }

    @Test
    public void shouldIgnoreFlowElementEvent()
    {
        // given
        mockFlowElementEvent();

        final TaskQueueBpmnEventHandler handler = new TaskQueueBpmnEventHandler(taskQueueContextProvider);
        handler.setTaskInstanceWriter(taskInstanceWriter);

        final LogWriter logWriter = taskQueueContext.getLogWriter();

        // when
        handler.handle(bpmnEventReader);

        // then
        verifyZeroInteractions(taskInstanceWriter, logWriter);
    }

    @Test
    public void shouldIgnoreProcessEvent()
    {
        // given
        mockProcessEvent();

        final TaskQueueBpmnEventHandler handler = new TaskQueueBpmnEventHandler(taskQueueContextProvider);
        handler.setTaskInstanceWriter(taskInstanceWriter);

        final LogWriter logWriter = taskQueueContext.getLogWriter();

        // when
        handler.handle(bpmnEventReader);

        // then
        verifyZeroInteractions(taskInstanceWriter, logWriter);
    }

}
