/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.starter;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.auth.domain.model.CamundaAuthentication;
import io.camunda.auth.domain.spi.BasicAuthMembershipResolver;
import io.camunda.auth.spring.converter.UsernamePasswordAuthenticationTokenConverter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class CamundaBasicAuthAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  CamundaAuthAutoConfiguration.class, CamundaBasicAuthAutoConfiguration.class))
          .withPropertyValues(
              "camunda.auth.method=basic", "camunda.auth.basic.secondary-storage-available=true");

  @Test
  void shouldCreateConverterWithBasicAuthAndStorage() {
    contextRunner
        .withUserConfiguration(BasicAuthBeansConfiguration.class)
        .run(
            context -> {
              assertThat(context).hasSingleBean(UsernamePasswordAuthenticationTokenConverter.class);
            });
  }

  @Test
  void shouldNotLoadForOidcMethod() {
    new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                CamundaAuthAutoConfiguration.class, CamundaBasicAuthAutoConfiguration.class))
        .withPropertyValues("camunda.auth.method=oidc")
        .run(
            context ->
                assertThat(context)
                    .doesNotHaveBean(UsernamePasswordAuthenticationTokenConverter.class));
  }

  @Test
  void shouldBackOffWhenCustomConverterExists() {
    contextRunner
        .withUserConfiguration(
            BasicAuthBeansConfiguration.class, CustomConverterConfiguration.class)
        .run(
            context -> {
              assertThat(context).hasSingleBean(UsernamePasswordAuthenticationTokenConverter.class);
              assertThat(context)
                  .getBean(UsernamePasswordAuthenticationTokenConverter.class)
                  .isSameAs(context.getBean("customConverter"));
            });
  }

  @Configuration(proxyBeanMethods = false)
  static class BasicAuthBeansConfiguration {
    @Bean
    BasicAuthMembershipResolver basicAuthMembershipResolver() {
      return username -> CamundaAuthentication.anonymous();
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class CustomConverterConfiguration {
    @Bean
    UsernamePasswordAuthenticationTokenConverter customConverter() {
      return new UsernamePasswordAuthenticationTokenConverter(
          username -> CamundaAuthentication.anonymous());
    }
  }
}
