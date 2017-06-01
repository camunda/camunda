package org.camunda.tngp.client;

import org.camunda.tngp.client.workflow.cmd.CancelWorkflowInstanceCmd;
import org.camunda.tngp.client.workflow.cmd.CreateDeploymentCmd;
import org.camunda.tngp.client.workflow.cmd.CreateWorkflowInstanceCmd;
import org.camunda.tngp.client.workflow.cmd.UpdatePayloadCmd;

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

    /**
     * Update the payload of a workflow instance.
     */
    UpdatePayloadCmd updatePayload();
}
