package org.camunda.tngp.broker.wf.runtime.bpmn.handler;

import org.camunda.tngp.bpmn.graph.ProcessGraph;
import org.camunda.tngp.broker.wf.runtime.bpmn.event.BpmnActivityEventReader;
import org.camunda.tngp.broker.wf.runtime.bpmn.event.BpmnFlowElementEventReader;
import org.camunda.tngp.broker.wf.runtime.bpmn.event.BpmnProcessEventReader;
import org.camunda.tngp.broker.wf.runtime.bpmn.event.BpmnProcessEventWriter;
import org.camunda.tngp.graph.bpmn.BpmnAspect;
import org.camunda.tngp.graph.bpmn.ExecutionEventType;
import org.camunda.tngp.hashindex.Long2LongHashIndex;
import org.camunda.tngp.log.LogReader;
import org.camunda.tngp.log.LogWriter;
import org.camunda.tngp.log.idgenerator.IdGenerator;

public class EndProcessHandler implements BpmnFlowElementEventHandler, BpmnActivityInstanceEventHandler
{
    protected BpmnProcessEventWriter eventWriter = new BpmnProcessEventWriter();

    protected final Long2LongHashIndex workflowEventIndex;
    protected final LogReader logReader;
    protected BpmnProcessEventReader latestEventReader = new BpmnProcessEventReader();

    public EndProcessHandler(LogReader logReader, Long2LongHashIndex workflowEventIndex)
    {
        this.logReader = logReader;
        this.workflowEventIndex = workflowEventIndex;
    }

    @Override
    public void handle(BpmnFlowElementEventReader flowElementEventReader, ProcessGraph process, LogWriter logWriter, IdGenerator idGenerator)
    {
        writeProcessEndEvent(
            flowElementEventReader.wfInstanceId(),
            logWriter);
    }

    @Override
    public void handle(BpmnActivityEventReader activityEventReader, ProcessGraph process, LogWriter logWriter,
            IdGenerator idGenerator)
    {
        writeProcessEndEvent(
            activityEventReader.wfInstanceId(),
            logWriter);

    }

    protected void writeProcessEndEvent(long processInstanceId, LogWriter logWriter)
    {
        final long previousEventPosition = workflowEventIndex.get(processInstanceId, -1L);

        logReader.setPosition(previousEventPosition);
        logReader.read(latestEventReader);

        eventWriter
            .event(ExecutionEventType.PROC_INST_COMPLETED)
            .processId(latestEventReader.processId())
            .processInstanceId(processInstanceId)
            .initialElementId(latestEventReader.initialElementId())
            .key(latestEventReader.key());

        if (logWriter.write(eventWriter) < 0)
        {
            // TODO: throw exception/backpressure; could not write event
            System.err.println("Could not write process start event");
        }
    }

    @Override
    public BpmnAspect getHandledBpmnAspect()
    {
        return BpmnAspect.END_PROCESS;
    }

    public void setEventWriter(BpmnProcessEventWriter eventWriter)
    {
        this.eventWriter = eventWriter;
    }

    public void setLatestEventReader(BpmnProcessEventReader latestEventReader)
    {
        this.latestEventReader = latestEventReader;
    }
}
