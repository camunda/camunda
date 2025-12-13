/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.identity;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.security.configuration.ConfiguredAuthorization;
import io.camunda.zeebe.engine.processing.identity.initialize.IdentityInitializationException;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;

/**
 * Acceptance test to verify that Basic Authentication fails fast when configured with no secondary
 * storage. This test validates that the application startup fails with a clear error when
 * database.type=none and Basic Authentication is configured.
 */
@ZeebeIntegration
public class InitializeInvalidAuthorizationIT {

  private static final ConfiguredAuthorization INVALID_AUTH =
      new ConfiguredAuthorization(
          AuthorizationOwnerType.USER,
          "john.doe",
          null,
          "*",
          Set.of(PermissionType.READ_PROCESS_INSTANCE, PermissionType.CREATE_PROCESS_INSTANCE));

  @Test
  void shouldFailToStartWithInvalidAuthorization() {
    // given - An invalid configured authorization
    final var broker =
        new TestStandaloneBroker()
            .withBasicAuth()
            .withSecurityConfig(
                conf -> conf.getInitialization().setAuthorizations(List.of(INVALID_AUTH)));

    // when/then - application startup should fail with the expected exception
    assertThatThrownBy(broker::start)
        .isInstanceOf(BeanCreationException.class)
        .hasRootCauseInstanceOf(IdentityInitializationException.class);
  }
}
