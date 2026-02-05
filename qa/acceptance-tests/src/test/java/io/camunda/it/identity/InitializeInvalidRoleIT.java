/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.identity;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.security.configuration.ConfiguredRole;
import io.camunda.security.validation.IdentityInitializationException;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;

@ZeebeIntegration
public class InitializeInvalidRoleIT {

  private static final ConfiguredRole INVALID_ROLE =
      new ConfiguredRole(
          null, // Invalid: roleId is null
          "Role 1",
          "something to test",
          List.of(),
          List.of(),
          List.of(),
          List.of());

  private static final ConfiguredRole ROLE_INVALID_MEMBER_ID =
      new ConfiguredRole(
          "role-1",
          "Role 1",
          "something to test",
          List.of(),
          List.of("client-1!!!"), // Invalid: memberId has invalid characters
          List.of(),
          List.of());

  @Test
  void shouldFailToStartWithInvalidRole() {
    // given - An invalid configured role
    final var broker =
        new TestStandaloneBroker()
            .withBasicAuth()
            .withSecurityConfig(conf -> conf.getInitialization().setRoles(List.of(INVALID_ROLE)));

    // when/then - application startup should fail with the expected exception
    assertThatThrownBy(broker::start)
        .isInstanceOf(BeanCreationException.class)
        .hasRootCauseInstanceOf(IdentityInitializationException.class);
  }

  @Test
  void shouldFailToStartWithInvalidRoleMember() {
    // given - An invalid configured role
    final var broker =
        new TestStandaloneBroker()
            .withBasicAuth()
            .withSecurityConfig(
                conf -> conf.getInitialization().setRoles(List.of(ROLE_INVALID_MEMBER_ID)));

    // when/then - application startup should fail with the expected exception
    assertThatThrownBy(broker::start)
        .isInstanceOf(BeanCreationException.class)
        .hasRootCauseInstanceOf(IdentityInitializationException.class);
  }
}
