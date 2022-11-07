/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.deployment.model.element;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class ExecutableEscalation extends AbstractFlowElement {

  private final DirectBuffer escalationCode = new UnsafeBuffer();

  public ExecutableEscalation(final String id) {
    super(id);
  }

  public DirectBuffer getEscalationCode() {
    return escalationCode;
  }

  public void setEscalationCode(final DirectBuffer escalationCode) {
    this.escalationCode.wrap(escalationCode);
  }
}
