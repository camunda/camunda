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

import io.camunda.auth.domain.port.outbound.TokenStorePort;
import io.camunda.auth.domain.store.CompositeTokenStore;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class CamundaAuthPersistenceAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  CamundaAuthAutoConfiguration.class,
                  CamundaAuthPersistenceAutoConfiguration.class))
          .withPropertyValues(
              "camunda.auth.method=oidc", "camunda.auth.token-exchange.enabled=true");

  @Test
  void shouldCreateCompositeTokenStoreWithSingleStore() {
    contextRunner
        .withUserConfiguration(SingleStoreConfiguration.class)
        .run(
            context -> {
              assertThat(context).hasBean("compositeTokenStore");
              assertThat(context)
                  .getBean("compositeTokenStore")
                  .isSameAs(context.getBean("tokenStore"));
            });
  }

  @Test
  void shouldCreateCompositeTokenStoreWithMultipleStores() {
    contextRunner
        .withUserConfiguration(MultipleStoreConfiguration.class)
        .run(
            context -> {
              assertThat(context).hasBean("compositeTokenStore");
              assertThat(context)
                  .getBean("compositeTokenStore")
                  .isInstanceOf(CompositeTokenStore.class);
            });
  }

  @Test
  void shouldNotLoadWhenTokenExchangeDisabled() {
    new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                CamundaAuthAutoConfiguration.class, CamundaAuthPersistenceAutoConfiguration.class))
        .withPropertyValues("camunda.auth.method=oidc", "camunda.auth.token-exchange.enabled=false")
        .withUserConfiguration(SingleStoreConfiguration.class)
        .run(context -> assertThat(context).doesNotHaveBean("compositeTokenStore"));
  }

  @Configuration(proxyBeanMethods = false)
  static class SingleStoreConfiguration {
    @Bean
    TokenStorePort tokenStore() {
      return mock(TokenStorePort.class);
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class MultipleStoreConfiguration {
    @Bean
    TokenStorePort tokenStore1() {
      return mock(TokenStorePort.class);
    }

    @Bean
    TokenStorePort tokenStore2() {
      return mock(TokenStorePort.class);
    }
  }
}
