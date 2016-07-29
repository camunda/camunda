package org.camunda.tngp.broker.wf.runtime.bpmn.handler;

import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;

import org.camunda.tngp.broker.taskqueue.log.TaskInstanceReader;
import org.camunda.tngp.broker.test.util.FluentMock;
import org.camunda.tngp.broker.util.mocks.BpmnEventMocks;
import org.camunda.tngp.broker.wf.runtime.bpmn.event.BpmnActivityEventReader;
import org.camunda.tngp.broker.wf.runtime.bpmn.event.BpmnActivityEventWriter;
import org.camunda.tngp.graph.bpmn.ExecutionEventType;
import org.camunda.tngp.hashindex.Long2LongHashIndex;
import org.camunda.tngp.log.LogReader;
import org.camunda.tngp.log.LogWriter;
import org.camunda.tngp.taskqueue.data.TaskInstanceState;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import uk.co.real_logic.agrona.DirectBuffer;
import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

public class TaskEventHandlerTest
{

    @Mock
    protected TaskInstanceReader taskInstanceReader;

    @Mock
    protected LogWriter logWriter;

    @Mock
    protected LogReader logReader;

    @Mock
    protected Long2LongHashIndex workflowEventIndex;

    @Mock
    protected BpmnActivityEventReader activityEventReader;

    @FluentMock
    protected BpmnActivityEventWriter activityEventWriter;

    protected DirectBuffer payloadBuffer = new UnsafeBuffer("PingPong".getBytes(StandardCharsets.UTF_8));
    protected DirectBuffer taskTypeBuffer = new UnsafeBuffer("Foobar".getBytes(StandardCharsets.UTF_8));

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

    @Before
    public void setUp()
    {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldHandleCompleteEvent()
    {
        // given
        final TaskEventHandler handler = new TaskEventHandler(logReader, logWriter, workflowEventIndex);
        handler.setEventWriter(activityEventWriter);
        handler.setLatestEventReader(activityEventReader);

        mockTaskInstanceEvent(TaskInstanceState.COMPLETED);
        BpmnEventMocks.mockActivityInstanceEvent(activityEventReader, ExecutionEventType.ACT_INST_CREATED);

        when(workflowEventIndex.get(eq(23456789L), anyLong())).thenReturn(748L);

        // when
        handler.onComplete(taskInstanceReader);

        // then
        final InOrder inOrder = Mockito.inOrder(logReader, activityEventWriter, logWriter);

        inOrder.verify(logReader).setPosition(748L);
        inOrder.verify(logReader).read(activityEventReader);
        inOrder.verify(activityEventWriter).eventType(ExecutionEventType.ACT_INST_COMPLETED);
        inOrder.verify(activityEventWriter).flowElementId(1235);
        inOrder.verify(activityEventWriter).key(23456789L);
        inOrder.verify(activityEventWriter).wfDefinitionId(8888L);
        inOrder.verify(activityEventWriter).wfInstanceId(9999L);
        inOrder.verify(activityEventWriter).taskQueueId(3);
        inOrder.verify(logWriter).write(activityEventWriter);

        verifyNoMoreInteractions(logReader);
        verifyNoMoreInteractions(logWriter);

    }
}
