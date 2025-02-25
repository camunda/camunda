/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.element;

import io.camunda.zeebe.el.Expression;
import java.util.List;

/**
 * The properties of an element that is based on a job and should be processed by a job worker. For
 * example, a service task.
 */
public class JobWorkerProperties extends UserTaskProperties {

  private Expression type;
  private Expression retries;
  private List<LinkedResource> linkedResources;

  public Expression getType() {
    return type;
  }

  public void setType(final Expression type) {
    this.type = type;
  }

  public Expression getRetries() {
    return retries;
  }

  public void setRetries(final Expression retries) {
    this.retries = retries;
  }

  public List<LinkedResource> getLinkedResources() {
    return linkedResources;
  }

  public void setLinkedResources(final List<LinkedResource> linkedResources) {
    this.linkedResources = linkedResources;
  }
}
