/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.tngp.broker.workflow.processor;

import static org.agrona.BitUtil.SIZE_OF_CHAR;
import static org.camunda.tngp.protocol.clientapi.EventType.DEPLOYMENT_EVENT;

import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;

import org.agrona.DirectBuffer;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.Process;
import org.camunda.bpm.model.xml.validation.ValidationResults;
import org.camunda.tngp.broker.Constants;
import org.camunda.tngp.broker.logstreams.BrokerEventMetadata;
import org.camunda.tngp.broker.logstreams.processor.HashIndexSnapshotSupport;
import org.camunda.tngp.broker.logstreams.processor.MetadataFilter;
import org.camunda.tngp.broker.transport.clientapi.CommandResponseWriter;
import org.camunda.tngp.broker.workflow.data.WorkflowDeploymentEvent;
import org.camunda.tngp.broker.workflow.data.WorkflowDeploymentEventType;
import org.camunda.tngp.broker.workflow.graph.WorkflowValidationResultFormatter;
import org.camunda.tngp.broker.workflow.graph.transformer.BpmnTransformer;
import org.camunda.tngp.broker.workflow.graph.transformer.validator.ProcessIdRule;
import org.camunda.tngp.hashindex.Bytes2LongHashIndex;
import org.camunda.tngp.hashindex.store.IndexStore;
import org.camunda.tngp.logstreams.log.LogStreamWriter;
import org.camunda.tngp.logstreams.log.LoggedEvent;
import org.camunda.tngp.logstreams.processor.EventProcessor;
import org.camunda.tngp.logstreams.processor.StreamProcessor;
import org.camunda.tngp.logstreams.processor.StreamProcessorContext;
import org.camunda.tngp.logstreams.spi.SnapshotSupport;

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

    protected int streamId;

    protected long eventKey;

    public DeploymentStreamProcessor(CommandResponseWriter responseWriter, IndexStore indexStore)
    {
        this.responseWriter = responseWriter;

        this.index = new Bytes2LongHashIndex(indexStore, Short.MAX_VALUE, 64, ProcessIdRule.PROCESS_ID_MAX_LENGTH * SIZE_OF_CHAR);
        this.indexSnapshotSupport = new HashIndexSnapshotSupport<>(index, indexStore);
    }

    @Override
    public SnapshotSupport getStateResource()
    {
        return indexSnapshotSupport;
    }

    @Override
    public void onOpen(StreamProcessorContext context)
    {
        streamId = context.getSourceStream().getId();
    }

    public static MetadataFilter eventFilter()
    {
        return m -> m.getEventType() == DEPLOYMENT_EVENT;
    }

    @Override
    public EventProcessor onEvent(LoggedEvent event)
    {
        eventKey = event.getLongKey();

        event.readMetadata(sourceEventMetadata);
        event.readValue(deploymentEvent);

        EventProcessor eventProcessor = null;

        switch (deploymentEvent.getEvent())
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
        sourceEventMetadata.reset();
        deploymentEvent.reset();

        deployedWorkflows.clear();
    }

    class CreateDeploymentEventProcessor implements EventProcessor
    {
        @Override
        public void processEvent()
        {
            final BpmnModelInstance bpmnModelInstance = readModel(deploymentEvent.getBpmnXml());

            if (bpmnModelInstance != null)
            {
                final ValidationResults validationResults = bpmnTransformer.validate(bpmnModelInstance);

                if (!validationResults.hasErrors())
                {
                    deploymentEvent.setEvent(WorkflowDeploymentEventType.DEPLOYMENT_CREATED);

                    collectDeployedWorkflows(bpmnModelInstance);
                }

                if (validationResults.getErrorCount() > 0 || validationResults.getWarinigCount() > 0)
                {
                    final String errorMessage = generateErrorMessage(validationResults);
                    deploymentEvent.setErrorMessage(errorMessage);
                }
            }

            if (deployedWorkflows.isEmpty())
            {
                deploymentEvent.setEvent(WorkflowDeploymentEventType.DEPLOYMENT_REJECTED);
            }
        }

        protected BpmnModelInstance readModel(final DirectBuffer buffer)
        {
            BpmnModelInstance bpmnModelInstance = null;

            final byte[] bytes = new byte[buffer.capacity()];
            buffer.getBytes(0, bytes);

            try (final ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes))
            {
                bpmnModelInstance = Bpmn.readModelFromStream(inputStream);
            }
            catch (Exception e)
            {
                final StringWriter stacktraceWriter = new StringWriter();
                e.printStackTrace(new PrintWriter(stacktraceWriter));

                deploymentEvent.setErrorMessage("Failed to read BPMN model. " + stacktraceWriter.toString());
            }
            return bpmnModelInstance;
        }

        protected void collectDeployedWorkflows(final BpmnModelInstance bpmnModelInstance)
        {
            final Collection<Process> processes = bpmnModelInstance.getModelElementsByType(Process.class);
            // currently, it can only be one process
            final Process process = processes.iterator().next();

            final String processId = process.getId();
            final byte[] processIdKey = processId.getBytes(StandardCharsets.UTF_8);

            final int latestVersion = (int) index.get(processIdKey, 0L);

            final int version = latestVersion + 1;

            deployedWorkflows.add(new DeployedWorkflow(processIdKey, version));

            deploymentEvent.deployedWorkflows().add()
                .setProcessId(processId)
                .setVersion(version);
        }

        protected String generateErrorMessage(final ValidationResults validationResults)
        {
            final StringWriter errorMessageWriter = new StringWriter();

            validationResults.write(errorMessageWriter, validationResultFormatter);

            return errorMessageWriter.toString();
        }

        @Override
        public boolean executeSideEffects()
        {
            return responseWriter
                    .brokerEventMetadata(sourceEventMetadata)
                    .topicId(streamId)
                    .longKey(eventKey)
                    .eventWriter(deploymentEvent)
                    .tryWriteResponse();
        }

        @Override
        public long writeEvent(LogStreamWriter writer)
        {
            targetEventMetadata.reset();
            targetEventMetadata
                .protocolVersion(Constants.PROTOCOL_VERSION)
                .eventType(DEPLOYMENT_EVENT);

            // TODO: targetEventMetadata.raftTermId(raftTermId);

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

                index.put(deployedWorkflow.getProcessId(), deployedWorkflow.getVersion());
            }
        }
    }

    class DeployedWorkflow
    {
        private final byte[] processId;
        private final int version;

        DeployedWorkflow(byte[] processId, int version)
        {
            this.processId = processId;
            this.version = version;
        }

        public byte[] getProcessId()
        {
            return processId;
        }

        public int getVersion()
        {
            return version;
        }
    }

}
