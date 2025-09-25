/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.common.processing.processinstance;

import io.camunda.zeebe.engine.common.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.common.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.common.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.common.state.instance.ElementInstance;
import io.camunda.zeebe.engine.common.state.mutable.MutableElementInstanceState;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;

public final class ProcessInstanceCommandContext {

  private final MutableElementInstanceState elementInstanceState;

  private TypedRecord<ProcessInstanceRecord> record;
  private ElementInstance elementInstance;
  private final Writers writers;

  public ProcessInstanceCommandContext(
      final MutableElementInstanceState elementInstanceState, final Writers writers) {
    this.elementInstanceState = elementInstanceState;
    this.writers = writers;
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
    return writers.response();
  }

  public MutableElementInstanceState getElementInstanceState() {
    return elementInstanceState;
  }

  public void reject(final RejectionType rejectionType, final String reason) {
    writers.rejection().appendRejection(record, rejectionType, reason);
    writers.response().writeRejectionOnCommand(record, rejectionType, reason);
  }

  public TypedCommandWriter getCommandWriter() {
    return writers.command();
  }
}
