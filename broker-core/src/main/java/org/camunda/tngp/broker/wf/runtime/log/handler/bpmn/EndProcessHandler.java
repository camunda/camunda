package org.camunda.tngp.broker.wf.runtime.log.handler.bpmn;

import org.camunda.tngp.bpmn.graph.ProcessGraph;
import org.camunda.tngp.broker.log.LogEntryHandler;
import org.camunda.tngp.broker.log.LogWriters;
import org.camunda.tngp.broker.wf.runtime.log.bpmn.BpmnActivityEventReader;
import org.camunda.tngp.broker.wf.runtime.log.bpmn.BpmnFlowElementEventReader;
import org.camunda.tngp.broker.wf.runtime.log.bpmn.BpmnProcessEventReader;
import org.camunda.tngp.broker.wf.runtime.log.bpmn.BpmnProcessEventWriter;
import org.camunda.tngp.graph.bpmn.BpmnAspect;
import org.camunda.tngp.graph.bpmn.ExecutionEventType;
import org.camunda.tngp.hashindex.Long2LongHashIndex;
import org.camunda.tngp.log.LogReader;
import org.camunda.tngp.log.idgenerator.IdGenerator;

public class EndProcessHandler implements BpmnFlowElementAspectHandler, BpmnActivityInstanceAspectHandler
{
    protected final BpmnProcessEventWriter eventWriter = new BpmnProcessEventWriter();

    protected final Long2LongHashIndex workflowEventIndex;
    protected final LogReader logReader;
    protected final BpmnProcessEventReader latestEventReader = new BpmnProcessEventReader();

    public EndProcessHandler(LogReader logReader, Long2LongHashIndex workflowEventIndex)
    {
        this.logReader = logReader;
        this.workflowEventIndex = workflowEventIndex;
    }

    @Override
    public int handle(BpmnFlowElementEventReader flowElementEventReader, ProcessGraph process, LogWriters logWriters, IdGenerator idGenerator)
    {
        return writeProcessEndEvent(
            flowElementEventReader.wfInstanceId(),
            flowElementEventReader.bpmnBranchKey(),
            logWriters);
    }

    @Override
    public int handle(BpmnActivityEventReader activityEventReader, ProcessGraph process, LogWriters logWriters,
            IdGenerator idGenerator)
    {
        return writeProcessEndEvent(
            activityEventReader.wfInstanceId(),
            activityEventReader.bpmnBranchKey(),
            logWriters);

    }

    protected int writeProcessEndEvent(long processInstanceId, long bpmnBranchKey, LogWriters logWriters)
    {
        final long previousEventPosition = workflowEventIndex.get(processInstanceId, -1L);

        logReader.seek(previousEventPosition);
        logReader.next()
            .readValue(latestEventReader);

        eventWriter
            .event(ExecutionEventType.PROC_INST_COMPLETED)
            .processId(latestEventReader.processId())
            .processInstanceId(processInstanceId)
            .initialElementId(latestEventReader.initialElementId())
            .bpmnBranchKey(bpmnBranchKey)
            .key(latestEventReader.key());

        logWriters.writeToCurrentLog(eventWriter);
        return LogEntryHandler.CONSUME_ENTRY_RESULT;
    }

    @Override
    public BpmnAspect getHandledBpmnAspect()
    {
        return BpmnAspect.END_PROCESS;
    }
}
