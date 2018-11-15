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
package io.zeebe.broker.incident.data;

import io.zeebe.broker.logstreams.processor.TypedRecord;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.msgpack.property.EnumProperty;
import io.zeebe.msgpack.property.LongProperty;
import io.zeebe.msgpack.property.StringProperty;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import org.agrona.DirectBuffer;

public class IncidentRecord extends UnpackedObject {
  private final EnumProperty<ErrorType> errorTypeProp =
      new EnumProperty<>("errorType", ErrorType.class, ErrorType.UNKNOWN);
  private final StringProperty errorMessageProp = new StringProperty("errorMessage", "");

  private final StringProperty bpmnProcessIdProp = new StringProperty("bpmnProcessId", "");
  private final LongProperty workflowInstanceKeyProp = new LongProperty("workflowInstanceKey", -1L);
  private final StringProperty elementIdProp = new StringProperty("elementId", "");
  private final LongProperty elementInstanceKeyProp = new LongProperty("elementInstanceKey", -1L);
  private final LongProperty jobKeyProp = new LongProperty("jobKey", -1L);

  public IncidentRecord() {
    this.declareProperty(errorTypeProp)
        .declareProperty(errorMessageProp)
        .declareProperty(bpmnProcessIdProp)
        .declareProperty(workflowInstanceKeyProp)
        .declareProperty(elementIdProp)
        .declareProperty(elementInstanceKeyProp)
        .declareProperty(jobKeyProp);
  }

  public ErrorType getErrorType() {
    return errorTypeProp.getValue();
  }

  public IncidentRecord setErrorType(ErrorType errorType) {
    this.errorTypeProp.setValue(errorType);
    return this;
  }

  public DirectBuffer getErrorMessage() {
    return errorMessageProp.getValue();
  }

  public IncidentRecord setErrorMessage(DirectBuffer errorMessage) {
    this.errorMessageProp.setValue(errorMessage);
    return this;
  }

  public IncidentRecord setErrorMessage(String errorMessage) {
    this.errorMessageProp.setValue(errorMessage);
    return this;
  }

  public DirectBuffer getBpmnProcessId() {
    return bpmnProcessIdProp.getValue();
  }

  public IncidentRecord setBpmnProcessId(DirectBuffer directBuffer) {
    bpmnProcessIdProp.setValue(directBuffer, 0, directBuffer.capacity());
    return this;
  }

  public DirectBuffer getElementId() {
    return elementIdProp.getValue();
  }

  public IncidentRecord setElementId(DirectBuffer elementId) {
    this.elementIdProp.setValue(elementId, 0, elementId.capacity());
    return this;
  }

  public long getWorkflowInstanceKey() {
    return workflowInstanceKeyProp.getValue();
  }

  public IncidentRecord setWorkflowInstanceKey(long workflowInstanceKey) {
    this.workflowInstanceKeyProp.setValue(workflowInstanceKey);
    return this;
  }

  public long getElementInstanceKey() {
    return elementInstanceKeyProp.getValue();
  }

  public IncidentRecord setElementInstanceKey(long elementInstanceKey) {
    this.elementInstanceKeyProp.setValue(elementInstanceKey);
    return this;
  }

  public long getJobKey() {
    return jobKeyProp.getValue();
  }

  public IncidentRecord setJobKey(long jobKey) {
    this.jobKeyProp.setValue(jobKey);
    return this;
  }

  public IncidentRecord initFromWorkflowInstanceFailure(
      TypedRecord<WorkflowInstanceRecord> workflowInstanceEvent) {
    final WorkflowInstanceRecord value = workflowInstanceEvent.getValue();

    setElementInstanceKey(workflowInstanceEvent.getKey());
    setBpmnProcessId(value.getBpmnProcessId());
    setWorkflowInstanceKey(value.getWorkflowInstanceKey());
    setElementId(value.getElementId());

    return this;
  }
}
