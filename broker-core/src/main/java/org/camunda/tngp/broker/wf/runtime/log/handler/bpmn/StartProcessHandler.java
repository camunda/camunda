package org.camunda.tngp.broker.wf.runtime.log.handler.bpmn;

import org.camunda.tngp.bpmn.graph.ProcessGraph;
import org.camunda.tngp.broker.log.LogEntryHandler;
import org.camunda.tngp.broker.log.LogWriters;
import org.camunda.tngp.broker.wf.runtime.log.bpmn.BpmnFlowElementEventReader;
import org.camunda.tngp.broker.wf.runtime.log.bpmn.BpmnProcessEventWriter;
import org.camunda.tngp.graph.bpmn.BpmnAspect;
import org.camunda.tngp.graph.bpmn.ExecutionEventType;
import org.camunda.tngp.log.idgenerator.IdGenerator;

public class StartProcessHandler implements BpmnFlowElementAspectHandler
{

    protected BpmnProcessEventWriter eventWriter = new BpmnProcessEventWriter();

    @Override
    public int handle(BpmnFlowElementEventReader flowElementEventReader, ProcessGraph process, LogWriters logWriters, IdGenerator idGenerator)
    {

        eventWriter
            .event(ExecutionEventType.PROC_INST_CREATED)
            .processId(flowElementEventReader.wfDefinitionId())
            .processInstanceId(flowElementEventReader.wfInstanceId())
            .initialElementId(flowElementEventReader.flowElementId())
            .key(flowElementEventReader.wfInstanceId());

        logWriters.writeToCurrentLog(eventWriter);

        return LogEntryHandler.CONSUME_ENTRY_RESULT;

    }

    @Override
    public BpmnAspect getHandledBpmnAspect()
    {
        return BpmnAspect.START_PROCESS;
    }

}
