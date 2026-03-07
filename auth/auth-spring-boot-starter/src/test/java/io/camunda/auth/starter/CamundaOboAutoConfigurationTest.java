/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.starter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.camunda.auth.spring.OnBehalfOfTokenRelayFilter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;

class CamundaOboAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  CamundaAuthAutoConfiguration.class, CamundaOboAutoConfiguration.class))
          .withPropertyValues("camunda.auth.method=oidc", "camunda.auth.obo.enabled=true");

  @Test
  void shouldCreateFilterWhenEnabledWithClientManager() {
    contextRunner
        .withUserConfiguration(ClientManagerConfiguration.class)
        .run(context -> assertThat(context).hasSingleBean(OnBehalfOfTokenRelayFilter.class));
  }

  @Test
  void shouldNotLoadWhenDisabled() {
    new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                CamundaAuthAutoConfiguration.class, CamundaOboAutoConfiguration.class))
        .withPropertyValues("camunda.auth.method=oidc", "camunda.auth.obo.enabled=false")
        .withUserConfiguration(ClientManagerConfiguration.class)
        .run(context -> assertThat(context).doesNotHaveBean(OnBehalfOfTokenRelayFilter.class));
  }

  @Test
  void shouldNotLoadWithoutClientManager() {
    contextRunner.run(
        context -> assertThat(context).doesNotHaveBean(OnBehalfOfTokenRelayFilter.class));
  }

  @Configuration(proxyBeanMethods = false)
  static class ClientManagerConfiguration {
    @Bean
    OAuth2AuthorizedClientManager authorizedClientManager() {
      return mock(OAuth2AuthorizedClientManager.class);
    }
  }
}
