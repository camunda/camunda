/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.generator.execution;

public class CreateProcessInstanceStep implements ProcessExecutionStep {

  private final String processId;

  public CreateProcessInstanceStep(final String processId) {
    this.processId = processId;
  }

  @Override
  public String elementId() {
    return processId; // TODO getElementId returns processId??? Improve naming
  }

  @Override
  public String description() {
    return "Create a process instance with process id '%s'.".formatted(processId);
  }
}
