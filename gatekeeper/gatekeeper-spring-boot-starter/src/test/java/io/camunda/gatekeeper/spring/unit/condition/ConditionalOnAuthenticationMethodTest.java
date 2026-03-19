/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gatekeeper.spring.unit.condition;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.gatekeeper.model.identity.AuthenticationMethod;
import io.camunda.gatekeeper.spring.condition.ConditionalOnAuthenticationMethod;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

final class ConditionalOnAuthenticationMethodTest {

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner();

  @Test
  void shouldActivateOidcBeanWhenMethodIsOidc() {
    contextRunner
        .withUserConfiguration(OidcConfig.class)
        .withPropertyValues("camunda.security.authentication.method=oidc")
        .run(
            context -> {
              assertThat(context).hasBean("oidcBean");
            });
  }

  @Test
  void shouldNotActivateOidcBeanWhenMethodIsBasic() {
    contextRunner
        .withUserConfiguration(OidcConfig.class)
        .withPropertyValues("camunda.security.authentication.method=basic")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean("oidcBean");
            });
  }

  @Test
  void shouldActivateBasicBeanByDefault() {
    contextRunner
        .withUserConfiguration(BasicConfig.class)
        .run(
            context -> {
              assertThat(context).hasBean("basicBean");
            });
  }

  @Test
  void shouldNotActivateOidcBeanByDefault() {
    contextRunner
        .withUserConfiguration(OidcConfig.class)
        .run(
            context -> {
              assertThat(context).doesNotHaveBean("oidcBean");
            });
  }

  @Test
  void shouldActivateBasicBeanWhenMethodIsBasic() {
    contextRunner
        .withUserConfiguration(BasicConfig.class)
        .withPropertyValues("camunda.security.authentication.method=basic")
        .run(
            context -> {
              assertThat(context).hasBean("basicBean");
            });
  }

  @Test
  void shouldNotActivateBasicBeanWhenMethodIsOidc() {
    contextRunner
        .withUserConfiguration(BasicConfig.class)
        .withPropertyValues("camunda.security.authentication.method=oidc")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean("basicBean");
            });
  }

  @Configuration(proxyBeanMethods = false)
  static class OidcConfig {
    @Bean
    @ConditionalOnAuthenticationMethod(AuthenticationMethod.OIDC)
    String oidcBean() {
      return "oidc";
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class BasicConfig {
    @Bean
    @ConditionalOnAuthenticationMethod(AuthenticationMethod.BASIC)
    String basicBean() {
      return "basic";
    }
  }
}
