/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.identity;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.security.configuration.ConfiguredTenant;
import io.camunda.security.validation.IdentityInitializationException;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;

@ZeebeIntegration
public class InitializeInvalidTenantIT {

  private static final ConfiguredTenant INVALID_TENANT =
      new ConfiguredTenant(
          null,
          "Tenant 1",
          "something to test",
          List.of(),
          List.of(),
          List.of(),
          List.of(),
          List.of());

  @Test
  void shouldFailToStartWithInvalidTenant() {
    // given - An invalid configured authorization
    final var broker =
        new TestStandaloneBroker()
            .withBasicAuth()
            .withSecurityConfig(
                conf -> conf.getInitialization().setTenants(List.of(INVALID_TENANT)));

    // when/then - application startup should fail with the expected exception
    assertThatThrownBy(broker::start)
        .isInstanceOf(BeanCreationException.class)
        .hasRootCauseInstanceOf(IdentityInitializationException.class);
  }
}
