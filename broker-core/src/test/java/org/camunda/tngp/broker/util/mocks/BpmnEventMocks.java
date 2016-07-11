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
    public static DirectBuffer taskTypeBuffer = new UnsafeBuffer("foo".getBytes(StandardCharsets.UTF_8));

    public static void mockActivityInstanceEvent(BpmnEventReader bpmnEventReader, BpmnActivityEventReader activityEventReader, ExecutionEventType event)
    {
        when(activityEventReader.event()).thenReturn(event);
        when(activityEventReader.flowElementId()).thenReturn(1235);
        when(activityEventReader.key()).thenReturn(23456789L);
        when(activityEventReader.processId()).thenReturn(8888L);
        when(activityEventReader.processInstanceId()).thenReturn(9999L);

        when(activityEventReader.taskQueueId()).thenReturn(3);
        when(activityEventReader.resourceId()).thenReturn(6);
        when(activityEventReader.getTaskType()).thenReturn(taskTypeBuffer);

        if (bpmnEventReader != null)
        {
            when(bpmnEventReader.templateId()).thenReturn(BpmnActivityEventDecoder.TEMPLATE_ID);
            when(bpmnEventReader.activityEvent()).thenReturn(activityEventReader);
        }
    }

    public static void mockActivityInstanceEvent(BpmnActivityEventReader activityEventReader, ExecutionEventType event)
    {
        mockActivityInstanceEvent(null, activityEventReader, event);
    }

    public static void mockFlowElementEvent(BpmnEventReader bpmnEventReader, BpmnFlowElementEventReader flowElementEventReader)
    {
        when(flowElementEventReader.event()).thenReturn(ExecutionEventType.EVT_OCCURRED);
        when(flowElementEventReader.flowElementId()).thenReturn(1235);
        when(flowElementEventReader.key()).thenReturn(6778L);
        when(flowElementEventReader.processId()).thenReturn(8888L);
        when(flowElementEventReader.processInstanceId()).thenReturn(9999L);

        when(bpmnEventReader.templateId()).thenReturn(BpmnFlowElementEventDecoder.TEMPLATE_ID);
        when(bpmnEventReader.flowElementEvent()).thenReturn(flowElementEventReader);
    }

    public static void mockProcessEvent(BpmnEventReader bpmnEventReader, BpmnProcessEventReader processEventReader)
    {

        when(processEventReader.event()).thenReturn(ExecutionEventType.PROC_INST_CREATED);
        when(processEventReader.initialElementId()).thenReturn(1235);
        when(processEventReader.key()).thenReturn(6778L);
        when(processEventReader.processId()).thenReturn(8888L);
        when(processEventReader.processInstanceId()).thenReturn(9999L);

        when(bpmnEventReader.templateId()).thenReturn(BpmnProcessEventDecoder.TEMPLATE_ID);
        when(bpmnEventReader.processEvent()).thenReturn(processEventReader);
    }

}
