/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.client.role;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.protocol.rest.GroupUpdateRequest;
import io.camunda.client.util.ClientRestTest;
import org.junit.jupiter.api.Test;

public class UpdateRoleTest extends ClientRestTest {
  private static final String ROLE_ID = "roleId";
  private static final String UPDATED_NAME = "Updated Role Name";
  private static final String UPDATED_DESCRIPTION = "Updated Role Description";

  @Test
  void shouldUpdateRole() {
    // when
    client
        .newUpdateRoleCommand(ROLE_ID)
        .name(UPDATED_NAME)
        .description(UPDATED_DESCRIPTION)
        .send()
        .join();

    // then
    final GroupUpdateRequest request = gatewayService.getLastRequest(GroupUpdateRequest.class);
    assertThat(request.getName()).isEqualTo(UPDATED_NAME);
    assertThat(request.getDescription()).isEqualTo(UPDATED_DESCRIPTION);
  }

  @Test
  void shouldRaiseExceptionOnEmptyName() {
    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newUpdateRoleCommand(ROLE_ID)
                    .name("")
                    .description(UPDATED_DESCRIPTION)
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("name must not be empty");
  }

  @Test
  void shouldRaiseExceptionOnNullName() {
    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newUpdateRoleCommand(ROLE_ID)
                    .name(null)
                    .description(UPDATED_DESCRIPTION)
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("name must not be null");
  }

  @Test
  void shouldRaiseExceptionOnNullDescription() {
    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newUpdateRoleCommand(ROLE_ID)
                    .name(UPDATED_NAME)
                    .description(null)
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("description must not be null");
  }
}
