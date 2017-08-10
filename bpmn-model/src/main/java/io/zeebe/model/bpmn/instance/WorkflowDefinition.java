package io.zeebe.model.bpmn.instance;

import java.util.Collection;

import org.agrona.DirectBuffer;

public interface WorkflowDefinition
{

    Workflow getWorklow(DirectBuffer bpmnProcessId);

    Collection<Workflow> getWorkflows();

}
