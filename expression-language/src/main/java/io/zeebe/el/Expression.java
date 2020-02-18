/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.el;

/** A parsed expression. */
public interface Expression {

  /** @return the (raw) expression as string */
  String getExpression();

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
