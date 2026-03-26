/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.publish.flowcontrol;

public final class CompositeFlowControl implements FlowControl {

  private final FlowControl[] controls;

  public CompositeFlowControl(final FlowControl... controls) {
    this.controls = controls;
  }

  @Override
  public boolean tryAcquire(final int entryCount, final int bytesLength) {
    for (int i = 0; i < controls.length; i++) {
      if (!controls[i].tryAcquire(entryCount, bytesLength)) {
        for (int j = 0; j < i; j++) {
          controls[j].onCompleted(entryCount, bytesLength);
        }
        return false;
      }
    }
    return true;
  }

  @Override
  public void onCompleted(final int entryCount, final int bytesLength) {
    for (final var control : controls) {
      control.onCompleted(entryCount, bytesLength);
    }
  }
}
