/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.variable;

import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.state.immutable.VariableState;
import io.camunda.zeebe.engine.state.variable.DocumentEntry;
import io.camunda.zeebe.engine.state.variable.IndexedDocument;
import io.camunda.zeebe.engine.state.variable.VariableInstance;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableRecord;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.util.Iterator;
import org.agrona.DirectBuffer;

/**
 * A behavior which allows processors to mutate the variable state. Use this anywhere where you
 * would want to set a variable during processing.
 *
 * <p>Note that for {@link io.camunda.zeebe.engine.state.EventApplier}, you should just use the
 * mutable state directly.
 */
public final class VariableBehavior {

  private final VariableState variableState;
  private final StateWriter stateWriter;
  private final KeyGenerator keyGenerator;

  private final IndexedDocument indexedDocument = new IndexedDocument();
  private final VariableRecord variableRecord = new VariableRecord();

  public VariableBehavior(
      final VariableState variableState,
      final StateWriter stateWriter,
      final KeyGenerator keyGenerator) {
    this.variableState = variableState;
    this.stateWriter = stateWriter;
    this.keyGenerator = keyGenerator;
  }

  /**
   * Merges the given document directly on the given scope key.
   *
   * <p>If any variable from the document already exists on the current scope, a {@code
   * Variable.UPDATED} record is produced as a follow up event.
   *
   * <p>For all variables from the document which do not exist in the current scope, a {@code
   * Variable.CREATED} record is produced as a follow up event.
   *
   * @param scopeKey the scope key for each variable
   * @param processDefinitionKey the process key to be associated with each variable
   * @param processInstanceKey the process instance key to be associated with each variable
   * @param document the document to merge
   */
  public void mergeLocalDocument(
      final long scopeKey,
      final long processDefinitionKey,
      final long processInstanceKey,
      final DirectBuffer bpmnProcessId,
      final DirectBuffer document) {
    indexedDocument.index(document);
    if (indexedDocument.isEmpty()) {
      return;
    }

    variableRecord
        .setScopeKey(scopeKey)
        .setProcessDefinitionKey(processDefinitionKey)
        .setProcessInstanceKey(processInstanceKey)
        .setBpmnProcessId(bpmnProcessId);
    for (final DocumentEntry entry : indexedDocument) {
      applyEntryToRecord(entry);
      setLocalVariable(variableRecord);
    }
  }

  public void initLocalDocument(
      final long scopeKey,
      final long processDefinitionKey,
      final long processInstanceKey,
      final DirectBuffer bpmnProcessId,
      final DirectBuffer document) {
    indexedDocument.index(document);
    if (indexedDocument.isEmpty()) {
      return;
    }

    variableRecord
        .setScopeKey(scopeKey)
        .setProcessDefinitionKey(processDefinitionKey)
        .setProcessInstanceKey(processInstanceKey)
        .setBpmnProcessId(bpmnProcessId);

    for (final DocumentEntry entry : indexedDocument) {
      applyEntryToRecord(entry);
      final long key = keyGenerator.nextKey();
      stateWriter.appendFollowUpEvent(key, VariableIntent.CREATED, variableRecord);
    }
  }

  /**
   * Merges the given document, propagating its changes from the bottom to the top of the scope
   * hierarchy.
   *
   * <p>Starting at the given {@code scopeKey}, it will overwrite any variables that exist in that
   * scope with the corresponding values from the given document. Variables that were not set
   * because they did not exist in the current scope are collected as a sub document, which will
   * then be merged with the parent scope, recursively, until there are no more. If we reach a scope
   * with no parent, then any remaining variables are created there.
   *
   * <p>If any variable from the document already exists on the current scope, a {@code
   * Variable.UPDATED} record is produced as a follow up event.
   *
   * <p>For all variables from the document which do not exist in the current scope, a {@code
   * Variable.CREATED} record is produced as a follow up event.
   *
   * @param scopeKey the scope key for each variable
   * @param processDefinitionKey the process key to be associated with each variable
   * @param processInstanceKey the process instance key to be associated with each variable
   * @param document the document to merge
   */
  public void mergeDocument(
      final long scopeKey,
      final long processDefinitionKey,
      final long processInstanceKey,
      final DirectBuffer bpmnProcessId,
      final DirectBuffer document) {
    indexedDocument.index(document);
    if (indexedDocument.isEmpty()) {
      return;
    }

    long currentScope = scopeKey;
    long parentScope;

    variableRecord
        .setProcessDefinitionKey(processDefinitionKey)
        .setProcessInstanceKey(processInstanceKey)
        .setBpmnProcessId(bpmnProcessId);
    while ((parentScope = variableState.getParentScopeKey(currentScope)) > 0) {
      final Iterator<DocumentEntry> entryIterator = indexedDocument.iterator();

      variableRecord.setScopeKey(currentScope);
      while (entryIterator.hasNext()) {
        final DocumentEntry entry = entryIterator.next();
        final VariableInstance variableInstance =
            variableState.getVariableInstanceLocal(currentScope, entry.getName());

        if (variableInstance != null && !variableInstance.getValue().equals(entry.getValue())) {
          applyEntryToRecord(entry);
          stateWriter.appendFollowUpEvent(
              variableInstance.getKey(), VariableIntent.UPDATED, variableRecord);
          entryIterator.remove();
        }
      }

      currentScope = parentScope;
    }

    variableRecord.setScopeKey(currentScope);
    for (final DocumentEntry entry : indexedDocument) {
      applyEntryToRecord(entry);
      setLocalVariable(variableRecord);
    }
  }

  /**
   * Publishes a follow up event to create or update the variable with name {@code name} on the
   * given scope with key {@code scopeKey}, with additional {@code processDefinitionKey} and {@code
   * processInstanceKey} context.
   *
   * <p>If the scope is the process instance itself, then {@code scopeKey} should be equal to {@code
   * processInstanceKey}.
   *
   * @param scopeKey the key of the scope on which to set the variable
   * @param processDefinitionKey the associated process key
   * @param processInstanceKey the associated process instance key
   * @param name a buffer containing only the name of the variable
   * @param value a buffer containing the value of the variable as MessagePack
   * @param valueOffset the offset of the value in the {@code value} buffer
   * @param valueLength the length of the value in the {@code value} buffer
   */
  public void setLocalVariable(
      final long scopeKey,
      final long processDefinitionKey,
      final long processInstanceKey,
      final DirectBuffer bpmnProcessId,
      final DirectBuffer name,
      final DirectBuffer value,
      final int valueOffset,
      final int valueLength) {

    variableRecord
        .setScopeKey(scopeKey)
        .setProcessDefinitionKey(processDefinitionKey)
        .setProcessInstanceKey(processInstanceKey)
        .setBpmnProcessId(bpmnProcessId)
        .setName(name)
        .setValue(value, valueOffset, valueLength);

    setLocalVariable(variableRecord);
  }

  private void setLocalVariable(final VariableRecord record) {
    final VariableInstance variableInstance =
        variableState.getVariableInstanceLocal(record.getScopeKey(), record.getNameBuffer());
    if (variableInstance == null) {
      final long key = keyGenerator.nextKey();
      stateWriter.appendFollowUpEvent(key, VariableIntent.CREATED, record);
    } else if (!variableInstance.getValue().equals(record.getValueBuffer())) {
      stateWriter.appendFollowUpEvent(variableInstance.getKey(), VariableIntent.UPDATED, record);
    }
  }

  private void applyEntryToRecord(final DocumentEntry entry) {
    variableRecord.setName(entry.getName()).setValue(entry.getValue());
  }
}
