/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.common.processing.deployment.model.element;

public class ExecutableJobWorkerTask extends ExecutableActivity
    implements ExecutableJobWorkerElement {

  private JobWorkerProperties jobWorkerProperties;

  public ExecutableJobWorkerTask(final String id) {
    super(id);
  }

  @Override
  public JobWorkerProperties getJobWorkerProperties() {
    return jobWorkerProperties;
  }

  @Override
  public void setJobWorkerProperties(final JobWorkerProperties jobWorkerProperties) {
    this.jobWorkerProperties = jobWorkerProperties;
  }
}
