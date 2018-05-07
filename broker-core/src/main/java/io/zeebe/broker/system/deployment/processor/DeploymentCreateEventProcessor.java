/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.system.deployment.processor;

import static io.zeebe.broker.workflow.data.DeploymentState.CREATED;
import static io.zeebe.broker.workflow.data.DeploymentState.REJECTED;
import static io.zeebe.util.buffer.BufferUtil.bufferAsString;
import static io.zeebe.util.buffer.BufferUtil.wrapString;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Iterator;

import io.zeebe.broker.logstreams.processor.*;
import io.zeebe.broker.system.deployment.data.*;
import io.zeebe.broker.workflow.data.*;
import io.zeebe.model.bpmn.BpmnModelApi;
import io.zeebe.model.bpmn.ValidationResult;
import io.zeebe.model.bpmn.instance.Workflow;
import io.zeebe.model.bpmn.instance.WorkflowDefinition;
import io.zeebe.msgpack.value.ValueArray;
import org.agrona.DirectBuffer;

public class DeploymentCreateEventProcessor implements TypedEventProcessor<DeploymentEvent>
{
    private final BpmnModelApi bpmn = new BpmnModelApi();

    private final LatestVersionByProcessIdAndTopicName workflowVersions;
    private final LastWorkflowKey lastWorkflowKey;

    private final TopicNames definedTopics;

    public DeploymentCreateEventProcessor(LatestVersionByProcessIdAndTopicName workflowVersions,
        LastWorkflowKey lastWorkflowKey,
        TopicNames definedTopicsSet)
    {
        this.workflowVersions = workflowVersions;
        this.lastWorkflowKey = lastWorkflowKey;
        this.definedTopics = definedTopicsSet;
    }

    @Override
    public void processEvent(TypedEvent<DeploymentEvent> event)
    {
        final DeploymentEvent deploymentEvent = event.getValue();
        final DirectBuffer topicName = deploymentEvent.getTopicName();

        boolean success = false;

        if (topicExists(topicName))
        {
            success = readAndValidateWorkflows(deploymentEvent);
        }
        else
        {
            final String name = bufferAsString(topicName);
            deploymentEvent.setErrorMessage("No topic found with name " + name);
        }

        deploymentEvent.setState(success ? CREATED : REJECTED);
    }

    @Override
    public long writeEvent(TypedEvent<DeploymentEvent> event, TypedStreamWriter writer)
    {
        return writer.writeFollowupEvent(event.getKey(),
            event.getValue(),
            (m) -> m.requestId(event.getMetadata().getRequestId())
                .requestStreamId(event.getMetadata().getRequestStreamId()));
    }

    @Override
    public void updateState(TypedEvent<DeploymentEvent> event)
    {
        final DeploymentEvent deploymentEvent = event.getValue();

        if (deploymentEvent.getState() == DeploymentState.CREATED)
        {
            final ValueArray<DeployedWorkflow> deployedWorkflows = deploymentEvent.deployedWorkflows();
            final DirectBuffer topicName = deploymentEvent.getTopicName();

            final Iterator<DeployedWorkflow> iterator = deployedWorkflows.iterator();

            while (iterator.hasNext())
            {
                final DeployedWorkflow deployedWorkflow = iterator.next();
                workflowVersions.setLatestVersion(topicName, deployedWorkflow.getBpmnProcessId(), deployedWorkflow.getVersion());
            }
        }
    }

    private boolean topicExists(DirectBuffer topicName)
    {
        return definedTopics.exists(topicName);
    }

    private boolean readAndValidateWorkflows(final DeploymentEvent deploymentEvent)
    {
        final DirectBuffer topicName = deploymentEvent.getTopicName();
        final StringBuilder validationErrors = new StringBuilder();

        boolean success = true;

        final Iterator<DeploymentResource> resourceIterator = deploymentEvent.resources().iterator();

        if (!resourceIterator.hasNext())
        {
            validationErrors.append("Deployment doesn't contain a resource to deploy.");

            success = false;
        }
        else
        {
            // TODO: only one resource is supported; turn resources into a property

            final DeploymentResource deploymentResource = resourceIterator.next();

            try
            {
                final WorkflowDefinition definition = readWorkflowDefinition(deploymentResource);
                final ValidationResult validationResult = bpmn.validate(definition);

                final boolean isValid = !validationResult.hasErrors();

                if (isValid)
                {
                    for (Workflow workflow : definition.getWorkflows())
                    {
                        if (workflow.isExecutable())
                        {
                            final DirectBuffer bpmnProcessId = workflow.getBpmnProcessId();

                            final int latestVersion = workflowVersions.getLatestVersion(topicName, bpmnProcessId, 0);

                            final long key = lastWorkflowKey.incrementAndGet();

                            deploymentEvent.deployedWorkflows().add()
                                .setBpmnProcessId(bpmnProcessId)
                                .setVersion(latestVersion + 1)
                                .setKey(key);
                        }
                    }

                    transformWorkflowResource(deploymentResource, definition);
                }

                if (validationResult.hasErrors() || validationResult.hasWarnings())
                {
                    validationErrors.append(String.format("Resource '%s':\n", bufferAsString(deploymentResource.getResourceName())));
                    validationErrors.append(validationResult.format());

                    success = !validationResult.hasErrors();
                }
            }
            catch (Exception e)
            {
                validationErrors.append(String.format("Failed to deploy resource '%s':\n", bufferAsString(deploymentResource.getResourceName())));
                validationErrors.append(generateErrorMessage(e));

                success = false;
            }
        }

        deploymentEvent.setErrorMessage(validationErrors.toString());

        return success;
    }

    private WorkflowDefinition readWorkflowDefinition(DeploymentResource deploymentResource)
    {
        final DirectBuffer resource = deploymentResource.getResource();

        switch (deploymentResource.getResourceType())
        {
            case BPMN_XML:
                return bpmn.readFromXmlBuffer(resource);

            case YAML_WORKFLOW:
                return bpmn.readFromYamlBuffer(resource);

            default:
                return bpmn.readFromXmlBuffer(resource);
        }
    }

    private boolean transformWorkflowResource(final DeploymentResource deploymentResource, final WorkflowDefinition definition)
    {
        if (deploymentResource.getResourceType() != ResourceType.BPMN_XML)
        {
            final DirectBuffer bpmnXml = wrapString(bpmn.convertToString(definition));
            deploymentResource.setResource(bpmnXml);

            return true;
        }
        return false;
    }

    private String generateErrorMessage(final Exception e)
    {
        final StringWriter stacktraceWriter = new StringWriter();

        e.printStackTrace(new PrintWriter(stacktraceWriter));

        return stacktraceWriter.toString();
    }
}
