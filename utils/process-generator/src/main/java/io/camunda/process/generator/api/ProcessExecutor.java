/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.generator.api;

import io.camunda.process.generator.execution.BroadcastSignalStep;
import io.camunda.process.generator.execution.CompleteJobStep;
import io.camunda.process.generator.execution.CompleteUserTaskStep;
import io.camunda.process.generator.execution.CreateProcessInstanceStep;
import io.camunda.process.generator.execution.ProcessExecutionStep;
import io.camunda.process.generator.execution.PublishMessageStep;

public interface ProcessExecutor {

  default void execute(final ProcessExecutionStep step) {
    switch (step) {
      case final CreateProcessInstanceStep createProcessInstanceStep ->
          execute(createProcessInstanceStep);
      case final BroadcastSignalStep broadcastSignalStep -> execute(broadcastSignalStep);
      case final CompleteJobStep completeJobStep -> execute(completeJobStep);
      case final CompleteUserTaskStep completeUserTaskStep -> execute(completeUserTaskStep);
      case final PublishMessageStep publishMessageStep -> execute(publishMessageStep);
      default ->
          throw new IllegalArgumentException("Unknown step type: " + step.getClass().getName());
    }
  }

  void execute(CreateProcessInstanceStep step);

  void execute(BroadcastSignalStep step);

  void execute(CompleteJobStep step);

  void execute(CompleteUserTaskStep step);

  void execute(PublishMessageStep step);
}
