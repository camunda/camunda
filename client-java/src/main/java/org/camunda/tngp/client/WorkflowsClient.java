package org.camunda.tngp.client;

import org.camunda.tngp.client.cmd.DeployBpmnResourceCmd;
import org.camunda.tngp.client.cmd.StartWorkflowInstanceCmd;

public interface WorkflowsClient
{

    DeployBpmnResourceCmd deploy();

    StartWorkflowInstanceCmd start();
}
