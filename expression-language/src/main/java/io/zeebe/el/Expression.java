/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.el;

import java.util.Optional;

/** A parsed expression. */
public interface Expression {

  /** @return the (raw) expression as string */
  String getExpression();

  /**
   * @return optional of the name of the variable if expression is a single variable or a property
   *     of a single variable, otherwise empty
   */
  Optional<String> getVariableName();

  /**
   * @return {@code true} if it is an static expression that does not require additional context
   *     variables
   */
  boolean isStatic();

  /** @return {@code true} if the expression is valid and can be evaluated */
  boolean isValid();

  /**
   * Returns the reason why the expression is not valid. Use {@link #isValid()} to check if the
   * expression is valid or not.
   *
   * @return the failure message if the expression is not valid, otherwise {@code null}
   */
  String getFailureMessage();
}
