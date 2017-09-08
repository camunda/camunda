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
package io.zeebe.broker.workflow.processor;

import static io.zeebe.protocol.clientapi.EventType.DEPLOYMENT_EVENT;
import static org.agrona.BitUtil.SIZE_OF_CHAR;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;

import io.zeebe.broker.logstreams.processor.MetadataFilter;
import io.zeebe.broker.transport.clientapi.CommandResponseWriter;
import io.zeebe.broker.workflow.data.*;
import io.zeebe.logstreams.log.*;
import io.zeebe.logstreams.processor.*;
import io.zeebe.logstreams.snapshot.ZbMapSnapshotSupport;
import io.zeebe.logstreams.spi.SnapshotSupport;
import io.zeebe.map.Bytes2LongZbMap;
import io.zeebe.model.bpmn.BpmnModelApi;
import io.zeebe.model.bpmn.ValidationResult;
import io.zeebe.model.bpmn.impl.ZeebeConstraints;
import io.zeebe.model.bpmn.instance.Workflow;
import io.zeebe.model.bpmn.instance.WorkflowDefinition;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.EventType;
import io.zeebe.protocol.impl.BrokerEventMetadata;
import org.agrona.DirectBuffer;

public class DeploymentStreamProcessor implements StreamProcessor, EventProcessor
{
    protected final BrokerEventMetadata sourceEventMetadata = new BrokerEventMetadata();
    protected final BrokerEventMetadata targetEventMetadata = new BrokerEventMetadata();

    protected final DeploymentEvent deploymentEvent = new DeploymentEvent();
    protected final WorkflowEvent workflowEvent = new WorkflowEvent();

    protected final CommandResponseWriter responseWriter;

    protected final Bytes2LongZbMap map;
    protected final ZbMapSnapshotSupport<Bytes2LongZbMap> indexSnapshotSupport;

    protected final BpmnModelApi bpmn = new BpmnModelApi();

    protected final ArrayList<DeployedWorkflow> deployedWorkflows = new ArrayList<>();

    protected DirectBuffer logStreamTopicName;
    protected int logStreamPartitionId;

    protected LogStream targetStream;
    protected LogStreamBatchWriter logStreamBatchWriter;
    protected int streamProcessorId;

    protected long eventKey;
    protected long eventPosition;

    public DeploymentStreamProcessor(CommandResponseWriter responseWriter)
    {
        this.responseWriter = responseWriter;

        this.map = new Bytes2LongZbMap(ZeebeConstraints.ID_MAX_LENGTH * SIZE_OF_CHAR);
        this.indexSnapshotSupport = new ZbMapSnapshotSupport<>(map);
    }

    @Override
    public SnapshotSupport getStateResource()
    {
        return indexSnapshotSupport;
    }

    @Override
    public void onOpen(StreamProcessorContext context)
    {
        final LogStream sourceStream = context.getSourceStream();
        logStreamTopicName = sourceStream.getTopicName();
        logStreamPartitionId = sourceStream.getPartitionId();

        streamProcessorId = context.getId();

        logStreamBatchWriter = new LogStreamBatchWriterImpl(context.getTargetStream());
        targetStream = context.getTargetStream();
    }

    @Override
    public void onClose()
    {
        map.close();
    }

    public static MetadataFilter eventFilter()
    {
        return m -> m.getEventType() == DEPLOYMENT_EVENT;
    }

    @Override
    public EventProcessor onEvent(LoggedEvent event)
    {
        sourceEventMetadata.reset();
        deploymentEvent.reset();

        eventKey = event.getKey();
        eventPosition = event.getPosition();

        event.readMetadata(sourceEventMetadata);
        event.readValue(deploymentEvent);

        EventProcessor eventProcessor = null;

        switch (deploymentEvent.getState())
        {
            case CREATE_DEPLOYMENT:
                eventProcessor = this;
                break;

            default:
                break;
        }

        return eventProcessor;
    }

    @Override
    public void afterEvent()
    {
        deployedWorkflows.clear();
    }

    @Override
    public void processEvent()
    {
        try
        {
            final WorkflowDefinition definition = bpmn.readFromBuffer(deploymentEvent.getBpmnXml());
            final ValidationResult validationResult = bpmn.validate(definition);

            if (!validationResult.hasErrors())
            {
                deploymentEvent.setState(DeploymentState.DEPLOYMENT_CREATED);

                collectDeployedWorkflows(definition);
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

        if (deployedWorkflows.isEmpty())
        {
            deploymentEvent.setState(DeploymentState.DEPLOYMENT_REJECTED);
        }
    }

    protected void collectDeployedWorkflows(final WorkflowDefinition definition)
    {
        // currently, it can only be one process
        final Workflow workflow = definition.getWorkflows().iterator().next();

        final DirectBuffer bpmnProcessId = workflow.getBpmnProcessId();

        final int latestVersion = (int) map.get(bpmnProcessId.byteArray(), 0L);
        final int version = latestVersion + 1;

        deploymentEvent.deployedWorkflows().add()
            .setBpmnProcessId(bpmnProcessId)
            .setVersion(version);

        deployedWorkflows.add(new DeployedWorkflow(bpmnProcessId, version));
    }

    protected String generateErrorMessage(final Exception e)
    {
        final StringWriter stacktraceWriter = new StringWriter();

        e.printStackTrace(new PrintWriter(stacktraceWriter));

        return String.format("Failed to deploy BPMN model: %s", stacktraceWriter);
    }

    @Override
    public boolean executeSideEffects()
    {
        return responseWriter
                .topicName(logStreamTopicName)
                .partitionId(logStreamPartitionId)
                .position(eventPosition)
                .key(eventKey)
                .eventWriter(deploymentEvent)
                .tryWriteResponse(sourceEventMetadata.getRequestStreamId(), sourceEventMetadata.getRequestId());
    }

    @Override
    public long writeEvent(LogStreamWriter writer)
    {
        logStreamBatchWriter
            .producerId(streamProcessorId)
            .sourceEvent(logStreamTopicName, logStreamPartitionId, eventPosition);

        // write deployment event
        targetEventMetadata.reset();
        targetEventMetadata
            .protocolVersion(Protocol.PROTOCOL_VERSION)
            .eventType(DEPLOYMENT_EVENT)
            .raftTermId(targetStream.getTerm());

        logStreamBatchWriter.event()
            .key(eventKey)
            .metadataWriter(targetEventMetadata)
            .valueWriter(deploymentEvent)
            .done();

        // write workflow events
        targetEventMetadata.eventType(EventType.WORKFLOW_EVENT);

        for (int i = 0; i < deployedWorkflows.size(); i++)
        {
            final DeployedWorkflow deployedWorkflow = deployedWorkflows.get(i);

            workflowEvent.reset();
            workflowEvent
                .setState(WorkflowState.CREATED)
                .setBpmnProcessId(deployedWorkflow.getBpmnProcessId())
                .setVersion(deployedWorkflow.getVersion())
                .setBpmnXml(deploymentEvent.getBpmnXml())
                .setDeploymentKey(eventKey);

            logStreamBatchWriter.event()
                .positionAsKey()
                .metadataWriter(targetEventMetadata)
                .valueWriter(workflowEvent)
                .done();
        }

        return logStreamBatchWriter.tryWrite();
    }

    @Override
    public void updateState()
    {
        for (int i = 0; i < deployedWorkflows.size(); i++)
        {
            final DeployedWorkflow deployedWorkflow = deployedWorkflows.get(i);

            map.put(deployedWorkflow.getBpmnProcessId().byteArray(), deployedWorkflow.getVersion());
        }
    }

    private static final class DeployedWorkflow
    {
        private final DirectBuffer bpmnProcessId;
        private final int version;

        DeployedWorkflow(DirectBuffer bpmnProcessId, int version)
        {
            this.bpmnProcessId = bpmnProcessId;
            this.version = version;
        }

        public DirectBuffer getBpmnProcessId()
        {
            return bpmnProcessId;
        }

        public int getVersion()
        {
            return version;
        }
    }

}
