package org.camunda.tngp.broker.wf.runtime.log.handler.bpmn;

import org.camunda.tngp.bpmn.graph.ProcessGraph;
import org.camunda.tngp.broker.log.LogEntryHandler;
import org.camunda.tngp.broker.log.LogWriters;
import org.camunda.tngp.broker.wf.runtime.log.bpmn.BpmnActivityEventReader;
import org.camunda.tngp.graph.bpmn.BpmnAspect;
import org.camunda.tngp.log.idgenerator.IdGenerator;

public interface BpmnActivityInstanceAspectHandler
{
    /**
     * @return see constants defined in {@link LogEntryHandler}
     */
    int handle(BpmnActivityEventReader activityEventReader, ProcessGraph process, LogWriters logWriters, IdGenerator idGenerator);

    BpmnAspect getHandledBpmnAspect();
}
