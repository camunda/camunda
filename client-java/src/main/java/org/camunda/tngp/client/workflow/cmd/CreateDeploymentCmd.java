package org.camunda.tngp.client.workflow.cmd;

import java.io.InputStream;

import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.tngp.client.cmd.ClientCommand;

public interface CreateDeploymentCmd extends ClientCommand<DeploymentResult>
{
    /**
     * Add the given workflow XML to the deployment.
     */
    CreateDeploymentCmd resourceString(String resourceString);

    /**
     * Add the given workflow stream to the deployment.
     */
    CreateDeploymentCmd resourceStream(InputStream resourceStream);

    /**
     * Add the given workflow classpath resource to the deployment.
     */
    CreateDeploymentCmd resourceFromClasspath(String classpathResource);

    /**
     * Add the given workflow file to the deployment.
     */
    CreateDeploymentCmd resourceFile(String filename);

    /**
     * Add the given workflow model instance to the deployment.
     */
    CreateDeploymentCmd bpmnModelInstance(BpmnModelInstance modelInstance);
}
