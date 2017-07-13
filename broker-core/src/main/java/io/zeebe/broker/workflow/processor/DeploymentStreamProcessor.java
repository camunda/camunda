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

import static io.zeebe.hashindex.HashIndex.OPTIMAL_BUCKET_COUNT;
import static io.zeebe.hashindex.HashIndex.OPTIMAL_INDEX_SIZE;
import static io.zeebe.protocol.clientapi.EventType.DEPLOYMENT_EVENT;
import static org.agrona.BitUtil.SIZE_OF_CHAR;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import io.zeebe.protocol.Protocol;
import org.agrona.DirectBuffer;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.xml.validation.ValidationResults;

import io.zeebe.protocol.impl.BrokerEventMetadata;
import io.zeebe.broker.logstreams.processor.HashIndexSnapshotSupport;
import io.zeebe.broker.logstreams.processor.MetadataFilter;
import io.zeebe.broker.transport.clientapi.CommandResponseWriter;
import io.zeebe.broker.workflow.data.WorkflowDeploymentEvent;
import io.zeebe.broker.workflow.data.WorkflowDeploymentEventType;
import io.zeebe.broker.workflow.graph.WorkflowValidationResultFormatter;
import io.zeebe.broker.workflow.graph.model.ExecutableWorkflow;
import io.zeebe.broker.workflow.graph.transformer.BpmnTransformer;
import io.zeebe.hashindex.Bytes2LongHashIndex;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LogStreamWriter;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.logstreams.processor.EventProcessor;
import io.zeebe.logstreams.processor.StreamProcessor;
import io.zeebe.logstreams.processor.StreamProcessorContext;
import io.zeebe.logstreams.spi.SnapshotSupport;

public class DeploymentStreamProcessor implements StreamProcessor
{
    protected final CreateDeploymentEventProcessor createDeploymentEventProcessor = new CreateDeploymentEventProcessor();

    protected final BrokerEventMetadata sourceEventMetadata = new BrokerEventMetadata();
    protected final BrokerEventMetadata targetEventMetadata = new BrokerEventMetadata();

    protected final WorkflowDeploymentEvent deploymentEvent = new WorkflowDeploymentEvent();

    protected final BpmnTransformer bpmnTransformer = new BpmnTransformer();
    protected final WorkflowValidationResultFormatter validationResultFormatter = new WorkflowValidationResultFormatter();

    protected final CommandResponseWriter responseWriter;

    protected final Bytes2LongHashIndex index;
    protected final HashIndexSnapshotSupport<Bytes2LongHashIndex> indexSnapshotSupport;

    protected final ArrayList<DeployedWorkflow> deployedWorkflows = new ArrayList<>();

    protected DirectBuffer logStreamTopicName;
    protected int logStreamPartitionId;

    protected LogStream targetStream;

    protected long eventKey;

    public DeploymentStreamProcessor(CommandResponseWriter responseWriter)
    {
        this.responseWriter = responseWriter;

        this.index = new Bytes2LongHashIndex(OPTIMAL_INDEX_SIZE, OPTIMAL_BUCKET_COUNT, BpmnTransformer.ID_MAX_LENGTH * SIZE_OF_CHAR);
        this.indexSnapshotSupport = new HashIndexSnapshotSupport<>(index);
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

        targetStream = context.getTargetStream();
    }

    @Override
    public void onClose()
    {
        index.close();
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

        event.readMetadata(sourceEventMetadata);
        event.readValue(deploymentEvent);

        EventProcessor eventProcessor = null;

        switch (deploymentEvent.getEventType())
        {
            case CREATE_DEPLOYMENT:
                eventProcessor = createDeploymentEventProcessor;
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

    private final class CreateDeploymentEventProcessor implements EventProcessor
    {
        @Override
        public void processEvent()
        {
            try
            {
                final BpmnModelInstance bpmnModelInstance = bpmnTransformer.readModelFromBuffer(deploymentEvent.getBpmnXml());
                final ValidationResults validationResults = bpmnTransformer.validate(bpmnModelInstance);

                if (!validationResults.hasErrors())
                {
                    deploymentEvent.setEventType(WorkflowDeploymentEventType.DEPLOYMENT_CREATED);

                    collectDeployedWorkflows(bpmnModelInstance);
                }

                if (validationResults.getErrorCount() > 0 || validationResults.getWarinigCount() > 0)
                {
                    final String errorMessage = generateErrorMessage(validationResults);
                    deploymentEvent.setErrorMessage(errorMessage);
                }
            }
            catch (Exception e)
            {
                final String errorMessage = generateErrorMessage(e);
                deploymentEvent.setErrorMessage(errorMessage);
            }

            if (deployedWorkflows.isEmpty())
            {
                deploymentEvent.setEventType(WorkflowDeploymentEventType.DEPLOYMENT_REJECTED);
            }
        }

        protected void collectDeployedWorkflows(final BpmnModelInstance bpmnModelInstance)
        {
            final List<ExecutableWorkflow> workflows = bpmnTransformer.transform(bpmnModelInstance);
            // currently, it can only be one process
            final ExecutableWorkflow workflow = workflows.get(0);

            final DirectBuffer bpmnProcessId = workflow.getId();

            final int latestVersion = (int) index.get(bpmnProcessId.byteArray(), 0L);

            final int version = latestVersion + 1;

            deployedWorkflows.add(new DeployedWorkflow(bpmnProcessId.byteArray(), version));

            deploymentEvent.deployedWorkflows().add()
                .setBpmnProcessId(bpmnProcessId)
                .setVersion(version);
        }

        protected String generateErrorMessage(final ValidationResults validationResults)
        {
            final StringWriter errorMessageWriter = new StringWriter();

            validationResults.write(errorMessageWriter, validationResultFormatter);

            return errorMessageWriter.toString();
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
                    .key(eventKey)
                    .eventWriter(deploymentEvent)
                    .tryWriteResponse(sourceEventMetadata.getRequestStreamId(), sourceEventMetadata.getRequestId());
        }

        @Override
        public long writeEvent(LogStreamWriter writer)
        {
            targetEventMetadata.reset();
            targetEventMetadata
                .protocolVersion(Protocol.PROTOCOL_VERSION)
                .eventType(DEPLOYMENT_EVENT)
                .raftTermId(targetStream.getTerm());

            return writer
                .key(eventKey)
                .metadataWriter(targetEventMetadata)
                .valueWriter(deploymentEvent)
                .tryWrite();
        }

        @Override
        public void updateState()
        {
            for (int i = 0; i < deployedWorkflows.size(); i++)
            {
                final DeployedWorkflow deployedWorkflow = deployedWorkflows.get(i);

                index.put(deployedWorkflow.getBpmnProcessId(), deployedWorkflow.getVersion());
            }
        }
    }

    private static final class DeployedWorkflow
    {
        private final byte[] bpmnProcessId;
        private final int version;

        DeployedWorkflow(byte[] bpmnProcessId, int version)
        {
            this.bpmnProcessId = bpmnProcessId;
            this.version = version;
        }

        public byte[] getBpmnProcessId()
        {
            return bpmnProcessId;
        }

        public int getVersion()
        {
            return version;
        }
    }

}
