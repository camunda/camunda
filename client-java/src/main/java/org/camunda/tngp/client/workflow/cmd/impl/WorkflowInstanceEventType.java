package org.camunda.tngp.client.workflow.cmd.impl;

/**
 * Represents the workflow instance event types,
 * which are written by the broker.
 */
public enum WorkflowInstanceEventType
{
    CREATE_WORKFLOW_INSTANCE,
    WORKFLOW_INSTANCE_CREATED,
    WORKFLOW_INSTANCE_REJECTED,

    EVENT_OCCURRED;
}

