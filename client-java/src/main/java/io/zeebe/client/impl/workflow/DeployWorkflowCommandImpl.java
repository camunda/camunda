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
package io.zeebe.client.impl.workflow;

import static io.zeebe.util.EnsureUtil.ensureNotNull;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import io.zeebe.client.api.commands.*;
import io.zeebe.client.api.commands.DeployWorkflowCommandStep1.DeployWorkflowCommandBuilderStep2;
import io.zeebe.client.api.events.DeploymentEvent;
import io.zeebe.client.cmd.ClientException;
import io.zeebe.client.impl.CommandImpl;
import io.zeebe.client.impl.RequestManager;
import io.zeebe.client.impl.command.DeploymentCommandImpl;
import io.zeebe.client.impl.command.DeploymentResourceImpl;
import io.zeebe.client.impl.record.RecordImpl;
import io.zeebe.model.bpmn.BpmnModelApi;
import io.zeebe.model.bpmn.instance.WorkflowDefinition;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.intent.DeploymentIntent;
import io.zeebe.util.StreamUtil;

public class DeployWorkflowCommandImpl extends CommandImpl<DeploymentEvent> implements DeployWorkflowCommandStep1, DeployWorkflowCommandBuilderStep2
{
    private final DeploymentCommandImpl command = new DeploymentCommandImpl(DeploymentIntent.CREATE);

    private final List<DeploymentResource> resources = new ArrayList<>();

    private final BpmnModelApi bpmn = new BpmnModelApi();

    public DeployWorkflowCommandImpl(final RequestManager commandManager, String topic)
    {
        super(commandManager);

        // send command always to the system topic
        this.command.setTopicName(Protocol.SYSTEM_TOPIC);
        // set the topic to deploy to
        this.command.setDeploymentTopic(topic);
    }

    @Override
    public DeployWorkflowCommandBuilderStep2 addResourceBytes(final byte[] resource, String resourceName)
    {
        final DeploymentResourceImpl deploymentResource = new DeploymentResourceImpl();

        deploymentResource.setResource(resource);
        deploymentResource.setResourceName(resourceName);
        deploymentResource.setResourceType(getResourceType(resourceName));

        resources.add(deploymentResource);

        return this;
    }

    @Override
    public DeployWorkflowCommandBuilderStep2 addResourceString(final String resource, Charset charset, String resourceName)
    {
        return addResourceBytes(resource.getBytes(charset), resourceName);
    }

    @Override
    public DeployWorkflowCommandBuilderStep2 addResourceStringUtf8(String resourceString, String resourceName)
    {
        return addResourceString(resourceString, StandardCharsets.UTF_8, resourceName);
    }

    @Override
    public DeployWorkflowCommandBuilderStep2 addResourceStream(final InputStream resourceStream, String resourceName)
    {
        ensureNotNull("resource stream", resourceStream);

        try
        {
            final byte[] bytes = StreamUtil.read(resourceStream);

            return addResourceBytes(bytes, resourceName);
        }
        catch (final IOException e)
        {
            final String exceptionMsg = String.format("Cannot deploy bpmn resource from stream. %s", e.getMessage());
            throw new ClientException(exceptionMsg, e);
        }
    }

    @Override
    public DeployWorkflowCommandBuilderStep2 addResourceFromClasspath(final String classpathResource)
    {
        ensureNotNull("classpath resource", classpathResource);

        try (InputStream resourceStream = getClass().getClassLoader().getResourceAsStream(classpathResource))
        {
            if (resourceStream != null)
            {
                return addResourceStream(resourceStream, classpathResource);
            }
            else
            {
                throw new FileNotFoundException(classpathResource);
            }

        }
        catch (final IOException e)
        {
            final String exceptionMsg = String.format("Cannot deploy resource from classpath. %s", e.getMessage());
            throw new RuntimeException(exceptionMsg, e);
        }
    }

    @Override
    public DeployWorkflowCommandBuilderStep2 addResourceFile(final String filename)
    {
        ensureNotNull("filename", filename);

        try (InputStream resourceStream = new FileInputStream(filename))
        {
            return addResourceStream(resourceStream, filename);
        }
        catch (final IOException e)
        {
            final String exceptionMsg = String.format("Cannot deploy resource from file. %s", e.getMessage());
            throw new RuntimeException(exceptionMsg, e);
        }
    }

    @Override
    public DeployWorkflowCommandBuilderStep2 addWorkflowModel(final WorkflowDefinition workflowDefinition, String resourceName)
    {
        ensureNotNull("workflow model", workflowDefinition);

        final String bpmnXml = bpmn.convertToString(workflowDefinition);
        return addResourceStringUtf8(bpmnXml, resourceName);
    }

    @Override
    public RecordImpl getCommand()
    {
        command.setResources(resources);
        return command;
    }

    private ResourceType getResourceType(String resourceName)
    {
        resourceName = resourceName.toLowerCase();

        if (resourceName.endsWith(".yaml"))
        {
            return ResourceType.YAML_WORKFLOW;
        }
        else if (resourceName.endsWith(".bpmn") || resourceName.endsWith(".bpmn20.xml"))
        {
            return ResourceType.BPMN_XML;
        }
        else
        {
            throw new RuntimeException(String.format("Cannot resolve type of resource '%s'.", resourceName));
        }
    }

}
