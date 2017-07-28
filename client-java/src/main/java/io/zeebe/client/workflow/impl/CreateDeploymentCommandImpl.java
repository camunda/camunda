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

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;

import io.zeebe.client.cmd.ClientException;
import io.zeebe.client.event.DeploymentEvent;
import io.zeebe.client.event.impl.EventImpl;
import io.zeebe.client.impl.RequestManager;
import io.zeebe.client.impl.cmd.CommandImpl;
import io.zeebe.client.workflow.cmd.CreateDeploymentCommand;
import io.zeebe.util.StreamUtil;

public class CreateDeploymentCommandImpl extends CommandImpl<DeploymentEvent> implements CreateDeploymentCommand
{
    protected final DeploymentEventImpl deploymentEvent = new DeploymentEventImpl(DeploymentEventType.CREATE_DEPLOYMENT.name());

    public CreateDeploymentCommandImpl(final RequestManager commandManager, String topic)
    {
        super(commandManager);
        this.deploymentEvent.setTopicName(topic);
    }

    @Override
    public CreateDeploymentCommand resourceBytes(final byte[] resource)
    {
        this.deploymentEvent.setBpmnXml(resource);
        return this;
    }

    @Override
    public CreateDeploymentCommand resourceString(final String resource, Charset charset)
    {
        return resourceBytes(resource.getBytes(charset));
    }

    @Override
    public CreateDeploymentCommand resourceStringUtf8(String resourceString)
    {
        return resourceString(resourceString, StandardCharsets.UTF_8);
    }

    @Override
    public CreateDeploymentCommand resourceStream(final InputStream resourceStream)
    {
        ensureNotNull("resource stream", resourceStream);

        try
        {
            final byte[] bytes = StreamUtil.read(resourceStream);

            return resourceBytes(bytes);
        }
        catch (final IOException e)
        {
            final String exceptionMsg = String.format("Cannot deploy bpmn resource from stream. %s", e.getMessage());
            throw new ClientException(exceptionMsg, e);
        }
    }

    @Override
    public CreateDeploymentCommand resourceFromClasspath(final String resourceName)
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
    public CreateDeploymentCommand resourceFile(final String filename)
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
    public CreateDeploymentCommand bpmnModelInstance(final BpmnModelInstance modelInstance)
    {
        ensureNotNull("model instance", modelInstance);

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        Bpmn.writeModelToStream(out, modelInstance);

        return resourceBytes(out.toByteArray());
    }

    @Override
    public EventImpl getEvent()
    {
        return deploymentEvent;
    }

    @Override
    public String getExpectedStatus()
    {
        return DeploymentEventType.DEPLOYMENT_CREATED.name();
    }

    @Override
    public String generateError(DeploymentEvent request, DeploymentEvent responseEvent)
    {
        return "Deployment was rejected: " + responseEvent.getErrorMessage();
    }

}
