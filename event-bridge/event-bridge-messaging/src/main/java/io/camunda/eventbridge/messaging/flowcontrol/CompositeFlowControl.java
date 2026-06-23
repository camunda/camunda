/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.messaging.flowcontrol;

public final class CompositeFlowControl implements FlowControl {

  private final FlowControl[] controls;

  public CompositeFlowControl(final FlowControl... controls) {
    this.controls = controls;
  }

  @Override
  public boolean tryAcquire(final int permits) {
    for (int i = 0; i < controls.length; i++) {
      if (!controls[i].tryAcquire(permits)) {
        for (int j = 0; j < i; j++) {
          controls[j].release(permits);
        }
        return false;
      }
    }
    return true;
  }

  @Override
  public void release(final int permits) {
    for (final var control : controls) {
      control.release(permits);
    }
  }
}
