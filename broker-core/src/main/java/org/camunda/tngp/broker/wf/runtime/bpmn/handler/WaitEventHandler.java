package org.camunda.tngp.broker.wf.runtime.bpmn.handler;

import org.camunda.tngp.bpmn.graph.ProcessGraph;
import org.camunda.tngp.broker.log.LogEntryHandler;
import org.camunda.tngp.broker.wf.runtime.bpmn.event.BpmnActivityEventReader;
import org.camunda.tngp.broker.wf.runtime.bpmn.event.BpmnFlowElementEventReader;
import org.camunda.tngp.broker.wf.runtime.bpmn.event.BpmnProcessEventReader;
import org.camunda.tngp.graph.bpmn.BpmnAspect;
import org.camunda.tngp.log.LogWriter;
import org.camunda.tngp.log.idgenerator.IdGenerator;

public class WaitEventHandler implements BpmnFlowElementEventHandler, BpmnProcessEventHandler, BpmnActivityInstanceEventHandler
{

    @Override
    public int handle(BpmnProcessEventReader processEventReader, ProcessGraph process, LogWriter logWriter,
            IdGenerator idGenerator)
    {
        // ignore
        return LogEntryHandler.CONSUME_ENTRY_RESULT;
    }

    @Override
    public int handle(BpmnFlowElementEventReader flowElementEventReader, ProcessGraph process, LogWriter logWriter,
            IdGenerator idGenerator)
    {
        // ignore
        return LogEntryHandler.CONSUME_ENTRY_RESULT;
    }

    @Override
    public int handle(BpmnActivityEventReader activityEventReader, ProcessGraph process, LogWriter logWriter,
            IdGenerator idGenerator)
    {
        // ignore
        return LogEntryHandler.CONSUME_ENTRY_RESULT;
    }

    @Override
    public BpmnAspect getHandledBpmnAspect()
    {
        return BpmnAspect.NULL_VAL;
    }

}
