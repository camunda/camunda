/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.deployment.model.element;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class ExecutableError extends AbstractFlowElement {

  private final DirectBuffer errorCode = new UnsafeBuffer();

  public ExecutableError(final String id) {
    super(id);
  }

  public DirectBuffer getErrorCode() {
    return errorCode;
  }

  public void setErrorCode(final DirectBuffer errorCode) {
    this.errorCode.wrap(errorCode);
  }
}
