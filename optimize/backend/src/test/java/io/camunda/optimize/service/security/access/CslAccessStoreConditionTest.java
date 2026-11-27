/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.security.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.mock.env.MockEnvironment;

class CslAccessStoreConditionTest {

  @Test
  void shouldKeepManagementIdentityByDefault() {
    // when / then
    assertThat(matches(environment("ccsm"))).isFalse();
  }

  @Test
  void shouldUseTheStoreInSelfManagedWhenManagementIdentityIsSwitchedOff() {
    // when / then
    assertThat(
            matches(
                environment("ccsm")
                    .withProperty(CslAccessStoreCondition.MANAGEMENT_IDENTITY_ENABLED, "false")))
        .isTrue();
  }

  @Test
  void shouldNeverUseTheStoreInSaas() {
    // when / then
    assertThat(
            matches(
                environment("cloud")
                    .withProperty(CslAccessStoreCondition.MANAGEMENT_IDENTITY_ENABLED, "false")))
        .isFalse();
  }

  @Test
  void shouldNotUseTheStoreWithoutCsl() {
    // when / then
    assertThat(
            matches(
                environment("ccsm")
                    .withProperty(CslAccessStoreCondition.MANAGEMENT_IDENTITY_ENABLED, "false")
                    .withProperty(CslAccessStoreCondition.CSL_ENABLED, "false")))
        .isFalse();
  }

  private static MockEnvironment environment(final String profile) {
    final MockEnvironment environment = new MockEnvironment();
    environment.setActiveProfiles(profile);
    return environment;
  }

  private static boolean matches(final MockEnvironment environment) {
    final ConditionContext context = mock(ConditionContext.class);
    when(context.getEnvironment()).thenReturn(environment);
    return new CslAccessStoreCondition().matches(context, mock(AnnotatedTypeMetadata.class));
  }
}
