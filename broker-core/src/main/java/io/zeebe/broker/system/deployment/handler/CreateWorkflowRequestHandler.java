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

import java.util.function.Consumer;

import io.zeebe.broker.system.deployment.message.CreateWorkflowRequest;
import io.zeebe.broker.workflow.data.WorkflowEvent;
import io.zeebe.broker.workflow.data.WorkflowState;
import io.zeebe.logstreams.log.*;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.EventType;
import io.zeebe.protocol.impl.BrokerEventMetadata;
import io.zeebe.transport.RemoteAddress;
import org.agrona.DirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.concurrent.ManyToOneConcurrentArrayQueue;

public class CreateWorkflowRequestHandler
{
    private final CreateWorkflowRequest request = new CreateWorkflowRequest();

    private final WorkflowEvent workflowEvent = new WorkflowEvent();
    private final BrokerEventMetadata eventMetadata = new BrokerEventMetadata();

    private final ManyToOneConcurrentArrayQueue<Runnable> cmdQueue = new ManyToOneConcurrentArrayQueue<>(100);
    private final Consumer<Runnable> cmdConsumer = (c) -> c.run();

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

        request.wrap(buffer, offset, length);

        return onRequest(request);
    }

    private boolean onRequest(CreateWorkflowRequest request)
    {
        boolean success = false;

        // process log-stream add / remove commands
        cmdQueue.drain(cmdConsumer);

        final LogStream logStream = logStreams.get(request.getPartitionId());

        if (logStream != null)
        {
            success = writeWorkflowEvent(request, logStream);
        }

        return success;
    }

    private boolean writeWorkflowEvent(CreateWorkflowRequest request, final LogStream logStream)
    {
        logStreamWriter.wrap(logStream);

        workflowEvent.reset();
        workflowEvent
            .setState(WorkflowState.CREATE)
            .setDeploymentKey(request.getDeploymentKey())
            .setBpmnProcessId(request.getBpmnProcessId())
            .setVersion(request.getVersion())
            .setBpmnXml(request.getBpmnXml());

        final long eventPosition = logStreamWriter
                .key(request.getWorkflowKey())
                .raftTermId(logStream.getTerm())
                .metadataWriter(eventMetadata)
                .valueWriter(workflowEvent)
                .tryWrite();

        return eventPosition > 0;
    }

    public void addStream(final LogStream logStream)
    {
        cmdQueue.add(() -> logStreams.put(logStream.getPartitionId(), logStream));
    }

    public void removeStream(final LogStream logStream)
    {
        cmdQueue.add(() -> logStreams.remove(logStream.getPartitionId()));
    }
}
