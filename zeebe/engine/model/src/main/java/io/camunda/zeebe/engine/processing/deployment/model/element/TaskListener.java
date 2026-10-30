/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.element;

import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskListenerEventType;

public class TaskListener {

  private ZeebeTaskListenerEventType eventType;
  private JobWorkerProperties jobWorkerProperties = new JobWorkerProperties();

  public ZeebeTaskListenerEventType getEventType() {
    return eventType;
  }

  public void setEventType(final ZeebeTaskListenerEventType eventType) {
    this.eventType = eventType;
  }

  public JobWorkerProperties getJobWorkerProperties() {
    return jobWorkerProperties;
  }

  public void setJobWorkerProperties(final JobWorkerProperties jobWorkerProperties) {
    this.jobWorkerProperties = jobWorkerProperties;
  }
}
