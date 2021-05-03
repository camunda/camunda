/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.processinstance;

import io.zeebe.engine.processing.streamprocessor.TypedRecord;
import io.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.zeebe.engine.processing.streamprocessor.writers.TypedStreamWriter;
import io.zeebe.engine.state.instance.ElementInstance;
import io.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.intent.ProcessInstanceIntent;

public final class ProcessInstanceCommandContext {

  private final MutableElementInstanceState elementInstanceState;

  private TypedRecord<ProcessInstanceRecord> record;
  private ElementInstance elementInstance;
  private TypedResponseWriter responseWriter;
  private TypedStreamWriter streamWriter;

  public ProcessInstanceCommandContext(final MutableElementInstanceState elementInstanceState) {
    this.elementInstanceState = elementInstanceState;
  }

  public ProcessInstanceIntent getCommand() {
    return (ProcessInstanceIntent) record.getIntent();
  }

  public TypedRecord<ProcessInstanceRecord> getRecord() {
    return record;
  }

  public void setRecord(final TypedRecord<ProcessInstanceRecord> record) {
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
