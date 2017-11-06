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

import static io.zeebe.broker.workflow.data.DeploymentState.DEPLOYMENT_REJECTED;
import static io.zeebe.broker.workflow.data.DeploymentState.DEPLOYMENT_VALIDATED;
import static io.zeebe.util.buffer.BufferUtil.bufferAsString;
import static io.zeebe.util.buffer.BufferUtil.wrapString;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import java.util.Iterator;
import java.util.function.Consumer;

import io.zeebe.broker.Loggers;
import io.zeebe.broker.logstreams.processor.*;
import io.zeebe.broker.system.deployment.data.*;
import io.zeebe.broker.system.deployment.data.PendingDeployments.PendingDeployment;
import io.zeebe.broker.system.deployment.data.PendingDeployments.PendingDeploymentIterator;
import io.zeebe.broker.system.deployment.data.TopicPartitions.TopicPartition;
import io.zeebe.broker.system.deployment.data.TopicPartitions.TopicPartitionIterator;
import io.zeebe.broker.workflow.data.*;
import io.zeebe.model.bpmn.BpmnModelApi;
import io.zeebe.model.bpmn.ValidationResult;
import io.zeebe.model.bpmn.instance.Workflow;
import io.zeebe.model.bpmn.instance.WorkflowDefinition;
import io.zeebe.msgpack.value.ValueArray;
import io.zeebe.protocol.impl.BrokerEventMetadata;
import io.zeebe.util.buffer.BufferUtil;
import io.zeebe.util.time.ClockUtil;
import org.agrona.DirectBuffer;
import org.slf4j.Logger;

public class DeploymentCreateProcessor implements TypedEventProcessor<DeploymentEvent>
{
    private static final Logger LOG = Loggers.SYSTEM_LOGGER;

    private final BpmnModelApi bpmn = new BpmnModelApi();

    private final WorkflowEvent workflowEvent = new WorkflowEvent();

    private final TopicPartitions topicPartitions;
    private final WorkflowVersions workflowVersions;
    private final PendingDeployments pendingDeployments;

    private final long timeoutInMillis;

    public DeploymentCreateProcessor(
            TopicPartitions topicPartitions,
            WorkflowVersions workflowVersions,
            PendingDeployments pendingDeployments,
            Duration deploymentTimeout)
    {
        this.topicPartitions = topicPartitions;
        this.workflowVersions = workflowVersions;
        this.pendingDeployments = pendingDeployments;
        this.timeoutInMillis = deploymentTimeout.toMillis();
    }

    @Override
    public void processEvent(TypedEvent<DeploymentEvent> event)
    {
        final DeploymentEvent deploymentEvent = event.getValue();
        final DirectBuffer topicName = deploymentEvent.getTopicName();

        boolean success = false;

        if (isTopicCreated(topicName))
        {
            if (hasPendingDeploymentForTopic(topicName))
            {
                // reject deployment if a previous deployment is not completed yet
                // -- otherwise, we could run into problems with the workflow versions when the previous deployment is rejected
                LOG.info("Cannot create deployment: pending deployment found for topic with name '{}'.", bufferAsString(topicName));
            }
            else
            {
                success = readAndValidateWorkflows(deploymentEvent);
            }
        }
        else
        {
            LOG.info("Cannot create deployment: no topic found with name '{}'.", bufferAsString(topicName));
        }

        deploymentEvent.setState(success ? DEPLOYMENT_VALIDATED : DEPLOYMENT_REJECTED);
    }

    private boolean isTopicCreated(final DirectBuffer topicName)
    {
        boolean hasPartition = false;
        boolean topicPartitionsCreated = true;

        final TopicPartitionIterator iterator = topicPartitions.iterator();
        while (iterator.hasNext())
        {
            final TopicPartition partition = iterator.next();

            if (BufferUtil.equals(topicName, partition.getTopicName()))
            {
                hasPartition = true;
                topicPartitionsCreated &= partition.getState() == TopicPartitions.STATE_CREATED;
            }
        }
        return hasPartition && topicPartitionsCreated;
    }

    private boolean hasPendingDeploymentForTopic(DirectBuffer topicName)
    {
        final PendingDeploymentIterator iterator = pendingDeployments.iterator();
        while (iterator.hasNext())
        {
            final PendingDeployment pendingDeployment = iterator.next();

            if (BufferUtil.equals(topicName, pendingDeployment.getTopicName()))
            {
                return true;
            }
        }
        return false;
    }

    private boolean readAndValidateWorkflows(final DeploymentEvent deploymentEvent)
    {
        boolean success = false;

        try
        {
            final WorkflowDefinition definition = readWorkflowDefinition(deploymentEvent);
            final ValidationResult validationResult = bpmn.validate(definition);

            success = !validationResult.hasErrors();
            if (success)
            {
                assignVersionToWorkflows(deploymentEvent, definition);

                transformWorkflowResource(deploymentEvent, definition);
            }

            if (validationResult.hasErrors() || validationResult.hasWarnings())
            {
                deploymentEvent.setErrorMessage(validationResult.format());
            }
        }
        catch (Exception e)
        {
            final String errorMessage = generateErrorMessage(e);
            deploymentEvent.setErrorMessage(errorMessage);
        }

        return success;
    }

    private WorkflowDefinition readWorkflowDefinition(DeploymentEvent deploymentEvent)
    {
        final DirectBuffer resource = deploymentEvent.getResource();

        switch (deploymentEvent.getResourceType())
        {
            case BPMN_XML:
                return bpmn.readFromXmlBuffer(resource);
            case YAML_WORKFLOW:
                return bpmn.readFromYamlBuffer(resource);
            default:
                return bpmn.readFromXmlBuffer(resource);
        }
    }

    private void transformWorkflowResource(final DeploymentEvent deploymentEvent, final WorkflowDefinition definition)
    {
        if (deploymentEvent.getResourceType() != ResourceType.BPMN_XML)
        {
            final DirectBuffer bpmnXml = wrapString(bpmn.convertToString(definition));
            deploymentEvent.setResource(bpmnXml);
        }
    }

    private void assignVersionToWorkflows(DeploymentEvent deploymentEvent, final WorkflowDefinition definition)
    {
        final DirectBuffer topicName = deploymentEvent.getTopicName();
        final ValueArray<DeployedWorkflow> deployedWorkflows = deploymentEvent.deployedWorkflows();

        for (Workflow workflow : definition.getWorkflows())
        {
            if (workflow.isExecutable())
            {
                final DirectBuffer bpmnProcessId = workflow.getBpmnProcessId();

                final int latestVersion = workflowVersions.getLatestVersion(topicName, bpmnProcessId, 0);

                deployedWorkflows.add()
                    .setBpmnProcessId(bpmnProcessId)
                    .setVersion(latestVersion + 1);
            }
        }
    }

    private String generateErrorMessage(final Exception e)
    {
        final StringWriter stacktraceWriter = new StringWriter();

        e.printStackTrace(new PrintWriter(stacktraceWriter));

        return String.format("Failed to deploy BPMN model: %s", stacktraceWriter);
    }

    @Override
    public boolean executeSideEffects(TypedEvent<DeploymentEvent> event, TypedResponseWriter responseWriter)
    {
        final DeploymentEvent deploymentEvent = event.getValue();

        if (deploymentEvent.getState() == DEPLOYMENT_REJECTED)
        {
            return responseWriter.write(event);
        }
        else
        {
            return true;
        }
    }

    @Override
    public long writeEvent(TypedEvent<DeploymentEvent> event, TypedStreamWriter writer)
    {
        final DeploymentEvent deploymentEvent = event.getValue();

        if (deploymentEvent.getState() == DEPLOYMENT_REJECTED)
        {
            return writer.writeFollowupEvent(event.getKey(), deploymentEvent);
        }
        else
        {
            final TypedBatchWriter batch = writer.newBatch();

            batch.addFollowUpEvent(event.getKey(), deploymentEvent, addRequestMetadata(event));

            final Iterator<DeployedWorkflow> deployedWorkflows = deploymentEvent.deployedWorkflows().iterator();
            while (deployedWorkflows.hasNext())
            {
                final DeployedWorkflow deployedWorkflow = deployedWorkflows.next();

                workflowEvent
                    .setState(WorkflowState.CREATE)
                    .setBpmnProcessId(deployedWorkflow.getBpmnProcessId())
                    .setVersion(deployedWorkflow.getVersion())
                    .setBpmnXml(deploymentEvent.getResource())
                    .setDeploymentKey(event.getKey());

                batch.addNewEvent(workflowEvent);
            }

            return batch.write();
        }
    }

    private Consumer<BrokerEventMetadata> addRequestMetadata(TypedEvent<DeploymentEvent> event)
    {
        final BrokerEventMetadata metadata = event.getMetadata();
        return m -> m
                .requestId(metadata.getRequestId())
                .requestStreamId(metadata.getRequestStreamId());
    }

    @Override
    public void updateState(TypedEvent<DeploymentEvent> event)
    {
        final DeploymentEvent deploymentEvent = event.getValue();

        if (deploymentEvent.getState() == DeploymentState.DEPLOYMENT_VALIDATED)
        {
            updateWorkflowVersions(deploymentEvent.getTopicName(), deploymentEvent.deployedWorkflows());

            final long timeout = ClockUtil.getCurrentTimeInMillis() + timeoutInMillis;

            pendingDeployments.put(event.getKey(), -1L, timeout, deploymentEvent.getTopicName());
        }
    }

    private void updateWorkflowVersions(final DirectBuffer topicName, final ValueArray<DeployedWorkflow> deployedWorkflows)
    {
        final Iterator<DeployedWorkflow> iterator = deployedWorkflows.iterator();
        while (iterator.hasNext())
        {
            final DeployedWorkflow deployedWorkflow = iterator.next();

            workflowVersions.setLatestVersion(topicName, deployedWorkflow.getBpmnProcessId(), deployedWorkflow.getVersion());
        }
    }

}
