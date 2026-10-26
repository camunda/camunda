/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dmn.impl;

import io.camunda.zeebe.dmn.DecisionContext;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Simple Decision Context based on a map of variables, where the key is the name of the variable.
 */
public final class VariablesContext implements DecisionContext {

  private final Map<String, @Nullable Object> variables;

  public VariablesContext(final Map<String, @Nullable Object> variables) {
    this.variables = variables;
  }

  @Override
  public Map<String, @Nullable Object> toMap() {
    return variables;
  }
}
