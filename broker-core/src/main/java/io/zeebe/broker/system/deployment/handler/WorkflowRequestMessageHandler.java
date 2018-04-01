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
package io.zeebe.broker.system.deployment.handler;

import io.zeebe.broker.clustering.base.partitions.Partition;
import io.zeebe.broker.system.deployment.message.CreateWorkflowRequest;
import io.zeebe.broker.system.deployment.message.DeleteWorkflowMessage;
import io.zeebe.broker.workflow.data.WorkflowEvent;
import io.zeebe.broker.workflow.data.WorkflowState;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LogStreamWriter;
import io.zeebe.logstreams.log.LogStreamWriterImpl;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.EventType;
import io.zeebe.protocol.impl.BrokerEventMetadata;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.util.DeferredCommandContext;
import org.agrona.DirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;

public class WorkflowRequestMessageHandler
{
    private final CreateWorkflowRequest createRequest = new CreateWorkflowRequest();
    private final DeleteWorkflowMessage deleteMessage = new DeleteWorkflowMessage();

    private final WorkflowEvent workflowEvent = new WorkflowEvent();
    private final BrokerEventMetadata eventMetadata = new BrokerEventMetadata();

    private final DeferredCommandContext deferredContext = new DeferredCommandContext();

    private final Int2ObjectHashMap<LogStream> logStreams = new Int2ObjectHashMap<>();

    private final LogStreamWriter logStreamWriter = new LogStreamWriterImpl();

    public boolean onCreateWorkflowRequest(
            DirectBuffer buffer,
            int offset,
            int length,
            RemoteAddress remoteAddress,
            long requestId)
    {
        eventMetadata.reset()
            .requestId(requestId)
            .requestStreamId(remoteAddress.getStreamId())
            .protocolVersion(Protocol.PROTOCOL_VERSION)
            .eventType(EventType.WORKFLOW_EVENT);

        createRequest.wrap(buffer, offset, length);

        final LogStream logStream = getLogStream(createRequest.getPartitionId());
        if (logStream != null)
        {
            workflowEvent.reset();
            workflowEvent
                .setState(WorkflowState.CREATE)
                .setDeploymentKey(createRequest.getDeploymentKey())
                .setBpmnProcessId(createRequest.getBpmnProcessId())
                .setVersion(createRequest.getVersion())
                .setBpmnXml(createRequest.getBpmnXml());

            return writeWorkflowEvent(createRequest.getWorkflowKey(), logStream);
        }
        else
        {
            return true;
        }
    }

    public boolean onDeleteWorkflowMessage(
            DirectBuffer buffer,
            int offset,
            int length)
    {
        eventMetadata.reset()
            .protocolVersion(Protocol.PROTOCOL_VERSION)
            .eventType(EventType.WORKFLOW_EVENT);

        deleteMessage.wrap(buffer, offset, length);

        final LogStream logStream = getLogStream(deleteMessage.getPartitionId());
        if (logStream != null)
        {
            workflowEvent.reset();
            workflowEvent
                .setState(WorkflowState.DELETE)
                .setDeploymentKey(deleteMessage.getDeploymentKey())
                .setBpmnProcessId(deleteMessage.getBpmnProcessId())
                .setVersion(deleteMessage.getVersion())
                .setBpmnXml(deleteMessage.getBpmnXml());

            return writeWorkflowEvent(deleteMessage.getWorkflowKey(), logStream);
        }
        else
        {
            return true;
        }
    }

    private LogStream getLogStream(int partitionId)
    {
        // process log-stream add / remove commands
        deferredContext.doWork();

        return logStreams.get(partitionId);
    }

    private boolean writeWorkflowEvent(long key, final LogStream logStream)
    {
        logStreamWriter.wrap(logStream);

        final long eventPosition = logStreamWriter
                .key(key)
                .metadataWriter(eventMetadata)
                .valueWriter(workflowEvent)
                .tryWrite();

        return eventPosition > 0;
    }

    public void addPartition(final Partition partition)
    {
        deferredContext.runAsync(() -> logStreams.put(partition.getInfo().getPartitionId(), partition.getLogStream()));
    }

    public void removeStream(final Partition partition)
    {
        deferredContext.runAsync(() -> logStreams.remove(partition.getInfo().getPartitionId()));
    }
}
