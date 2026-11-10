/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.mailbox.api;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

final class MessageValidationTest {

  @Test
  void shouldRejectBlankGatewayId() {
    // when / then
    assertThatThrownBy(() -> new GatewayId(" "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("gateway id");
  }

  @Test
  void shouldRejectBlankSender() {
    // when / then
    assertThatThrownBy(
            () -> new NewInboxMessage(1, MessageKind.INTER_PARTITION_COMMAND, "", 1, new byte[0]))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("sender");
  }
}
