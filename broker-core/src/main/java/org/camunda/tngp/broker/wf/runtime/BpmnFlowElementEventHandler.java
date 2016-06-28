package org.camunda.tngp.broker.wf.runtime;

import org.camunda.tngp.bpmn.graph.ProcessGraph;
import org.camunda.tngp.graph.bpmn.BpmnAspect;
import org.camunda.tngp.log.LogWriter;
import org.camunda.tngp.log.idgenerator.IdGenerator;

public interface BpmnFlowElementEventHandler
{

    void handle(BpmnFlowElementEventReader flowElementEventReader, ProcessGraph process, LogWriter logWriter, IdGenerator idGenerator);

    BpmnAspect getHandledBpmnAspect();
}
