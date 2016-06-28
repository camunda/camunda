package org.camunda.tngp.broker.wf.runtime;

import org.camunda.tngp.bpmn.graph.ProcessGraph;
import org.camunda.tngp.graph.bpmn.BpmnAspect;
import org.camunda.tngp.graph.bpmn.ExecutionEventType;
import org.camunda.tngp.log.LogWriter;
import org.camunda.tngp.log.idgenerator.IdGenerator;

public class StartProcessHandler implements BpmnFlowElementEventHandler
{

    protected BpmnProcessEventWriter eventWriter = new BpmnProcessEventWriter();

    @Override
    public void handle(BpmnFlowElementEventReader flowElementEventReader, ProcessGraph process, LogWriter logWriter, IdGenerator idGenerator)
    {

        eventWriter
            .event(ExecutionEventType.PROC_INST_CREATED)
            .processId(flowElementEventReader.processId())
            .processInstanceId(flowElementEventReader.processInstanceId())
            .initialElementId(flowElementEventReader.flowElementId())
            .key(flowElementEventReader.processInstanceId());

        if (logWriter.write(eventWriter) < 0)
        {
            // TODO: throw exception/backpressure; could not write event
            System.err.println("Could not write process start event");
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
