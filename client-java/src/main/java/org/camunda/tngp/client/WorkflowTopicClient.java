package org.camunda.tngp.client;

import org.camunda.tngp.client.workflow.cmd.CreateWorkflowInstanceCmd;
import org.camunda.tngp.client.workflow.cmd.CreateDeploymentCmd;

public interface WorkflowTopicClient
{

    /**
     * Deploy new workflows.
     */
    CreateDeploymentCmd deploy();

    /**
     * Create new workflow.
     */
    CreateWorkflowInstanceCmd create();
}
