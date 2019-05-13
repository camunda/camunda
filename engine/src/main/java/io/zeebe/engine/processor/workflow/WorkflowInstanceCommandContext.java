/*
 * Zeebe Workflow Engine
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
package io.zeebe.engine.processor.workflow;

import io.zeebe.engine.processor.TypedRecord;
import io.zeebe.engine.processor.TypedResponseWriter;
import io.zeebe.engine.processor.TypedStreamWriter;
import io.zeebe.engine.state.instance.ElementInstance;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;

public class WorkflowInstanceCommandContext {

  private TypedRecord<WorkflowInstanceRecord> record;
  private final EventOutput eventOutput;
  private ElementInstance elementInstance;
  private TypedResponseWriter responseWriter;
  private TypedStreamWriter streamWriter;

  public WorkflowInstanceCommandContext(final EventOutput eventOutput) {
    this.eventOutput = eventOutput;
  }

  public WorkflowInstanceIntent getCommand() {
    return (WorkflowInstanceIntent) record.getMetadata().getIntent();
  }

  public TypedRecord<WorkflowInstanceRecord> getRecord() {
    return record;
  }

  public void setRecord(final TypedRecord<WorkflowInstanceRecord> record) {
    this.record = record;
  }

  public EventOutput getOutput() {
    return eventOutput;
  }

  public ElementInstance getElementInstance() {
    return elementInstance;
  }

  public void setElementInstance(final ElementInstance elementInstance) {
    this.elementInstance = elementInstance;
  }

  public TypedResponseWriter getResponseWriter() {
    return responseWriter;
  }

  public void setResponseWriter(final TypedResponseWriter responseWriter) {
    this.responseWriter = responseWriter;
  }

  public void setStreamWriter(final TypedStreamWriter writer) {
    this.streamWriter = writer;
    this.eventOutput.setStreamWriter(writer);
  }

  public void reject(final RejectionType rejectionType, final String reason) {
    streamWriter.appendRejection(record, rejectionType, reason);
    responseWriter.writeRejectionOnCommand(record, rejectionType, reason);
  }
}
