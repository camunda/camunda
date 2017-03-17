package org.camunda.tngp.client;

import org.camunda.tngp.client.cmd.StartWorkflowInstanceCmd;
import org.camunda.tngp.client.workflow.cmd.CreateDeploymentCmd;

public interface WorkflowTopicClient
{

    /**
     * Deploy new workflows.
     */
    CreateDeploymentCmd deploy();

    StartWorkflowInstanceCmd start();
}
