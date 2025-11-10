/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.variable;

import io.camunda.zeebe.engine.processing.common.ExpressionProcessor;
import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableCatchEvent;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.state.immutable.ConditionSubscriptionState;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.immutable.VariableState;
import io.camunda.zeebe.engine.state.variable.DocumentEntry;
import io.camunda.zeebe.engine.state.variable.IndexedDocument;
import io.camunda.zeebe.engine.state.variable.VariableInstance;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableRecord;
import io.camunda.zeebe.protocol.record.intent.ConditionSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.Either;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
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
  private final TypedCommandWriter commandWriter;
  private final KeyGenerator keyGenerator;
  private final ExpressionProcessor expressionProcessor;
  private final ElementInstanceState elementInstanceState;
  private final ConditionSubscriptionState conditionSubscriptionState;
  private final ProcessState processState;

  private final IndexedDocument indexedDocument = new IndexedDocument();
  private final VariableRecord variableRecord = new VariableRecord();

  public VariableBehavior(
      final VariableState variableState,
      final StateWriter stateWriter,
      final TypedCommandWriter commandWriter,
      final KeyGenerator keyGenerator,
      final ExpressionProcessor expressionProcessor,
      final ElementInstanceState elementInstanceState,
      final ConditionSubscriptionState conditionSubscriptionState,
      final ProcessState processState) {
    this.variableState = variableState;
    this.stateWriter = stateWriter;
    this.commandWriter = commandWriter;
    this.keyGenerator = keyGenerator;
    this.expressionProcessor = expressionProcessor;
    this.elementInstanceState = elementInstanceState;
    this.conditionSubscriptionState = conditionSubscriptionState;
    this.processState = processState;
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
      final String tenantId,
      final DirectBuffer document) {
    indexedDocument.index(document);
    if (indexedDocument.isEmpty()) {
      return;
    }

    final List<VariableRecord> allVariablesChanged = new ArrayList<>();

    variableRecord
        .setScopeKey(scopeKey)
        .setProcessDefinitionKey(processDefinitionKey)
        .setProcessInstanceKey(processInstanceKey)
        .setBpmnProcessId(bpmnProcessId)
        .setTenantId(tenantId);
    for (final DocumentEntry entry : indexedDocument) {
      applyEntryToRecord(entry);
      final List<VariableRecord> variablesChanged = setLocalVariable(variableRecord);
      allVariablesChanged.addAll(variablesChanged);
    }

    evaluateConditionalsIfExists(allVariablesChanged);
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
      final String tenantId,
      final DirectBuffer document) {
    indexedDocument.index(document);
    if (indexedDocument.isEmpty()) {
      return;
    }

    long currentScope = scopeKey;
    long parentScope;
    final List<VariableRecord> allVariablesChanged = new ArrayList<>();

    variableRecord
        .setProcessDefinitionKey(processDefinitionKey)
        .setProcessInstanceKey(processInstanceKey)
        .setBpmnProcessId(bpmnProcessId)
        .setTenantId(tenantId);
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

          allVariablesChanged.add(getVariableRecordCopy(variableRecord));
        }
      }

      currentScope = parentScope;
    }

    variableRecord.setScopeKey(currentScope);
    for (final DocumentEntry entry : indexedDocument) {
      applyEntryToRecord(entry);
      final List<VariableRecord> variablesChanged = setLocalVariable(variableRecord);
      allVariablesChanged.addAll(variablesChanged);
    }

    evaluateConditionalsIfExists(allVariablesChanged);

    // collect scope keys and variables in a ordered list (e.g. ArrayList) during the traversal
    // then iterate over that list to evaluate conditionals via evaluateConditionalsIfExists
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
      final String tenantId,
      final DirectBuffer name,
      final DirectBuffer value,
      final int valueOffset,
      final int valueLength) {

    variableRecord
        .setScopeKey(scopeKey)
        .setProcessDefinitionKey(processDefinitionKey)
        .setProcessInstanceKey(processInstanceKey)
        .setBpmnProcessId(bpmnProcessId)
        .setTenantId(tenantId)
        .setName(name)
        .setValue(value, valueOffset, valueLength);

    final List<VariableRecord> variablesChanged = setLocalVariable(variableRecord);

    evaluateConditionalsIfExists(variablesChanged);
  }

  private List<VariableRecord> setLocalVariable(final VariableRecord record) {
    final List<VariableRecord> variablesChanged = new ArrayList<>();
    final VariableInstance variableInstance =
        variableState.getVariableInstanceLocal(record.getScopeKey(), record.getNameBuffer());
    if (variableInstance == null) {
      final long key = keyGenerator.nextKey();
      stateWriter.appendFollowUpEvent(key, VariableIntent.CREATED, record);
      variablesChanged.add(getVariableRecordCopy(record));
    } else if (!variableInstance.getValue().equals(record.getValueBuffer())) {
      stateWriter.appendFollowUpEvent(variableInstance.getKey(), VariableIntent.UPDATED, record);
      variablesChanged.add(getVariableRecordCopy(record));
    }

    return variablesChanged;
  }

  private void applyEntryToRecord(final DocumentEntry entry) {
    variableRecord.setName(entry.getName()).setValue(entry.getValue());
  }

  private void evaluateConditionalsIfExists(final List<VariableRecord> records) {
    // each subscription should be triggered only once per document merge
    final Set<Long> triggeredSubscriptionKeys = new HashSet<>();
    // for each created/updated variable
    for (final VariableRecord record : records) {
      // start top-down traversal from the scope key where the variable was set
      final var scopes = new ArrayDeque<>(List.of(record.getScopeKey()));
      final Set<Long> visitedScopeKeys = new HashSet<>();

      while (!scopes.isEmpty()) {
        final var scopeKey = scopes.poll();
        if (visitedScopeKeys.contains(scopeKey)) {
          continue;
        }
        visitedScopeKeys.add(scopeKey);

        // O(n) but technically O(1) due to how rocksDB stores prefixes
        final var subscriptions =
            conditionSubscriptionState.getSubscriptionsByScopeKey(record.getTenantId(), scopeKey);
        if (!subscriptions.isEmpty()) {
          // O(n) x 2 (lookup + evaluation)
          subscriptions.forEach(
              subscription -> {
                final var subscriptionRecord = subscription.getRecord();
                final var catchEvent =
                    processState.getFlowElement(
                        subscriptionRecord.getProcessDefinitionKey(),
                        subscriptionRecord.getTenantId(),
                        subscriptionRecord.getCatchEventIdBuffer(),
                        ExecutableCatchEvent.class);
                final Either<Failure, Boolean> evaluation =
                    expressionProcessor.evaluateBooleanExpression(
                        catchEvent.getCondition().getConditionExpression(), scopeKey);
                if (evaluation.isRight()
                    && evaluation.get().equals(true)
                    && !triggeredSubscriptionKeys.contains(subscription.getKey())) {
                  commandWriter.appendFollowUpCommand(
                      subscription.getKey(),
                      ConditionSubscriptionIntent.TRIGGER,
                      subscriptionRecord);
                  triggeredSubscriptionKeys.add(subscription.getKey());
                }
              });
        }

        // O(m) but technically O(1) due to how rocksDB stores prefixes
        elementInstanceState
            .getChildren(scopeKey)
            .forEach(elementInstance -> scopes.add(elementInstance.getKey()));
      }
    }
    // retrieve all children element instances of this scope key
    // note: there is a need to traverse child instances recursively as done in process instance
    // migration
    // for each child scope
    // check if there are any conditionals to evaluate for this scope using conditional state
    // filtered by scopeKey, variable name and operation type
    // for each conditional found, evaluate it.
    // If the conditional is met, write the corresponding activation event
    // if the conditional is interrupting, do not continue evaluation for further conditionals in
    // child instances
  }

  private VariableRecord getVariableRecordCopy(final VariableRecord variableRecord) {
    final VariableRecord variableRecordCopy = new VariableRecord();
    variableRecordCopy.copyFrom(variableRecord);

    return variableRecordCopy;
  }
}
