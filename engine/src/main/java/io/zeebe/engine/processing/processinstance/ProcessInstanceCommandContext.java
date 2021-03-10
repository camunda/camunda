/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.workflowinstance;

import io.zeebe.engine.processing.streamprocessor.TypedRecord;
import io.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.zeebe.engine.processing.streamprocessor.writers.TypedStreamWriter;
import io.zeebe.engine.state.instance.ElementInstance;
import io.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;

public final class WorkflowInstanceCommandContext {

  private final MutableElementInstanceState elementInstanceState;

  private TypedRecord<WorkflowInstanceRecord> record;
  private ElementInstance elementInstance;
  private TypedResponseWriter responseWriter;
  private TypedStreamWriter streamWriter;

  public WorkflowInstanceCommandContext(final MutableElementInstanceState elementInstanceState) {
    this.elementInstanceState = elementInstanceState;
  }

  public WorkflowInstanceIntent getCommand() {
    return (WorkflowInstanceIntent) record.getIntent();
  }

  public TypedRecord<WorkflowInstanceRecord> getRecord() {
    return record;
  }

  public void setRecord(final TypedRecord<WorkflowInstanceRecord> record) {
    this.record = record;
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

  public MutableElementInstanceState getElementInstanceState() {
    return elementInstanceState;
  }

  public void reject(final RejectionType rejectionType, final String reason) {
    streamWriter.appendRejection(record, rejectionType, reason);
    responseWriter.writeRejectionOnCommand(record, rejectionType, reason);
  }

  public TypedStreamWriter getStreamWriter() {
    return streamWriter;
  }

  public void setStreamWriter(final TypedStreamWriter writer) {
    streamWriter = writer;
  }
}
