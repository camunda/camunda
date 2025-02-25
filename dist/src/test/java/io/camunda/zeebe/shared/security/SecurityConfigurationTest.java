/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.MethodMetadata;

public class SecurityConfigurationTest {

  @Mock private Environment environment;

  private ConditionContext conditionContext;
  private MethodMetadata methodMetadata;
  private final SecurityConfiguration.GatewaySecurityAuthenticationEnabledCondition condition =
      new SecurityConfiguration.GatewaySecurityAuthenticationEnabledCondition();

  @BeforeEach
  void setUp() throws Exception {
    MockitoAnnotations.openMocks(this).close();
    conditionContext = mock(ConditionContext.class);
    methodMetadata = mock(MethodMetadata.class);
    when(conditionContext.getEnvironment()).thenReturn(environment);
  }

  @Test
  void doesNotMatchWhenStandaloneGatewayEnabledAndSecurityModeIsNone() {
    when(environment.getProperty("zeebe.gateway.enabled")).thenReturn("true");
    when(environment.getProperty("zeebe.gateway.security.authentication.mode")).thenReturn("none");

    final boolean result = condition.matches(conditionContext, methodMetadata);
    assertThat(result).isFalse();
  }

  @Test
  void matchesWhenStandaloneGatewayEnabledAndSecurityModeIsNotNone() {
    when(environment.getProperty("zeebe.gateway.enabled")).thenReturn("true");
    when(environment.getProperty("zeebe.gateway.security.authentication.mode"))
        .thenReturn("identity");

    final boolean result = condition.matches(conditionContext, methodMetadata);
    assertThat(result).isTrue();
  }

  @Test
  void doesNotOverrideSecurityWhenStandaloneGatewayEnabled() {
    when(environment.getProperty("zeebe.gateway.enabled")).thenReturn("true");
    when(environment.getProperty("zeebe.broker.gateway.security.authentication.mode"))
        .thenReturn("identity");

    final boolean result = condition.matches(conditionContext, methodMetadata);
    assertThat(result).isTrue();
  }

  @Test
  void doesNotMatchWhenEmbeddedGatewayEnabledAndSecurityModeIsNone() {
    when(environment.getProperty("zeebe.broker.gateway.enabled")).thenReturn("true");
    when(environment.getProperty("zeebe.broker.gateway.security.authentication.mode"))
        .thenReturn("none");

    final boolean result = condition.matches(conditionContext, methodMetadata);
    assertThat(result).isFalse();
  }

  @Test
  void matchesWhenEmbeddedGatewayEnabledAndSecurityModeIsNotNone() {
    when(environment.getProperty("zeebe.broker.gateway.enabled")).thenReturn("true");
    when(environment.getProperty("zeebe.broker.gateway.security.authentication.mode"))
        .thenReturn("identity");

    final boolean result = condition.matches(conditionContext, methodMetadata);
    assertThat(result).isTrue();
  }

  @Test
  void doesNotOverrideSecurityWhenEmbeddedGatewayEnabled() {
    when(environment.getProperty("zeebe.broker.gateway.enabled")).thenReturn("true");
    when(environment.getProperty("zeebe.gateway.security.authentication.mode"))
        .thenReturn("identity");

    final boolean result = condition.matches(conditionContext, methodMetadata);
    assertThat(result).isTrue();
  }
}
