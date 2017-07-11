/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.client.workflow.impl;

import static io.zeebe.util.EnsureUtil.ensureNotNull;

import java.io.*;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.zeebe.client.impl.ClientCommandManager;
import io.zeebe.client.impl.Topic;
import io.zeebe.client.impl.cmd.AbstractExecuteCmdImpl;
import io.zeebe.client.workflow.cmd.*;
import io.zeebe.protocol.clientapi.EventType;
import io.zeebe.util.StreamUtil;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;

public class CreateDeploymentCmdImpl extends AbstractExecuteCmdImpl<DeploymentEvent, DeploymentResult> implements CreateDeploymentCmd
{
    protected final DeploymentEvent deploymentEvent = new DeploymentEvent();

    protected String resource;

    public CreateDeploymentCmdImpl(final ClientCommandManager commandManager, final ObjectMapper objectMapper, final Topic topic)
    {
        super(commandManager, objectMapper, topic, DeploymentEvent.class, EventType.DEPLOYMENT_EVENT);
    }

    @Override
    public CreateDeploymentCmd resourceString(final String resource)
    {
        this.resource = resource;
        return this;
    }

    @Override
    public CreateDeploymentCmd resourceStream(final InputStream resourceStream)
    {
        ensureNotNull("resource stream", resourceStream);

        try
        {
            final byte[] bytes = StreamUtil.read(resourceStream);

            return resourceString(new String(bytes, CHARSET));
        }
        catch (final IOException e)
        {
            final String exceptionMsg = String.format("Cannot deploy bpmn resource from stream. %s", e.getMessage());
            throw new RuntimeException(exceptionMsg, e);
        }
    }

    @Override
    public CreateDeploymentCmd resourceFromClasspath(final String resourceName)
    {
        ensureNotNull("classpath resource", resourceName);

        try (InputStream resourceStream = getClass().getClassLoader().getResourceAsStream(resourceName))
        {
            if (resourceStream != null)
            {
                return resourceStream(resourceStream);
            }
            else
            {
                throw new FileNotFoundException(resourceName);
            }

        }
        catch (final IOException e)
        {
            final String exceptionMsg = String.format("Cannot deploy resource from classpath. %s", e.getMessage());
            throw new RuntimeException(exceptionMsg, e);
        }
    }

    @Override
    public CreateDeploymentCmd resourceFile(final String filename)
    {
        ensureNotNull("filename", filename);

        try (InputStream resourceStream = new FileInputStream(filename))
        {
            return resourceStream(resourceStream);
        }
        catch (final IOException e)
        {
            final String exceptionMsg = String.format("Cannot deploy resource from file. %s", e.getMessage());
            throw new RuntimeException(exceptionMsg, e);
        }
    }

    @Override
    public CreateDeploymentCmd bpmnModelInstance(final BpmnModelInstance modelInstance)
    {
        ensureNotNull("model instance", modelInstance);

        final String modelInstanceAsString = Bpmn.convertToString(modelInstance);

        return resourceString(modelInstanceAsString);
    }

    @Override
    public void validate()
    {
        super.validate();
        ensureNotNull("resource", resource);
    }

    @Override
    protected Object writeCommand()
    {
        deploymentEvent.setEventType(DeploymentEventType.CREATE_DEPLOYMENT);
        deploymentEvent.setBpmnXml(resource);

        return deploymentEvent;
    }

    @Override
    protected long getKey()
    {
        return -1L;
    }

    @Override
    protected void reset()
    {
        resource = null;

        deploymentEvent.reset();
    }

    @Override
    protected DeploymentResult getResponseValue(final long key, final DeploymentEvent event)
    {
        final boolean isDeployed = event.getEventType() == DeploymentEventType.DEPLOYMENT_CREATED;

        final DeploymentResultImpl result = new DeploymentResultImpl()
                .setIsDeployed(isDeployed)
                .setKey(key)
                .setErrorMessage(event.getErrorMessage());

        if (event.getDeployedWorkflows() != null)
        {
            final List<WorkflowDefinition> deployedWorkflows = event.getDeployedWorkflows().stream()
                    .map(wf -> new WorkflowDefinitionImpl()
                            .setBpmnProcessId(wf.getBpmnProcessId())
                            .setVersion(wf.getVersion()))
                    .collect(Collectors.toList());

            result.setDeployedWorkflows(deployedWorkflows);
        }

        return result;
    }

}
