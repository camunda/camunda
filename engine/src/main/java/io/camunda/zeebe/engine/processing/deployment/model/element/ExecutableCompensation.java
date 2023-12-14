/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.deployment.model.element;

public class ExecutableCompensation extends AbstractFlowElement {

  private ExecutableActivity compensationHandler;

  public ExecutableCompensation(final String id) {
    super(id);
  }

  public ExecutableActivity getCompensationHandler() {
    return compensationHandler;
  }

  public void setCompensationHandler(final ExecutableActivity compensationHandler) {
    this.compensationHandler = compensationHandler;
  }
}
