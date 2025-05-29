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
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;

public class SecurityConfigurationTest {

  @Mock private Environment environment;

  @BeforeEach
  void setUp() throws Exception {
    MockitoAnnotations.openMocks(this).close();
    final ConditionContext conditionContext = mock(ConditionContext.class);
    when(conditionContext.getEnvironment()).thenReturn(environment);
  }

  @ParameterizedTest
  @MethodSource("provideTestCases")
  void testGatewaySecurityAuthenticationEnabledCondition(final TestCase testCase) {
    when(environment.getProperty("zeebe.gateway.enable"))
        .thenReturn(testCase.standaloneGatewayEnable);
    when(environment.getProperty("camunda.security.authentication.unprotected-api"))
        .thenReturn(testCase.apiUnprotected);
    when(environment.getProperty("zeebe.broker.gateway.enable"))
        .thenReturn(testCase.embeddedGatewayEnable);

    final boolean result =
        (Boolean.parseBoolean(testCase.standaloneGatewayEnable)
                || Boolean.parseBoolean(testCase.embeddedGatewayEnable))
            && !Boolean.parseBoolean(testCase.apiUnprotected);
    assertThat(result).describedAs(testCase.description).isEqualTo(testCase.expectedResult);
  }

  private static Stream<Arguments> provideTestCases() {
    return Stream.of(
        Arguments.of(
            new TestCase.Builder()
                .description("Standalone gateway enabled and API is protected")
                .standaloneGatewayEnable()
                .apiProtected()
                .expectedResult(true)
                .build()),
        Arguments.of(
            new TestCase.Builder()
                .description("Standalone gateway enabled and API is unprotected")
                .standaloneGatewayEnable()
                .apiUnprotected()
                .expectedResult(false)
                .build()),
        Arguments.of(
            new TestCase.Builder()
                .description("Embedded gateway enabled and API is protected")
                .embeddedGatewayEnable()
                .apiProtected()
                .expectedResult(true)
                .build()),
        Arguments.of(
            new TestCase.Builder()
                .description("Embedded gateway enabled and API is unprotected")
                .embeddedGatewayEnable()
                .apiUnprotected()
                .expectedResult(false)
                .build()),
        Arguments.of(
            new Builder()
                .description("Both standalone and embedded gateways enabled with API unprotected")
                .standaloneGatewayEnable()
                .embeddedGatewayEnable()
                .apiUnprotected()
                .expectedResult(false)
                .build()),
        Arguments.of(
            new TestCase.Builder()
                .description("Both standalone and embedded gateways enabled with API protected")
                .standaloneGatewayEnable()
                .embeddedGatewayEnable()
                .apiProtected()
                .expectedResult(true)
                .build()));
  }

  record TestCase(
      String description,
      String standaloneGatewayEnable,
      String apiUnprotected,
      String embeddedGatewayEnable,
      boolean expectedResult) {

    static class Builder {
      private String description;
      private String standaloneGatewayEnable;
      private String apiUnprotected;
      private String embeddedGatewayEnable;
      private boolean expectedResult;

      Builder description(final String description) {
        this.description = description;
        return this;
      }

      Builder standaloneGatewayEnable() {
        standaloneGatewayEnable = "true";
        return this;
      }

      Builder apiProtected() {
        apiUnprotected = "false";
        return this;
      }

      Builder apiUnprotected() {
        apiUnprotected = "true";
        return this;
      }

      Builder embeddedGatewayEnable() {
        embeddedGatewayEnable = "true";
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
            apiUnprotected,
            embeddedGatewayEnable,
            expectedResult);
      }
    }
  }
}
