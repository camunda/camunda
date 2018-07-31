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

import io.zeebe.broker.incident.data.ErrorType;
import io.zeebe.broker.incident.data.IncidentRecord;
import io.zeebe.broker.logstreams.processor.TypedRecord;
import io.zeebe.broker.logstreams.processor.TypedStreamWriter;
import io.zeebe.broker.workflow.data.WorkflowInstanceRecord;
import io.zeebe.broker.workflow.model.ExecutableFlowElement;
import io.zeebe.protocol.intent.IncidentIntent;

public class BpmnStepContext<T extends ExecutableFlowElement> {

  private TypedRecord<WorkflowInstanceRecord> record;
  private ExecutableFlowElement element;
  private TypedStreamWriter streamWriter;

  private final IncidentRecord incidentCommand = new IncidentRecord();

  public TypedRecord<WorkflowInstanceRecord> getRecord() {
    return record;
  }

  public WorkflowInstanceRecord getValue() {
    return record.getValue();
  }

  public void setRecord(TypedRecord<WorkflowInstanceRecord> record) {
    this.record = record;
  }

  public T getElement() {
    return (T) element;
  }

  public void setElement(ExecutableFlowElement element) {
    this.element = element;
  }

  public TypedStreamWriter getStreamWriter() {
    return streamWriter;
  }

  public void setStreamWriter(TypedStreamWriter streamWriter) {
    this.streamWriter = streamWriter;
  }

  public void raiseIncident(ErrorType errorType, String errorMessage) {

    incidentCommand.reset();

    incidentCommand
        .initFromWorkflowInstanceFailure(record)
        .setErrorType(errorType)
        .setErrorMessage(errorMessage);

    if (!record.getMetadata().hasIncidentKey()) {
      streamWriter.writeNewCommand(IncidentIntent.CREATE, incidentCommand);
    } else {
      streamWriter.writeFollowUpEvent(
          record.getMetadata().getIncidentKey(), IncidentIntent.RESOLVE_FAILED, incidentCommand);
    }
  }
}
