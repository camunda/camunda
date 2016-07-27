package org.camunda.tngp.broker.wf.runtime.bpmn.handler;

import org.camunda.tngp.bpmn.graph.ProcessGraph;
import org.camunda.tngp.broker.wf.runtime.bpmn.event.BpmnProcessEventReader;
import org.camunda.tngp.graph.bpmn.BpmnAspect;
import org.camunda.tngp.log.LogWriter;
import org.camunda.tngp.log.idgenerator.IdGenerator;

public interface BpmnProcessEventHandler
{
    /**
     * @return see constants defined in {@link LogEntryHandler}
     */
    int handle(BpmnProcessEventReader processEventReader, ProcessGraph process, LogWriter logWriter, IdGenerator idGenerator);

    BpmnAspect getHandledBpmnAspect();
}
