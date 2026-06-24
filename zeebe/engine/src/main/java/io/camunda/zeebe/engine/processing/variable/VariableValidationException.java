/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.variable;

import io.camunda.zeebe.engine.processing.common.ValidationException;

public class VariableValidationException extends ValidationException {
  public VariableValidationException(final String message) {
    super(message);
  }

  public VariableValidationException(final String message, final Throwable cause) {
    super(message, cause);
  }
}
