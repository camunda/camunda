/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

/**
 * Defines configurations for jobs in the engine. The prefix for this class is
 * camunda.processing.engine.job.
 */
public class EngineJob {

  private boolean includeVariablesInJobCompletedEvent = false;

  /**
   * Configuration option to include variables in the job completed event. This configuration can be
   * accessed via the environment variable: <br>
   * {@code camunda.processing.engine.job.include-variables-in-job-completed-event}.
   *
   * <p>Defaults to {@code false} to prevent job completed events from failing due to excessive
   * batch record size.
   *
   * @return {@code true} if variables should be included in the job completed event, {@code false}
   *     otherwise
   */
  public boolean isIncludeVariablesInJobCompletedEvent() {
    return includeVariablesInJobCompletedEvent;
  }

  public void setIncludeVariablesInJobCompletedEvent(
      final boolean includeVariablesInJobCompletedEvent) {
    this.includeVariablesInJobCompletedEvent = includeVariablesInJobCompletedEvent;
  }
}
