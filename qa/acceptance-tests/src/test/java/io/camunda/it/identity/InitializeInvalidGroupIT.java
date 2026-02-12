/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.identity;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.security.configuration.ConfiguredGroup;
import io.camunda.security.validation.IdentityInitializationException;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;

@ZeebeIntegration
public class InitializeInvalidGroupIT {

  private static final ConfiguredGroup INVALID_GROUP =
      new ConfiguredGroup(
          null, "Group 1", "something to test", List.of(), List.of(), List.of(), List.of());

  @Test
  void shouldFailToStartWithInvalidGroup() {
    // given - An invalid configured group
    final var broker =
        new TestStandaloneBroker()
            .withBasicAuth()
            .withSecurityConfig(conf -> conf.getInitialization().setGroups(List.of(INVALID_GROUP)));

    // when/then - application startup should fail with the expected exception
    assertThatThrownBy(broker::start)
        .isInstanceOf(BeanCreationException.class)
        .hasRootCauseInstanceOf(IdentityInitializationException.class);
  }
}
