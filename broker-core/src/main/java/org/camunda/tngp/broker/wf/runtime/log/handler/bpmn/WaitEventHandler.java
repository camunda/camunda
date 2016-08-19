package org.camunda.tngp.broker.wf.runtime.log.handler.bpmn;

import org.camunda.tngp.bpmn.graph.ProcessGraph;
import org.camunda.tngp.broker.log.LogEntryHandler;
import org.camunda.tngp.broker.log.LogWriters;
import org.camunda.tngp.broker.wf.runtime.log.bpmn.BpmnActivityEventReader;
import org.camunda.tngp.broker.wf.runtime.log.bpmn.BpmnFlowElementEventReader;
import org.camunda.tngp.broker.wf.runtime.log.bpmn.BpmnProcessEventReader;
import org.camunda.tngp.graph.bpmn.BpmnAspect;
import org.camunda.tngp.log.idgenerator.IdGenerator;

public class WaitEventHandler implements BpmnFlowElementAspectHandler, BpmnProcessAspectHandler, BpmnActivityInstanceAspectHandler
{

    @Override
    public int handle(BpmnProcessEventReader processEventReader, ProcessGraph process, LogWriters logWriters,
            IdGenerator idGenerator)
    {
        // ignore
        return LogEntryHandler.CONSUME_ENTRY_RESULT;
    }

    @Override
    public int handle(BpmnFlowElementEventReader flowElementEventReader, ProcessGraph process, LogWriters logWriters,
            IdGenerator idGenerator)
    {
        // ignore
        return LogEntryHandler.CONSUME_ENTRY_RESULT;
    }

    @Override
    public int handle(BpmnActivityEventReader activityEventReader, ProcessGraph process, LogWriters logWriters,
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
