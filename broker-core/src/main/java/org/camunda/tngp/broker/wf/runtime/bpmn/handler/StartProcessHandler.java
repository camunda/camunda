package org.camunda.tngp.broker.wf.runtime.bpmn.handler;

import org.camunda.tngp.bpmn.graph.ProcessGraph;
import org.camunda.tngp.broker.log.LogEntryHandler;
import org.camunda.tngp.broker.wf.runtime.bpmn.event.BpmnFlowElementEventReader;
import org.camunda.tngp.broker.wf.runtime.bpmn.event.BpmnProcessEventWriter;
import org.camunda.tngp.graph.bpmn.BpmnAspect;
import org.camunda.tngp.graph.bpmn.ExecutionEventType;
import org.camunda.tngp.log.LogWriter;
import org.camunda.tngp.log.idgenerator.IdGenerator;

public class StartProcessHandler implements BpmnFlowElementEventHandler
{

    protected BpmnProcessEventWriter eventWriter = new BpmnProcessEventWriter();

    @Override
    public int handle(BpmnFlowElementEventReader flowElementEventReader, ProcessGraph process, LogWriter logWriter, IdGenerator idGenerator)
    {

        eventWriter
            .event(ExecutionEventType.PROC_INST_CREATED)
            .processId(flowElementEventReader.wfDefinitionId())
            .processInstanceId(flowElementEventReader.wfInstanceId())
            .initialElementId(flowElementEventReader.flowElementId())
            .key(flowElementEventReader.wfInstanceId());

        if (logWriter.write(eventWriter) >= 0)
        {
            return LogEntryHandler.CONSUME_ENTRY_RESULT;
        }
        else
        {
            System.err.println("Could not write process start event");
            return LogEntryHandler.POSTPONE_ENTRY_RESULT;
        }

    }

    @Override
    public BpmnAspect getHandledBpmnAspect()
    {
        return BpmnAspect.START_PROCESS;
    }

    public void setEventWriter(BpmnProcessEventWriter eventWriter)
    {
        this.eventWriter = eventWriter;
    }

}
