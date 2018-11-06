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

import io.zeebe.broker.logstreams.processor.TypedRecord;
import io.zeebe.broker.logstreams.processor.TypedResponseWriter;
import io.zeebe.broker.logstreams.processor.TypedStreamWriter;
import io.zeebe.broker.workflow.state.ElementInstance;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;

public class WorkflowInstanceCommandContext {

  private TypedRecord<WorkflowInstanceRecord> record;
  private final EventOutput eventOutput;
  private ElementInstance elementInstance;
  private TypedResponseWriter responseWriter;
  private TypedStreamWriter streamWriter;

  public WorkflowInstanceCommandContext(EventOutput eventOutput) {
    this.eventOutput = eventOutput;
  }

  public WorkflowInstanceIntent getCommand() {
    return (WorkflowInstanceIntent) record.getMetadata().getIntent();
  }

  public TypedRecord<WorkflowInstanceRecord> getRecord() {
    return record;
  }

  public void setRecord(TypedRecord<WorkflowInstanceRecord> record) {
    this.record = record;
  }

  public EventOutput getOutput() {
    return eventOutput;
  }

  public ElementInstance getElementInstance() {
    return elementInstance;
  }

  public void setElementInstance(ElementInstance elementInstance) {
    this.elementInstance = elementInstance;
  }

  public TypedResponseWriter getResponseWriter() {
    return responseWriter;
  }

  public void setResponseWriter(TypedResponseWriter responseWriter) {
    this.responseWriter = responseWriter;
  }

  public void setStreamWriter(TypedStreamWriter writer) {
    this.streamWriter = writer;
    this.eventOutput.setStreamWriter(writer);
  }

  public void reject(RejectionType rejectionType, String reason) {
    streamWriter.writeRejection(record, rejectionType, reason);
    responseWriter.writeRejectionOnCommand(record, rejectionType, reason);
  }
}
