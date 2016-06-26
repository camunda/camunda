package org.camunda.tngp.client.cmd;

import java.io.InputStream;

import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.tngp.client.ClientCommand;

public interface DeployBpmnResourceCmd extends ClientCommand<DeployedWorkflowType>
{

    DeployBpmnResourceCmd resourceBytes(byte[] resourceBytes);

    DeployBpmnResourceCmd resourceStream(InputStream resourceBytes);

    DeployBpmnResourceCmd resourceFromClasspath(String resourceName);

    DeployBpmnResourceCmd resourceFile(String filename);

    DeployBpmnResourceCmd bpmnModelInstance(BpmnModelInstance modelInstance);
}
