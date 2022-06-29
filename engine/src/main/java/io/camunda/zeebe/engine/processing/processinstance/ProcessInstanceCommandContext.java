/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecord;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Builders;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.CommandsBuilder;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;

public final class ProcessInstanceCommandContext {

  private final MutableElementInstanceState elementInstanceState;

  private TypedRecord<ProcessInstanceRecord> record;
  private ElementInstance elementInstance;
  private Builders builders;

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
    return builders.response();
  }

  public MutableElementInstanceState getElementInstanceState() {
    return elementInstanceState;
  }

  public void reject(final RejectionType rejectionType, final String reason) {
    builders.rejection().appendRejection(record, rejectionType, reason);
    builders.response().writeRejectionOnCommand(record, rejectionType, reason);
  }

  public CommandsBuilder getCommandWriter() {
    return builders.command();
  }

  public void setWriters(final Builders builders) {
    this.builders = builders;
  }
}
