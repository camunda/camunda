/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.el;

import java.util.Map;
import java.util.stream.Stream;

/** The context for evaluating an expression. */
public interface EvaluationContext {
  /**
   * Returns the value of the variable with the given name.
   *
   * @param variableName the name of the variable
   * @return the variable value as MessagePack encoded buffer, or {@code null} if the variable is
   *     not present
   */
  Object getVariable(String variableName);

  default Stream<String> getVariables() {
    return Stream.empty();
  }

  static <A> EvaluationContext ofMap(final Map<String, A> map) {
    return new EvaluationContext() {
      @Override
      public Object getVariable(final String variableName) {
        return map.get(variableName);
      }

      @Override
      public Stream<String> getVariables() {
        return map.keySet().stream();
      }
    };
  }

  static EvaluationContext empty() {
    return variableName -> null;
  }
}
