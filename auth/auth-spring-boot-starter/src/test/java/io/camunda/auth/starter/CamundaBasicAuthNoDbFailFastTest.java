/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.starter;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.auth.domain.exception.BasicAuthenticationNotSupportedException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Integration-style test for the fail-fast behavior when Basic Authentication is configured without
 * secondary storage. Ported from monorepo tests:
 * - qa/acceptance-tests/.../BasicAuthNoSecondaryStorageTest
 * - dist/.../NoSecondaryStorageAuthenticationIT
 *
 * <p>Supplements the existing {@link CamundaBasicAuthNoDbAutoConfigurationTest} with additional
 * scenarios: default-method behavior, fail-fast message validation, and multiple property
 * combinations.
 */
class CamundaBasicAuthNoDbFailFastTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  CamundaAuthAutoConfiguration.class,
                  CamundaBasicAuthNoDbAutoConfiguration.class));

  @Test
  void shouldFailFastWithBasicAuthAndNoSecondaryStorageProperty() {
    // The default for secondary-storage-available is false (matchIfMissing=true),
    // so omitting it should trigger the fail-fast
    contextRunner
        .withPropertyValues("camunda.auth.method=basic")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .rootCause()
                  .isInstanceOf(BasicAuthenticationNotSupportedException.class);
            });
  }

  @Test
  void shouldFailFastWithBasicAuthAndExplicitlyDisabledStorage() {
    contextRunner
        .withPropertyValues(
            "camunda.auth.method=basic",
            "camunda.auth.basic.secondary-storage-available=false")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .rootCause()
                  .isInstanceOf(BasicAuthenticationNotSupportedException.class);
            });
  }

  @Test
  void failFastMessageShouldBeDescriptive() {
    contextRunner
        .withPropertyValues("camunda.auth.method=basic")
        .run(
            context -> {
              assertThat(context).hasFailed();
              // Navigate the cause chain to find the BasicAuthenticationNotSupportedException
              Throwable cause = context.getStartupFailure();
              while (cause != null && !(cause instanceof BasicAuthenticationNotSupportedException)) {
                cause = cause.getCause();
              }
              assertThat(cause)
                  .isNotNull()
                  .isInstanceOf(BasicAuthenticationNotSupportedException.class)
                  .hasMessageContaining("Basic Authentication is not supported")
                  .hasMessageContaining("secondary storage")
                  .hasMessageContaining("camunda.auth.method");
            });
  }

  @Test
  void shouldSucceedWithBasicAuthAndSecondaryStorageAvailable() {
    contextRunner
        .withPropertyValues(
            "camunda.auth.method=basic",
            "camunda.auth.basic.secondary-storage-available=true")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context)
                  .doesNotHaveBean(CamundaBasicAuthNoDbAutoConfiguration.class);
            });
  }

  @Test
  void shouldSucceedWithOidcAuthAndNoSecondaryStorage() {
    // OIDC does not require secondary storage — should not trigger fail-fast
    contextRunner
        .withPropertyValues("camunda.auth.method=oidc")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context)
                  .doesNotHaveBean(CamundaBasicAuthNoDbAutoConfiguration.class);
            });
  }

  @Test
  void shouldNotActivateWithoutAuthMethodProperty() {
    // When camunda.auth.method is not set at all, CamundaAuthAutoConfiguration
    // is not activated (ConditionalOnProperty), so no fail-fast
    new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                CamundaAuthAutoConfiguration.class,
                CamundaBasicAuthNoDbAutoConfiguration.class))
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context)
                  .doesNotHaveBean(CamundaBasicAuthNoDbAutoConfiguration.class);
            });
  }

  @Test
  void shouldFailFastWithBasicAuthCaseInsensitive() {
    // The AuthenticationMethod.parse() is case-insensitive
    contextRunner
        .withPropertyValues("camunda.auth.method=BASIC")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .rootCause()
                  .isInstanceOf(BasicAuthenticationNotSupportedException.class);
            });
  }

  @Test
  void shouldSucceedWithOidcAndExplicitlyDisabledStorage() {
    // Even when secondary storage is explicitly disabled, OIDC should still work
    contextRunner
        .withPropertyValues(
            "camunda.auth.method=oidc",
            "camunda.auth.basic.secondary-storage-available=false")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context)
                  .doesNotHaveBean(CamundaBasicAuthNoDbAutoConfiguration.class);
            });
  }
}
