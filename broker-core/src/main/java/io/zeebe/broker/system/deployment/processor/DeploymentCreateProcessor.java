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

import static io.zeebe.broker.workflow.data.DeploymentState.REJECTED;
import static io.zeebe.broker.workflow.data.DeploymentState.VALIDATED;
import static io.zeebe.util.buffer.BufferUtil.bufferAsString;
import static io.zeebe.util.buffer.BufferUtil.wrapString;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Iterator;
import java.util.function.Consumer;

import org.agrona.DirectBuffer;
import org.agrona.collections.IntArrayList;
import org.slf4j.Logger;

import io.zeebe.broker.Loggers;
import io.zeebe.broker.logstreams.processor.TypedBatchWriter;
import io.zeebe.broker.logstreams.processor.TypedEvent;
import io.zeebe.broker.logstreams.processor.TypedEventProcessor;
import io.zeebe.broker.logstreams.processor.TypedResponseWriter;
import io.zeebe.broker.logstreams.processor.TypedStreamWriter;
import io.zeebe.broker.system.deployment.data.PendingDeployments;
import io.zeebe.broker.system.deployment.data.PendingDeployments.PendingDeployment;
import io.zeebe.broker.system.deployment.data.PendingDeployments.PendingDeploymentIterator;
import io.zeebe.broker.system.deployment.data.TopicPartitions;
import io.zeebe.broker.system.deployment.data.TopicPartitions.TopicPartition;
import io.zeebe.broker.system.deployment.data.TopicPartitions.TopicPartitionIterator;
import io.zeebe.broker.system.deployment.data.WorkflowVersions;
import io.zeebe.broker.workflow.data.DeployedWorkflow;
import io.zeebe.broker.workflow.data.DeploymentEvent;
import io.zeebe.broker.workflow.data.DeploymentResource;
import io.zeebe.broker.workflow.data.DeploymentState;
import io.zeebe.broker.workflow.data.ResourceType;
import io.zeebe.broker.workflow.data.WorkflowEvent;
import io.zeebe.broker.workflow.data.WorkflowState;
import io.zeebe.model.bpmn.BpmnModelApi;
import io.zeebe.model.bpmn.ValidationResult;
import io.zeebe.model.bpmn.instance.Workflow;
import io.zeebe.model.bpmn.instance.WorkflowDefinition;
import io.zeebe.msgpack.value.ValueArray;
import io.zeebe.protocol.impl.BrokerEventMetadata;
import io.zeebe.util.buffer.BufferUtil;
import io.zeebe.util.collection.IntArrayListIterator;

public class DeploymentCreateProcessor implements TypedEventProcessor<DeploymentEvent>
{
    private static final Logger LOG = Loggers.SYSTEM_LOGGER;

    private final BpmnModelApi bpmn = new BpmnModelApi();

    private final WorkflowEvent workflowEvent = new WorkflowEvent();

    private final TopicPartitions topicPartitions;
    private final WorkflowVersions workflowVersions;
    private final PendingDeployments pendingDeployments;

    private final DeploymentResourceIterator deploymentResourceIterator = new DeploymentResourceIterator();

    public DeploymentCreateProcessor(
            TopicPartitions topicPartitions,
            WorkflowVersions workflowVersions,
            PendingDeployments pendingDeployments)
    {
        this.topicPartitions = topicPartitions;
        this.workflowVersions = workflowVersions;
        this.pendingDeployments = pendingDeployments;
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
                final String name = bufferAsString(topicName);
                // reject deployment if a previous deployment is not completed yet
                // -- otherwise, we could run into problems with the workflow versions when the previous deployment is rejected
                LOG.info("Cannot create deployment: pending deployment found for topic with name '{}'.", name);
                deploymentEvent.setErrorMessage("Pending deployment found for topic with name " + name);
            }
            else
            {
                success = readAndValidateWorkflows(deploymentEvent);
            }
        }
        else
        {
            final String name = bufferAsString(topicName);
            LOG.info("Cannot create deployment: no topic found with name '{}'.", name);
            deploymentEvent.setErrorMessage("No topic found with name " + name);
        }

        deploymentEvent.setState(success ? VALIDATED : REJECTED);
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
        final DirectBuffer topicName = deploymentEvent.getTopicName();
        final StringBuilder validationErrors = new StringBuilder();

        boolean success = true;

        deploymentResourceIterator.wrap(deploymentEvent);

        if (!deploymentResourceIterator.hasNext())
        {
            validationErrors.append("Deployment doesn't contain a resource to deploy.");

            success = false;
        }

        while (deploymentResourceIterator.hasNext())
        {
            final DeploymentResource deploymentResource = deploymentResourceIterator.next();

            try
            {
                success &= readAndValidateWorkflowsOfResource(deploymentResource, topicName, validationErrors);

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

    private boolean readAndValidateWorkflowsOfResource(
            final DeploymentResource deploymentResource,
            final DirectBuffer topicName,
            final StringBuilder validationErrors)
    {
        final WorkflowDefinition definition = readWorkflowDefinition(deploymentResource);
        final ValidationResult validationResult = bpmn.validate(definition);

        final boolean isValid = !validationResult.hasErrors();

        if (isValid)
        {
            assignVersionToWorkflows(deploymentResourceIterator, topicName, definition);

            transformWorkflowResource(deploymentResource, definition);
        }

        if (validationResult.hasErrors() || validationResult.hasWarnings())
        {
            validationErrors.append(String.format("Resource '%s':\n", bufferAsString(deploymentResource.getResourceName())));
            validationErrors.append(validationResult.format());
        }

        return isValid;
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

    private void assignVersionToWorkflows(final DeploymentResourceIterator resourceIterator, final DirectBuffer topicName, final WorkflowDefinition definition)
    {
        for (Workflow workflow : definition.getWorkflows())
        {
            if (workflow.isExecutable())
            {
                final DirectBuffer bpmnProcessId = workflow.getBpmnProcessId();

                final int latestVersion = workflowVersions.getLatestVersion(topicName, bpmnProcessId, 0);

                resourceIterator.addDeployedWorkflow()
                    .setBpmnProcessId(bpmnProcessId)
                    .setVersion(latestVersion + 1);
            }
        }
    }

    private String generateErrorMessage(final Exception e)
    {
        final StringWriter stacktraceWriter = new StringWriter();

        e.printStackTrace(new PrintWriter(stacktraceWriter));

        return stacktraceWriter.toString();
    }

    @Override
    public boolean executeSideEffects(TypedEvent<DeploymentEvent> event, TypedResponseWriter responseWriter)
    {
        final DeploymentEvent deploymentEvent = event.getValue();

        if (deploymentEvent.getState() == REJECTED)
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

        if (deploymentEvent.getState() == REJECTED)
        {
            return writer.writeFollowupEvent(event.getKey(), deploymentEvent);
        }
        else
        {
            final TypedBatchWriter batch = writer.newBatch();

            batch.addFollowUpEvent(event.getKey(), deploymentEvent, addRequestMetadata(event));

            final DeployedWorkflowIterator deployedWorkflowIterator = deploymentResourceIterator.getDeployedWorkflows();
            while (deployedWorkflowIterator.hasNext())
            {
                final DeployedWorkflow deployedWorkflow = deployedWorkflowIterator.next();

                workflowEvent
                    .setState(WorkflowState.CREATE)
                    .setBpmnProcessId(deployedWorkflow.getBpmnProcessId())
                    .setVersion(deployedWorkflow.getVersion())
                    .setBpmnXml(deployedWorkflowIterator.getDeploymentResource().getResource())
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

        if (deploymentEvent.getState() == DeploymentState.VALIDATED)
        {
            updateWorkflowVersions(deploymentEvent.getTopicName(), deploymentEvent.deployedWorkflows());
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

    private class DeploymentResourceIterator implements Iterator<DeploymentResource>
    {
        private final DeployedWorkflowIterator deployedWorkflowIterator = new DeployedWorkflowIterator();
        private final IntArrayList workflowToResourceMapping = new IntArrayList();

        private ValueArray<DeployedWorkflow> deployedWorkflows;
        private ValueArray<DeploymentResource> deploymentResources;
        private Iterator<DeploymentResource> iterator;

        private int currentResource = 0;

        public void wrap(DeploymentEvent deploymentEvent)
        {
            this.deployedWorkflows = deploymentEvent.deployedWorkflows();
            this.deploymentResources = deploymentEvent.resources();
            this.iterator = deploymentResources.iterator();

            currentResource = 0;
            workflowToResourceMapping.clear();
        }

        public DeployedWorkflow addDeployedWorkflow()
        {
            workflowToResourceMapping.addInt(currentResource);

            return deployedWorkflows.add();
        }

        @Override
        public boolean hasNext()
        {
            return iterator.hasNext();
        }

        @Override
        public DeploymentResource next()
        {
            currentResource += 1;
            return iterator.next();
        }

        public DeployedWorkflowIterator getDeployedWorkflows()
        {
            deployedWorkflowIterator.wrap(deploymentResources.iterator(), deployedWorkflows.iterator(), workflowToResourceMapping);

            return deployedWorkflowIterator;
        }

    }

    private class DeployedWorkflowIterator implements Iterator<DeployedWorkflow>
    {
        private Iterator<DeploymentResource> deploymentResourceIterator;

        private Iterator<DeployedWorkflow> deployedWorkflowIterator;

        private final IntArrayListIterator workflowToResourceIterator = new IntArrayListIterator();

        private DeploymentResource deploymentResource;
        private int lastResource = -1;

        public void wrap(
                Iterator<DeploymentResource> deploymentResourceIterator,
                Iterator<DeployedWorkflow> deployedWorkflowIterator,
                IntArrayList workflowToResourceMapping)
        {
            this.deploymentResourceIterator = deploymentResourceIterator;
            this.deployedWorkflowIterator = deployedWorkflowIterator;

            workflowToResourceIterator.wrap(workflowToResourceMapping);

            lastResource = -1;
            deploymentResource = null;
        }

        @Override
        public boolean hasNext()
        {
            return deployedWorkflowIterator.hasNext();
        }

        @Override
        public DeployedWorkflow next()
        {
            final DeployedWorkflow deployedWorkflow = deployedWorkflowIterator.next();

            final int resource = workflowToResourceIterator.nextInt();
            if (resource > lastResource)
            {
                lastResource = resource;
                deploymentResource = deploymentResourceIterator.next();
            }

            return deployedWorkflow;
        }

        public DeploymentResource getDeploymentResource()
        {
            return deploymentResource;
        }
    }

}
