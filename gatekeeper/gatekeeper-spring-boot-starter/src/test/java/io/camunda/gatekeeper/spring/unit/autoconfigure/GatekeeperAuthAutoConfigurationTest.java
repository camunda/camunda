/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gatekeeper.spring.unit.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.gatekeeper.config.AuthenticationConfig;
import io.camunda.gatekeeper.model.identity.CamundaAuthentication;
import io.camunda.gatekeeper.spi.CamundaAuthenticationHolder;
import io.camunda.gatekeeper.spi.CamundaAuthenticationProvider;
import io.camunda.gatekeeper.spring.autoconfigure.GatekeeperAuthAutoConfiguration;
import io.camunda.gatekeeper.spring.handler.AuthFailureHandler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class GatekeeperAuthAutoConfigurationTest {

  private final WebApplicationContextRunner contextRunner =
      new WebApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(GatekeeperAuthAutoConfiguration.class))
          .withUserConfiguration(ObjectMapperConfiguration.class);

  @Test
  void shouldCreateCoreBeans() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(CamundaAuthenticationProvider.class);
          assertThat(context).hasSingleBean(AuthFailureHandler.class);
          assertThat(context).hasSingleBean(AuthenticationConfig.class);
        });
  }

  @Test
  void shouldCreateBothHolders() {
    contextRunner.run(
        context ->
            assertThat(context.getBeansOfType(CamundaAuthenticationHolder.class)).hasSize(2));
  }

  @Test
  void shouldBackOffWhenUserProvidesProvider() {
    contextRunner
        .withUserConfiguration(CustomProviderConfiguration.class)
        .run(
            context -> {
              assertThat(context).hasSingleBean(CamundaAuthenticationProvider.class);
              assertThat(context)
                  .getBean(CamundaAuthenticationProvider.class)
                  .isInstanceOf(TestCamundaAuthenticationProvider.class);
            });
  }

  @Test
  void shouldBackOffWhenUserProvidesFailureHandler() {
    contextRunner
        .withUserConfiguration(CustomFailureHandlerConfiguration.class)
        .run(
            context -> {
              assertThat(context).hasSingleBean(AuthFailureHandler.class);
              // The custom one should be the only one
              assertThat(context).getBean(AuthFailureHandler.class).isNotNull();
            });
  }

  @Configuration(proxyBeanMethods = false)
  static class CustomProviderConfiguration {
    @Bean
    public CamundaAuthenticationProvider camundaAuthenticationProvider() {
      return new TestCamundaAuthenticationProvider();
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class CustomFailureHandlerConfiguration {
    @Bean
    public AuthFailureHandler authFailureHandler() {
      return new AuthFailureHandler(new com.fasterxml.jackson.databind.ObjectMapper());
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class ObjectMapperConfiguration {
    @Bean
    public ObjectMapper objectMapper() {
      return new ObjectMapper();
    }
  }

  static class TestCamundaAuthenticationProvider implements CamundaAuthenticationProvider {
    @Override
    public CamundaAuthentication getCamundaAuthentication() {
      return null;
    }
  }
}
