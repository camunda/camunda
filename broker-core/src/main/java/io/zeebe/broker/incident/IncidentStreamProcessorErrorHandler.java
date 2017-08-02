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
package io.zeebe.broker.incident;

import io.zeebe.broker.incident.data.*;
import io.zeebe.broker.logstreams.processor.StreamProcessorIds;
import io.zeebe.broker.workflow.data.WorkflowInstanceEvent;
import io.zeebe.logstreams.log.*;
import io.zeebe.logstreams.processor.StreamProcessorErrorHandler;
import io.zeebe.msgpack.mapping.MappingException;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.EventType;
import io.zeebe.protocol.impl.BrokerEventMetadata;
import org.agrona.DirectBuffer;

public class IncidentStreamProcessorErrorHandler implements StreamProcessorErrorHandler
{
    private final LogStream logStream;
    private final DirectBuffer sourceStreamTopicName;
    private final int sourceStreamPartitionId;

    private final LogStreamWriter logStreamWriter;

    private final BrokerEventMetadata incidentEventMetadata = new BrokerEventMetadata();
    private final IncidentEvent incidentEvent = new IncidentEvent();

    private final BrokerEventMetadata failureEventMetadata = new BrokerEventMetadata();
    private final WorkflowInstanceEvent workflowInstanceEvent = new WorkflowInstanceEvent();

    public IncidentStreamProcessorErrorHandler(LogStream logStream)
    {
        this.logStream = logStream;
        this.sourceStreamTopicName = logStream.getTopicName();
        this.sourceStreamPartitionId = logStream.getPartitionId();

        this.logStreamWriter = new LogStreamWriterImpl(logStream);
    }

    @Override
    public boolean canHandle(Exception error)
    {
        return error instanceof MappingException;
    }

    @Override
    public boolean onError(LoggedEvent failureEvent, Exception error)
    {
        boolean success = false;

        if (error instanceof MappingException)
        {
            success = handlePayloadException(failureEvent, ErrorType.IO_MAPPING_ERROR, error);
        }

        return success;
    }

    private boolean handlePayloadException(LoggedEvent failureEvent, ErrorType errorType, Exception error)
    {
        incidentEventMetadata.reset()
            .protocolVersion(Protocol.PROTOCOL_VERSION)
            .eventType(EventType.INCIDENT_EVENT)
            .raftTermId(logStream.getTerm());

        incidentEvent.reset();
        incidentEvent
            .setErrorType(errorType)
            .setErrorMessage(error.getMessage())
            .setFailureEventPosition(failureEvent.getPosition());

        failureEventMetadata.reset();
        failureEvent.readMetadata(failureEventMetadata);

        setWorkflowInstanceData(failureEvent);

        if (!failureEventMetadata.hasIncidentKey())
        {
            incidentEvent.setState(IncidentState.CREATE);

            logStreamWriter.positionAsKey();
        }
        else
        {
            incidentEvent.setState(IncidentState.RESOLVE_FAILED);

            logStreamWriter.key(failureEventMetadata.getIncidentKey());
        }

        final long position = logStreamWriter
                .producerId(StreamProcessorIds.WORKFLOW_INSTANCE_PROCESSOR_ID)
                .sourceEvent(sourceStreamTopicName, sourceStreamPartitionId, failureEvent.getPosition())
                .metadataWriter(incidentEventMetadata)
                .valueWriter(incidentEvent)
                .tryWrite();

        return position > 0;
    }

    private void setWorkflowInstanceData(LoggedEvent failureEvent)
    {
        if (failureEventMetadata.getEventType() == EventType.WORKFLOW_INSTANCE_EVENT)
        {
            workflowInstanceEvent.reset();
            failureEvent.readValue(workflowInstanceEvent);

            incidentEvent
                .setBpmnProcessId(workflowInstanceEvent.getBpmnProcessId())
                .setWorkflowInstanceKey(workflowInstanceEvent.getWorkflowInstanceKey())
                .setActivityId(workflowInstanceEvent.getActivityId())
                .setActivityInstanceKey(failureEvent.getKey());
        }
        else
        {
            throw new RuntimeException(String.format("Unsupported failure event type '%s'.", failureEventMetadata.getEventType().name()));
        }
    }

}
