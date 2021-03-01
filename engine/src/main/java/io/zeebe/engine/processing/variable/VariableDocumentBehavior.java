/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.variable;

import io.zeebe.engine.state.mutable.MutableVariableState;
import io.zeebe.engine.state.variable.DocumentEntry;
import io.zeebe.engine.state.variable.IndexedDocument;
import io.zeebe.engine.state.variable.VariableInstance;
import java.util.Iterator;
import org.agrona.DirectBuffer;

public final class VariableDocumentBehavior {

  private final MutableVariableState variableState;
  private final IndexedDocument indexedDocument = new IndexedDocument();

  public VariableDocumentBehavior(final MutableVariableState variableState) {
    this.variableState = variableState;
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
   * @param workflowKey the workflow key to be associated with each variable
   * @param document the document to merge
   */
  public void mergeLocalDocument(
      final long scopeKey, final long workflowKey, final DirectBuffer document) {
    indexedDocument.index(document);
    if (indexedDocument.isEmpty()) {
      return;
    }

    for (final DocumentEntry entry : indexedDocument) {
      variableState.setVariableLocal(scopeKey, workflowKey, entry.getName(), entry.getValue());
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
   * @param scopeKey the bottom-most scope key to start with
   * @param workflowKey the workflow key to be associated with each variable
   * @param document the document to merge
   */
  public void mergeDocument(
      final long scopeKey, final long workflowKey, final DirectBuffer document) {
    indexedDocument.index(document);
    if (indexedDocument.isEmpty()) {
      return;
    }

    long currentScope = scopeKey;
    long parentScope;

    while ((parentScope = variableState.getParentScopeKey(currentScope)) > 0
        && !indexedDocument.isEmpty()) {
      final Iterator<DocumentEntry> entryIterator = indexedDocument.iterator();

      while (entryIterator.hasNext()) {
        final DocumentEntry entry = entryIterator.next();
        // seems unnecessary to grab a reference yet, but will soon be used to produce follow up
        // events with the right key
        final VariableInstance variableInstance =
            variableState.getVariableInstanceLocal(currentScope, entry.getName());

        if (variableInstance != null) {
          variableState.setVariableLocal(
              currentScope, workflowKey, entry.getName(), entry.getValue());
          entryIterator.remove();
        }
      }
      currentScope = parentScope;
    }

    for (final DocumentEntry entry : indexedDocument) {
      variableState.setVariableLocal(currentScope, workflowKey, entry.getName(), entry.getValue());
    }
  }
}
