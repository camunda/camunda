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

import static io.zeebe.util.EnsureUtil.ensureGreaterThan;
import static io.zeebe.util.EnsureUtil.ensureNotNull;

import io.zeebe.broker.incident.data.ErrorType;
import io.zeebe.broker.incident.data.IncidentRecord;
import io.zeebe.broker.workflow.data.WorkflowInstanceRecord;
import io.zeebe.logstreams.log.LogStreamWriter;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.Intent;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.impl.RecordMetadata;

public class IncidentEventWriter
{
    private static final String UNKNOWN_ERROR = "unknown";

    private final RecordMetadata incidentEventMetadata = new RecordMetadata();
    private final IncidentRecord incidentEvent = new IncidentRecord();

    private final WorkflowInstanceRecord workflowInstanceEvent;
    private final RecordMetadata failureEventMetadata;

    private long failureEventPosition;
    private long activityInstanceKey;

    private ErrorType errorType;
    private String errorMessage;

    public IncidentEventWriter(RecordMetadata failureEventMetadata, WorkflowInstanceRecord workflowInstanceEvent)
    {
        ensureNotNull("failure event metadata", failureEventMetadata);
        ensureNotNull("workflow instance event", workflowInstanceEvent);

        this.failureEventMetadata = failureEventMetadata;
        this.workflowInstanceEvent = workflowInstanceEvent;
    }

    public IncidentEventWriter reset()
    {
        failureEventPosition = -1;
        activityInstanceKey = -1;

        errorType = ErrorType.UNKNOWN;
        errorMessage = UNKNOWN_ERROR;

        return this;
    }

    public IncidentEventWriter failureEventPosition(long position)
    {
        this.failureEventPosition = position;
        return this;
    }

    public IncidentEventWriter activityInstanceKey(long activityInstanceKey)
    {
        this.activityInstanceKey = activityInstanceKey;
        return this;
    }

    public IncidentEventWriter errorType(ErrorType errorType)
    {
        this.errorType = errorType;
        return this;
    }

    public IncidentEventWriter errorMessage(String errorMessage)
    {
        this.errorMessage = errorMessage;
        return this;
    }

    public long tryWrite(LogStreamWriter logStreamWriter)
    {
        ensureGreaterThan("failure event position", failureEventPosition, 0);
        ensureGreaterThan("activity instance key", activityInstanceKey, 0);

        incidentEventMetadata.reset()
            .protocolVersion(Protocol.PROTOCOL_VERSION)
            .valueType(ValueType.INCIDENT)
            .recordType(RecordType.EVENT);

        incidentEvent.reset();
        incidentEvent
            .setErrorType(errorType)
            .setErrorMessage(errorMessage)
            .setFailureEventPosition(failureEventPosition)
            .setBpmnProcessId(workflowInstanceEvent.getBpmnProcessId())
            .setWorkflowInstanceKey(workflowInstanceEvent.getWorkflowInstanceKey())
            .setActivityId(workflowInstanceEvent.getActivityId())
            .setActivityInstanceKey(activityInstanceKey);

        if (!failureEventMetadata.hasIncidentKey())
        {
            incidentEventMetadata.recordType(RecordType.COMMAND);
            incidentEventMetadata.intent(Intent.CREATE);

            logStreamWriter.positionAsKey();
        }
        else
        {
            incidentEventMetadata.recordType(RecordType.EVENT);
            incidentEventMetadata.intent(Intent.RESOLVE_FAILED);

            logStreamWriter.key(failureEventMetadata.getIncidentKey());
        }

        return logStreamWriter
                .metadataWriter(incidentEventMetadata)
                .valueWriter(incidentEvent)
                .tryWrite();
    }

}
