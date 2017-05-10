package org.camunda.tngp.client;

import org.camunda.tngp.client.workflow.cmd.CancelWorkflowInstanceCmd;
import org.camunda.tngp.client.workflow.cmd.CreateDeploymentCmd;
import org.camunda.tngp.client.workflow.cmd.CreateWorkflowInstanceCmd;

public interface WorkflowTopicClient
{

    /**
     * Deploy new workflow definitions.
     */
    CreateDeploymentCmd deploy();

    /**
     * Create new workflow instance.
     */
    CreateWorkflowInstanceCmd create();

    /**
     * Cancel a workflow instance.
     */
    CancelWorkflowInstanceCmd cancel();
}
