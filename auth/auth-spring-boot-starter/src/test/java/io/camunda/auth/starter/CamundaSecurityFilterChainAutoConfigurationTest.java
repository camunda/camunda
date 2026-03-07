/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.starter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

class CamundaSecurityFilterChainAutoConfigurationTest {

  private final WebApplicationContextRunner contextRunner =
      new WebApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(CamundaSecurityFilterChainAutoConfiguration.class));

  @Test
  void shouldNotLoadForBasicAuth() {
    contextRunner
        .withPropertyValues("camunda.auth.method=basic")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean("unprotectedPathsSecurityFilterChain");
              assertThat(context).doesNotHaveBean("unprotectedApiSecurityFilterChain");
              assertThat(context).doesNotHaveBean("oidcApiSecurityFilterChain");
              assertThat(context).doesNotHaveBean("protectedUnhandledPathsSecurityFilterChain");
            });
  }

  @Test
  void shouldNotLoadWhenMethodNotSet() {
    contextRunner.run(
        context -> {
          assertThat(context).doesNotHaveBean("unprotectedPathsSecurityFilterChain");
          assertThat(context).doesNotHaveBean("unprotectedApiSecurityFilterChain");
          assertThat(context).doesNotHaveBean("oidcApiSecurityFilterChain");
          assertThat(context).doesNotHaveBean("protectedUnhandledPathsSecurityFilterChain");
        });
  }
}
