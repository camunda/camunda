/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow;

import io.zeebe.engine.processor.TypedStreamWriter;
import io.zeebe.engine.state.instance.VariablesState.VariableListener;
import io.zeebe.protocol.impl.record.value.variable.VariableRecord;
import io.zeebe.protocol.record.intent.VariableIntent;
import org.agrona.DirectBuffer;

public class UpdateVariableStreamWriter implements VariableListener {

  private final VariableRecord record = new VariableRecord();

  private final TypedStreamWriter streamWriter;

  public UpdateVariableStreamWriter(TypedStreamWriter streamWriter) {
    this.streamWriter = streamWriter;
  }

  @Override
  public void onCreate(
      long key,
      long workflowKey,
      DirectBuffer name,
      DirectBuffer value,
      long variableScopeKey,
      long rootScopeKey) {
    record
        .setName(name)
        .setValue(value)
        .setScopeKey(variableScopeKey)
        .setWorkflowInstanceKey(rootScopeKey)
        .setWorkflowKey(workflowKey);

    streamWriter.appendFollowUpEvent(key, VariableIntent.CREATED, record);
  }

  @Override
  public void onUpdate(
      long key,
      long workflowKey,
      DirectBuffer name,
      DirectBuffer value,
      long variableScopeKey,
      long rootScopeKey) {
    record
        .setName(name)
        .setValue(value)
        .setScopeKey(variableScopeKey)
        .setWorkflowInstanceKey(rootScopeKey)
        .setWorkflowKey(workflowKey);

    streamWriter.appendFollowUpEvent(key, VariableIntent.UPDATED, record);
  }
}
