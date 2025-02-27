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

import io.camunda.zeebe.shared.security.SecurityConfigurationTest.TestCase.Builder;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.MethodMetadata;

public class SecurityConfigurationTest {

  @Mock private Environment environment;

  private ConditionContext conditionContext;
  private MethodMetadata methodMetadata;
  private final Condition condition =
      new SecurityConfiguration.GatewaySecurityAuthenticationEnabledCondition();

  @BeforeEach
  void setUp() throws Exception {
    MockitoAnnotations.openMocks(this).close();
    conditionContext = mock(ConditionContext.class);
    methodMetadata = mock(MethodMetadata.class);
    when(conditionContext.getEnvironment()).thenReturn(environment);
  }

  @ParameterizedTest
  @MethodSource("provideTestCases")
  void testGatewaySecurityAuthenticationEnabledCondition(final TestCase testCase) {
    when(environment.getProperty("zeebe.gateway.enable"))
        .thenReturn(testCase.standaloneGatewayEnable);
    when(environment.getProperty("zeebe.gateway.security.authentication.mode"))
        .thenReturn(testCase.standaloneGatewaySecurityMode);
    when(environment.getProperty("zeebe.broker.gateway.enable"))
        .thenReturn(testCase.embeddedGatewayEnable);
    when(environment.getProperty("zeebe.broker.gateway.security.authentication.mode"))
        .thenReturn(testCase.embeddedGatewaySecurityMode);

    final boolean result = condition.matches(conditionContext, methodMetadata);
    assertThat(result).describedAs(testCase.description).isEqualTo(testCase.expectedResult);
  }

  private static Stream<Arguments> provideTestCases() {
    return Stream.of(
        Arguments.of(
            new TestCase.Builder()
                .description("Standalone gateway enabled and security mode is none")
                .standaloneGatewayEnable()
                .standaloneGatewaySecurityMode("none")
                .expectedResult(false)
                .build()),
        Arguments.of(
            new TestCase.Builder()
                .description("Standalone gateway enabled and security mode is identity")
                .standaloneGatewayEnable()
                .standaloneGatewaySecurityMode("identity")
                .expectedResult(true)
                .build()),
        Arguments.of(
            new TestCase.Builder()
                .description("Embedded gateway enabled and security mode is none")
                .embeddedGatewayEnable()
                .embeddedGatewaySecurityMode("none")
                .expectedResult(false)
                .build()),
        Arguments.of(
            new TestCase.Builder()
                .description("Embedded gateway enabled and security mode is identity")
                .embeddedGatewayEnable()
                .embeddedGatewaySecurityMode("identity")
                .expectedResult(true)
                .build()),
        Arguments.of(
            new TestCase.Builder()
                .description(
                    "Both standalone and embedded gateways enabled with security mode none")
                .standaloneGatewayEnable()
                .standaloneGatewaySecurityMode("none")
                .embeddedGatewayEnable()
                .embeddedGatewaySecurityMode("none")
                .expectedResult(false)
                .build()),
        Arguments.of(
            new Builder()
                .description(
                    "Standalone gateway enabled, standalone gateway security mode none, embedded gateway security mode identity")
                .standaloneGatewayEnable()
                .standaloneGatewaySecurityMode("none")
                .embeddedGatewaySecurityMode("identity")
                .expectedResult(false)
                .build()),
        Arguments.of(
            new TestCase.Builder()
                .description(
                    "Standalone gateway enabled, standalone gateway security mode identity, embedded gateway security mode none")
                .standaloneGatewayEnable()
                .standaloneGatewaySecurityMode("identity")
                .embeddedGatewaySecurityMode("none")
                .expectedResult(true)
                .build()),
        Arguments.of(
            new TestCase.Builder()
                .description(
                    "Embedded gateway enabled, standalone gateway security mode none, embedded gateway security mode identity")
                .standaloneGatewaySecurityMode("none")
                .embeddedGatewayEnable()
                .embeddedGatewaySecurityMode("identity")
                .expectedResult(true)
                .build()),
        Arguments.of(
            new Builder()
                .description(
                    "Embedded gateway enabled, standalone gateway security mode identity, embedded gateway security mode none")
                .standaloneGatewaySecurityMode("identity")
                .embeddedGatewayEnable()
                .embeddedGatewaySecurityMode("none")
                .expectedResult(false)
                .build()));
  }

  record TestCase(
      String description,
      String standaloneGatewayEnable,
      String standaloneGatewaySecurityMode,
      String embeddedGatewayEnable,
      String embeddedGatewaySecurityMode,
      boolean expectedResult) {

    static class Builder {
      private String description;
      private String standaloneGatewayEnable;
      private String standaloneGatewaySecurityMode;
      private String embeddedGatewayEnable;
      private String embeddedGatewaySecurityMode;
      private boolean expectedResult;

      Builder description(final String description) {
        this.description = description;
        return this;
      }

      Builder standaloneGatewayEnable() {
        standaloneGatewayEnable = "true";
        return this;
      }

      Builder standaloneGatewaySecurityMode(final String standaloneGatewaySecurityMode) {
        this.standaloneGatewaySecurityMode = standaloneGatewaySecurityMode;
        return this;
      }

      Builder embeddedGatewayEnable() {
        embeddedGatewayEnable = "true";
        return this;
      }

      Builder embeddedGatewaySecurityMode(final String embeddedGatewaySecurityMode) {
        this.embeddedGatewaySecurityMode = embeddedGatewaySecurityMode;
        return this;
      }

      Builder expectedResult(final boolean expectedResult) {
        this.expectedResult = expectedResult;
        return this;
      }

      TestCase build() {
        return new TestCase(
            description,
            standaloneGatewayEnable,
            standaloneGatewaySecurityMode,
            embeddedGatewayEnable,
            embeddedGatewaySecurityMode,
            expectedResult);
      }
    }
  }
}
