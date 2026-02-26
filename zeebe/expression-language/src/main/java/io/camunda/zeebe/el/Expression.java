/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.el;

import java.util.List;
import java.util.Optional;

/** A parsed expression. */
public interface Expression {

  /**
   * @return the (raw) expression as string
   */
  String getExpression();

  /**
   * @return optional of the name of the variable if expression is a single variable or a property
   *     of a single variable, otherwise empty
   */
  Optional<String> getVariableName();

  /**
   * Returns all top-level variable names referenced by this expression. For path expressions like
   * {@code x.y}, only the root variable {@code x} is returned.
   *
   * @return an ordered list of distinct top-level variable names, or an empty list if none can be
   *     determined (e.g. for static or invalid expressions)
   */
  default List<String> getVariableNames() {
    return List.of();
  }

  /**
   * @return {@code true} if it is a static expression that does not require additional context
   *     variables
   */
  boolean isStatic();

  /**
   * @return {@code true} if the expression is valid and can be evaluated
   */
  boolean isValid();

  /**
   * Returns the reason why the expression is not valid. Use {@link #isValid()} to check if the
   * expression is valid or not.
   *
   * @return the failure message if the expression is not valid, otherwise {@code null}
   */
  String getFailureMessage();
}
