/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.deployment.model.element;

public class ExecutableEndEvent extends ExecutableFlowNode {

  private ExecutableError error;

  public ExecutableEndEvent(final String id) {
    super(id);
  }

  public ExecutableError getError() {
    return error;
  }

  public void setError(final ExecutableError error) {
    this.error = error;
  }

  public boolean hasError() {
    return error != null;
  }
}
