/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.mcp;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

abstract class AuthenticatedMcpServerTest extends McpServerAuthenticationTest {

  @Test
  void failsOnUnauthenticatedRequest() {
    assertThatThrownBy(
            () -> {
              try (final var client = createMcpClient(null)) {
                client.listTools();
              }
            })
        .isNotNull()
        .hasMessage("Client failed to initialize listing tools");
  }
}
