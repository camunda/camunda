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

class CamundaBasicAuthNoDbAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  CamundaAuthAutoConfiguration.class, CamundaBasicAuthNoDbAutoConfiguration.class));

  @Test
  void shouldFailFastWithoutSecondaryStorage() {
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
  void shouldNotActivateWhenStorageAvailable() {
    contextRunner
        .withPropertyValues(
            "camunda.auth.method=basic", "camunda.auth.basic.secondary-storage-available=true")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).doesNotHaveBean(CamundaBasicAuthNoDbAutoConfiguration.class);
            });
  }

  @Test
  void shouldNotActivateForOidcMethod() {
    contextRunner
        .withPropertyValues("camunda.auth.method=oidc")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).doesNotHaveBean(CamundaBasicAuthNoDbAutoConfiguration.class);
            });
  }
}
