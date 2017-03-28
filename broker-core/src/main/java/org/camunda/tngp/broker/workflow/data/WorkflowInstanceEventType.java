package org.camunda.tngp.broker.workflow.data;

public enum WorkflowInstanceEventType
{
    CREATE_WORKFLOW_INSTANCE,
    WORKFLOW_INSTANCE_CREATED,
    WORKFLOW_INSTANCE_REJECTED,

    EVENT_OCCURRED,
    SEQUENCE_FLOW_TAKEN;
}
