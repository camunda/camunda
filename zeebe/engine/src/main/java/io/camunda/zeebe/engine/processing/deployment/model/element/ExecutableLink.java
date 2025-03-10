/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.element;

import org.agrona.DirectBuffer;

public class ExecutableLink extends AbstractFlowElement {

  private DirectBuffer name;

  private ExecutableCatchEventElement catchEventElement;

  public ExecutableLink(final String id) {
    super(id);
  }

  @Override
  public DirectBuffer getName() {
    return name;
  }

  @Override
  public void setName(final DirectBuffer name) {
    this.name = name;
  }

  public void setCatchEvent(final ExecutableCatchEventElement catchEventElement) {
    this.catchEventElement = catchEventElement;
  }

  public ExecutableCatchEventElement getCatchEventElement() {
    return catchEventElement;
  }
}
