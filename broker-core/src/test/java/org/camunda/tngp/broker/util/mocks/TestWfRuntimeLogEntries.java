package org.camunda.tngp.broker.util.mocks;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;

import org.camunda.tngp.broker.wf.runtime.log.bpmn.BpmnActivityEventReader;
import org.camunda.tngp.broker.wf.runtime.log.bpmn.BpmnActivityEventWriter;
import org.camunda.tngp.broker.wf.runtime.log.bpmn.BpmnFlowElementEventReader;
import org.camunda.tngp.broker.wf.runtime.log.bpmn.BpmnFlowElementEventWriter;
import org.camunda.tngp.broker.wf.runtime.log.bpmn.BpmnProcessEventReader;
import org.camunda.tngp.broker.wf.runtime.log.bpmn.BpmnProcessEventWriter;
import org.camunda.tngp.graph.bpmn.ExecutionEventType;

import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

public class TestWfRuntimeLogEntries
{
    public static final int FLOW_ELEMENT_ID = 1235;
    public static final long KEY = 23456789L;
    public static final long PROCESS_ID = 8888L;
    public static final long PROCESS_INSTANCE_ID = 9999L;
    public static final int TASK_QUEUE_ID = 3;
    public static final int WF_RUNTIME_LOG_ID = 4;

    public static final byte[] TASK_TYPE = "foo".getBytes(StandardCharsets.UTF_8);

    public static BpmnActivityEventWriter createActivityInstanceEvent(ExecutionEventType event)
    {
        final BpmnActivityEventWriter activityEventWriter = new BpmnActivityEventWriter();

        activityEventWriter.eventType(event);
        activityEventWriter.flowElementId(FLOW_ELEMENT_ID);
        activityEventWriter.key(KEY);
        activityEventWriter.wfDefinitionId(PROCESS_ID);
        activityEventWriter.wfInstanceId(PROCESS_INSTANCE_ID);

        activityEventWriter.taskQueueId(TASK_QUEUE_ID);
        activityEventWriter.taskType(new UnsafeBuffer(TASK_TYPE), 0, TASK_TYPE.length);

        return activityEventWriter;
    }

    public static BpmnFlowElementEventWriter createFlowElementEvent()
    {
        final BpmnFlowElementEventWriter writer = new BpmnFlowElementEventWriter();

        writer.eventType(ExecutionEventType.EVT_OCCURRED);
        writer.flowElementId(FLOW_ELEMENT_ID);
        writer.key(KEY);
        writer.processId(PROCESS_ID);
        writer.workflowInstanceId(PROCESS_INSTANCE_ID);

        return writer;
    }

    public static BpmnProcessEventWriter createProcessEvent(ExecutionEventType event)
    {
        final BpmnProcessEventWriter writer = new BpmnProcessEventWriter();

        writer.event(event);
        writer.initialElementId(FLOW_ELEMENT_ID);
        writer.key(PROCESS_INSTANCE_ID);
        writer.processId(PROCESS_ID);
        writer.processInstanceId(PROCESS_INSTANCE_ID);

        return writer;
    }


    public static BpmnFlowElementEventReader mockFlowElementEvent()
    {
        final BpmnFlowElementEventReader reader = mock(BpmnFlowElementEventReader.class);

        when(reader.event()).thenReturn(ExecutionEventType.EVT_OCCURRED);
        when(reader.flowElementId()).thenReturn(FLOW_ELEMENT_ID);
        when(reader.key()).thenReturn(KEY);
        when(reader.wfDefinitionId()).thenReturn(PROCESS_ID);
        when(reader.wfInstanceId()).thenReturn(PROCESS_INSTANCE_ID);

        return reader;
    }

    public static BpmnProcessEventReader mockProcessEvent()
    {
        final BpmnProcessEventReader reader = new BpmnProcessEventReader();

        when(reader.event()).thenReturn(ExecutionEventType.PROC_INST_COMPLETED);
        when(reader.initialElementId()).thenReturn(FLOW_ELEMENT_ID);
        when(reader.key()).thenReturn(PROCESS_INSTANCE_ID);
        when(reader.processId()).thenReturn(PROCESS_ID);
        when(reader.processInstanceId()).thenReturn(PROCESS_INSTANCE_ID);

        return reader;
    }

    public static BpmnActivityEventReader mockActivityInstanceEvent(ExecutionEventType event)
    {
        final BpmnActivityEventReader reader = mock(BpmnActivityEventReader.class);

        when(reader.event()).thenReturn(event);
        when(reader.flowElementId()).thenReturn(FLOW_ELEMENT_ID);
        when(reader.key()).thenReturn(KEY);
        when(reader.wfDefinitionId()).thenReturn(PROCESS_ID);
        when(reader.wfInstanceId()).thenReturn(PROCESS_INSTANCE_ID);
        when(reader.resourceId()).thenReturn(WF_RUNTIME_LOG_ID);

        when(reader.taskQueueId()).thenReturn(TASK_QUEUE_ID);
        when(reader.getTaskType()).thenReturn(new UnsafeBuffer(TASK_TYPE));

        return reader;
    }

}
