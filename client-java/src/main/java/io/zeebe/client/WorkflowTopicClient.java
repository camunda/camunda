package io.zeebe.client;

import io.zeebe.client.workflow.cmd.CancelWorkflowInstanceCmd;
import io.zeebe.client.workflow.cmd.CreateDeploymentCmd;
import io.zeebe.client.workflow.cmd.CreateWorkflowInstanceCmd;
import io.zeebe.client.workflow.cmd.UpdatePayloadCmd;

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
