/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

final class ITestSomethingImpossible {
  private static final AtomicBoolean TOGGLE = new AtomicBoolean();
  private static final AtomicBoolean TOGGLE_PRIME = new AtomicBoolean();

  @Test
  void aFlakyTest() {
    assertThat(TOGGLE.getAndSet(true))
        .as("should fail on the first try but pass on the second")
        .isTrue();
  }

  @Test
  void aSecondFlakyTest() {
    assertThat(TOGGLE_PRIME.getAndSet(true))
        .as("should fail on the first try but pass on the second")
        .isTrue();
  }
}
