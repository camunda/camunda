package org.camunda.tngp.client;

import org.camunda.tngp.client.cmd.DeployBpmnResourceCmd;
import org.camunda.tngp.client.cmd.StartWorkflowInstanceCmd;

public interface WorkflowTopicClient
{

    DeployBpmnResourceCmd deploy();

    StartWorkflowInstanceCmd start();
}
