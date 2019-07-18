/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.message;

import io.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;

public class MessageCorrelationKeyContext {
  private long variablesScopeKey;
  private DirectBuffer variablesDocument;
  private VariablesDocumentSupplier variablesSupplier;

  public MessageCorrelationKeyContext() {}

  public MessageCorrelationKeyContext(
      VariablesDocumentSupplier variablesSupplier, long variablesScopeKey) {
    this.variablesSupplier = variablesSupplier;
    this.variablesScopeKey = variablesScopeKey;
  }

  public long getVariablesScopeKey() {
    return variablesScopeKey;
  }

  public MessageCorrelationKeyContext setVariablesScopeKey(long variablesScopeKey) {
    this.variablesScopeKey = variablesScopeKey;
    return this;
  }

  public MessageCorrelationKeyContext reset() {
    variablesSupplier = null;
    variablesScopeKey = -1;
    variablesDocument = null;

    return this;
  }

  public MessageCorrelationKeyContext setVariablesSupplier(
      VariablesDocumentSupplier variablesSupplier) {
    this.variablesSupplier = variablesSupplier;
    return this;
  }

  public DirectBuffer getVariablesAsDocument() {
    assert variablesScopeKey >= 0 : "no variables scope key given";
    assert variablesSupplier != null : "no variables supplier given";

    if (variablesDocument == null) {
      final DirectBuffer document = variablesSupplier.getVariablesAsDocument(variablesScopeKey);

      if (document != null) { // must be cloned in case the supplier reuses the given buffer
        variablesDocument = BufferUtil.cloneBuffer(document);
      }
    }

    return variablesDocument;
  }

  @FunctionalInterface
  public interface VariablesDocumentSupplier {
    DirectBuffer getVariablesAsDocument(long scopeKey);
  }
}
