package org.camunda.tngp.broker.util.mocks;

import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;

import org.camunda.tngp.broker.wf.runtime.bpmn.event.BpmnActivityEventReader;
import org.camunda.tngp.broker.wf.runtime.bpmn.event.BpmnEventReader;
import org.camunda.tngp.broker.wf.runtime.bpmn.event.BpmnFlowElementEventReader;
import org.camunda.tngp.broker.wf.runtime.bpmn.event.BpmnProcessEventReader;
import org.camunda.tngp.graph.bpmn.ExecutionEventType;
import org.camunda.tngp.taskqueue.data.BpmnActivityEventDecoder;
import org.camunda.tngp.taskqueue.data.BpmnFlowElementEventDecoder;
import org.camunda.tngp.taskqueue.data.BpmnProcessEventDecoder;

import uk.co.real_logic.agrona.DirectBuffer;
import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

public class BpmnEventMocks
{
    public static final int FLOW_ELEMENT_ID = 1235;
    public static final long KEY = 23456789L;
    public static final long PROCESS_ID = 8888L;
    public static final long PROCESS_INSTANCE_ID = 9999L;
    public static final int TASK_QUEUE_ID = 3;
    public static final int RESOURCE_ID = 6;

    public static DirectBuffer taskTypeBuffer = new UnsafeBuffer("foo".getBytes(StandardCharsets.UTF_8));

    public static void mockActivityInstanceEvent(BpmnEventReader bpmnEventReader, BpmnActivityEventReader activityEventReader, ExecutionEventType event)
    {
        when(activityEventReader.event()).thenReturn(event);
        when(activityEventReader.flowElementId()).thenReturn(FLOW_ELEMENT_ID);
        when(activityEventReader.key()).thenReturn(KEY);
        when(activityEventReader.wfDefinitionId()).thenReturn(PROCESS_ID);
        when(activityEventReader.wfInstanceId()).thenReturn(PROCESS_INSTANCE_ID);

        when(activityEventReader.taskQueueId()).thenReturn(TASK_QUEUE_ID);
        when(activityEventReader.resourceId()).thenReturn(RESOURCE_ID);
        when(activityEventReader.getTaskType()).thenReturn(taskTypeBuffer);

        if (bpmnEventReader != null)
        {
            when(bpmnEventReader.templateId()).thenReturn(BpmnActivityEventDecoder.TEMPLATE_ID);
            when(bpmnEventReader.activityEvent()).thenReturn(activityEventReader);
            when(bpmnEventReader.isActivityEvent()).thenReturn(true);
            when(bpmnEventReader.isFlowElementEvent()).thenReturn(false);
            when(bpmnEventReader.isProcessEvent()).thenReturn(false);
        }
    }

    public static void mockActivityInstanceEvent(BpmnActivityEventReader activityEventReader, ExecutionEventType event)
    {
        mockActivityInstanceEvent(null, activityEventReader, event);
    }

    public static void mockFlowElementEvent(BpmnEventReader bpmnEventReader, BpmnFlowElementEventReader flowElementEventReader)
    {
        when(flowElementEventReader.event()).thenReturn(ExecutionEventType.EVT_OCCURRED);
        when(flowElementEventReader.flowElementId()).thenReturn(FLOW_ELEMENT_ID);
        when(flowElementEventReader.key()).thenReturn(KEY);
        when(flowElementEventReader.wfDefinitionId()).thenReturn(PROCESS_ID);
        when(flowElementEventReader.wfInstanceId()).thenReturn(PROCESS_INSTANCE_ID);

        if (bpmnEventReader != null)
        {
            when(bpmnEventReader.templateId()).thenReturn(BpmnFlowElementEventDecoder.TEMPLATE_ID);
            when(bpmnEventReader.flowElementEvent()).thenReturn(flowElementEventReader);
            when(bpmnEventReader.isActivityEvent()).thenReturn(false);
            when(bpmnEventReader.isFlowElementEvent()).thenReturn(true);
            when(bpmnEventReader.isProcessEvent()).thenReturn(false);
        }
    }

    public static void mockProcessEvent(BpmnEventReader bpmnEventReader, BpmnProcessEventReader processEventReader)
    {

        when(processEventReader.event()).thenReturn(ExecutionEventType.PROC_INST_CREATED);
        when(processEventReader.initialElementId()).thenReturn(FLOW_ELEMENT_ID);
        when(processEventReader.key()).thenReturn(KEY);
        when(processEventReader.processId()).thenReturn(PROCESS_ID);
        when(processEventReader.processInstanceId()).thenReturn(PROCESS_INSTANCE_ID);

        if (bpmnEventReader != null)
        {
            when(bpmnEventReader.templateId()).thenReturn(BpmnProcessEventDecoder.TEMPLATE_ID);
            when(bpmnEventReader.processEvent()).thenReturn(processEventReader);
            when(bpmnEventReader.isActivityEvent()).thenReturn(false);
            when(bpmnEventReader.isFlowElementEvent()).thenReturn(false);
            when(bpmnEventReader.isProcessEvent()).thenReturn(true);
        }
    }

}
