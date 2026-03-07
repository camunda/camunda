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
import static org.mockito.Mockito.when;

import io.camunda.auth.spring.converter.TokenClaimsConverter;
import io.camunda.auth.spring.oidc.JWSKeySelectorFactory;
import io.camunda.auth.spring.oidc.OidcAuthenticationConfigurationRepository;
import io.camunda.auth.spring.oidc.TokenValidatorFactory;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

class CamundaOidcAutoConfigurationTest {

  private final WebApplicationContextRunner contextRunner =
      new WebApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(CamundaOidcAutoConfiguration.class))
          .withPropertyValues("camunda.auth.method=oidc")
          .withUserConfiguration(OidcDependenciesConfiguration.class);

  @Test
  void shouldNotLoadForBasicAuth() {
    new WebApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CamundaOidcAutoConfiguration.class))
        .withPropertyValues("camunda.auth.method=basic")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(TokenValidatorFactory.class);
              assertThat(context).doesNotHaveBean(JWSKeySelectorFactory.class);
              assertThat(context).doesNotHaveBean(OidcAuthenticationConfigurationRepository.class);
            });
  }

  @Test
  void shouldCreateTokenValidatorFactory() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(TokenValidatorFactory.class);
        });
  }

  @Test
  void shouldCreateJwsKeySelectorFactory() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(JWSKeySelectorFactory.class);
        });
  }

  @Test
  void shouldBackOffWhenCustomTokenValidatorFactoryExists() {
    contextRunner
        .withUserConfiguration(CustomTokenValidatorFactoryConfiguration.class)
        .run(
            context -> {
              assertThat(context).hasSingleBean(TokenValidatorFactory.class);
              assertThat(context).hasBean("customTokenValidatorFactory");
              assertThat(context).doesNotHaveBean("tokenValidatorFactory");
            });
  }

  @Test
  void shouldBackOffWhenCustomJwsKeySelectorFactoryExists() {
    contextRunner
        .withUserConfiguration(CustomJwsKeySelectorFactoryConfiguration.class)
        .run(
            context -> {
              assertThat(context).hasSingleBean(JWSKeySelectorFactory.class);
              assertThat(context).hasBean("customJwsKeySelectorFactory");
            });
  }

  /**
   * Provides mock dependencies that {@link CamundaOidcAutoConfiguration} beans require but that
   * would otherwise trigger network calls or require a full Spring Security context.
   */
  @Configuration(proxyBeanMethods = false)
  static class OidcDependenciesConfiguration {
    @Bean
    OidcAuthenticationConfigurationRepository oidcAuthenticationConfigurationRepository() {
      final var repo = mock(OidcAuthenticationConfigurationRepository.class);
      when(repo.getOidcAuthenticationConfigurations()).thenReturn(Map.of());
      return repo;
    }

    @Bean
    ClientRegistrationRepository clientRegistrationRepository() {
      return mock(ClientRegistrationRepository.class);
    }

    @Bean
    TokenClaimsConverter tokenClaimsConverter() {
      return mock(TokenClaimsConverter.class);
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class CustomTokenValidatorFactoryConfiguration {
    @Bean
    TokenValidatorFactory customTokenValidatorFactory() {
      return mock(TokenValidatorFactory.class);
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class CustomJwsKeySelectorFactoryConfiguration {
    @Bean
    JWSKeySelectorFactory customJwsKeySelectorFactory() {
      return mock(JWSKeySelectorFactory.class);
    }
  }
}
