package org.camunda.tngp.broker.wf.runtime;

import org.camunda.tngp.bpmn.graph.ProcessGraph;
import org.camunda.tngp.graph.bpmn.BpmnAspect;
import org.camunda.tngp.graph.bpmn.ExecutionEventType;
import org.camunda.tngp.log.Log;
import org.camunda.tngp.log.LogEntryWriter;

public class StartProcessHandler implements BpmnFlowElementEventHandler
{

    protected final BpmnProcessEventWriter eventWriter = new BpmnProcessEventWriter();
    protected final LogEntryWriter logEntryWriter = new LogEntryWriter();

    @Override
    public void handle(BpmnFlowElementEventReader flowElementEventReader, ProcessGraph process, Log log)
    {

        eventWriter
          .event(ExecutionEventType.PROC_INST_CREATED)
          .processId(flowElementEventReader.processId())
          .processInstanceId(flowElementEventReader.processInstanceId())
          .initialElementId(flowElementEventReader.flowElementId())
          .key(flowElementEventReader.processInstanceId());

        if (logEntryWriter.write(log, eventWriter) < 0)
        {
            // TODO: throw exception; could not write event
        }

    }

    @Override
    public BpmnAspect getHandledBpmnAspect()
    {
        return BpmnAspect.START_PROCESS;
    }

}
