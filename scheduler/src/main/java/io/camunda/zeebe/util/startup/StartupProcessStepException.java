/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.util.startup;

public final class StartupProcessStepException extends Exception {
  private final String stepName;

  StartupProcessStepException(final String stepName, final Throwable cause) {
    super(String.format("Bootstrap step %s failed", stepName), cause);
    this.stepName = stepName;
  }

  public String getStepName() {
    return stepName;
  }
}
