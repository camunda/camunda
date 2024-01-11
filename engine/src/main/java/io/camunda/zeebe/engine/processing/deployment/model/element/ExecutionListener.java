/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.deployment.model.element;

import io.camunda.zeebe.protocol.record.value.ExecutionListenerEventType;

public class ExecutionListener {
  private ExecutionListenerEventType eventType;
  private JobWorkerProperties jobWorkerProperties = new JobWorkerProperties();

  public ExecutionListenerEventType getEventType() {
    return eventType;
  }

  public void setEventType(final ExecutionListenerEventType eventType) {
    this.eventType = eventType;
  }

  public JobWorkerProperties getJobWorkerProperties() {
    return jobWorkerProperties;
  }

  public void setJobWorkerProperties(final JobWorkerProperties jobWorkerProperties) {
    this.jobWorkerProperties = jobWorkerProperties;
  }
}
