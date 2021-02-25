/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.state.mutable;

import io.zeebe.engine.state.immutable.VariableState;
import org.agrona.DirectBuffer;

public interface MutableVariableState extends VariableState {

  void setVariablesLocalFromDocument(long scopeKey, long workflowKey, DirectBuffer document);

  void setVariableLocal(long scopeKey, long workflowKey, DirectBuffer name, DirectBuffer value);

  void setVariableLocal(
      long scopeKey,
      long workflowKey,
      DirectBuffer name,
      DirectBuffer value,
      int valueOffset,
      int valueLength);

  void setVariableLocal(
      long scopeKey,
      long workflowKey,
      DirectBuffer name,
      int nameOffset,
      int nameLength,
      DirectBuffer value,
      int valueOffset,
      int valueLength);

  void setVariablesFromDocument(long scopeKey, long workflowKey, DirectBuffer document);

  void createScope(long childKey, long parentKey);

  void removeScope(long scopeKey);

  void removeAllVariables(long scopeKey);

  void setTemporaryVariables(long scopeKey, DirectBuffer variables);

  void removeTemporaryVariables(long scopeKey);
}
