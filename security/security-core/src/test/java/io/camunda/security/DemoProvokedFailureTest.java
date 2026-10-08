/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security;

import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;

// DEMO ONLY (camunda/infra-global-github-actions#850): provoked failure in a non-container job
class DemoProvokedFailureTest {

  @Test
  void shouldFailOnPurpose() {
    fail("demo: provoked failure for submit-test-status in non-container job");
  }
}
