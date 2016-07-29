package org.camunda.tngp.broker.wf.runtime.bpmn.handler;

import org.camunda.tngp.broker.taskqueue.log.TaskInstanceReader;
import org.camunda.tngp.broker.wf.runtime.bpmn.event.BpmnActivityEventReader;
import org.camunda.tngp.broker.wf.runtime.bpmn.event.BpmnActivityEventWriter;
import org.camunda.tngp.graph.bpmn.ExecutionEventType;
import org.camunda.tngp.hashindex.Long2LongHashIndex;
import org.camunda.tngp.log.LogReader;
import org.camunda.tngp.log.LogWriter;

import uk.co.real_logic.agrona.DirectBuffer;

public class TaskEventHandler
{
    protected final LogWriter logWriter;
    protected final LogReader logReader;
    protected final Long2LongHashIndex workflowEventIndex;

    protected BpmnActivityEventReader latestEventReader = new BpmnActivityEventReader();
    protected BpmnActivityEventWriter eventWriter = new BpmnActivityEventWriter();

    public TaskEventHandler(LogReader bpmnEventReader, LogWriter bpmnEventWriter, Long2LongHashIndex workflowEventIndex)
    {
        this.logWriter = bpmnEventWriter;
        this.logReader = bpmnEventReader;
        this.workflowEventIndex = workflowEventIndex;
    }

    public void onComplete(TaskInstanceReader taskInstance)
    {
        final long activityInstanceId = taskInstance.wfActivityInstanceEventKey();
        final long latestPosition = workflowEventIndex.get(activityInstanceId, -1);

        logReader.setPosition(latestPosition);
        logReader.read(latestEventReader);

        eventWriter
            .eventType(ExecutionEventType.ACT_INST_COMPLETED)
            .flowElementId(latestEventReader.flowElementId())
            .key(taskInstance.wfActivityInstanceEventKey())
            .wfDefinitionId(latestEventReader.wfDefinitionId())
            .wfInstanceId(latestEventReader.wfInstanceId())
            .taskQueueId(latestEventReader.taskQueueId());

        final DirectBuffer taskType = latestEventReader.getTaskType();

        eventWriter
            .taskType(taskType, 0, taskType.capacity());

        logWriter.write(eventWriter);
    }

    public void setLatestEventReader(BpmnActivityEventReader latestEventReader)
    {
        this.latestEventReader = latestEventReader;
    }

    public void setEventWriter(BpmnActivityEventWriter eventWriter)
    {
        this.eventWriter = eventWriter;
    }

}
