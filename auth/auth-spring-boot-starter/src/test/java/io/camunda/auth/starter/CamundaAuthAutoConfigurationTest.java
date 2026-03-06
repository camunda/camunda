/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.starter;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.auth.domain.spi.CamundaAuthenticationConverter;
import io.camunda.auth.domain.spi.CamundaAuthenticationHolder;
import io.camunda.auth.domain.spi.CamundaAuthenticationProvider;
import io.camunda.auth.spring.converter.UnprotectedCamundaAuthenticationConverter;
import io.camunda.auth.spring.holder.HttpSessionBasedAuthenticationHolder;
import io.camunda.auth.spring.holder.RequestContextBasedAuthenticationHolder;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class CamundaAuthAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(CamundaAuthAutoConfiguration.class));

  @Test
  void shouldCreateDefaultBeans() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(CamundaAuthenticationProvider.class);
          assertThat(context).hasSingleBean(CamundaAuthenticationHolder.class);
          assertThat(context)
              .getBean(CamundaAuthenticationHolder.class)
              .isInstanceOf(RequestContextBasedAuthenticationHolder.class);
        });
  }

  @Test
  void shouldCreateHttpSessionHolderWhenSessionEnabled() {
    contextRunner
        .withPropertyValues("camunda.auth.session.enabled=true")
        .run(
            context -> {
              assertThat(context).getBeans(CamundaAuthenticationHolder.class).hasSize(2);
              assertThat(context.getBeansOfType(CamundaAuthenticationHolder.class).values())
                  .anyMatch(h -> h instanceof HttpSessionBasedAuthenticationHolder);
            });
  }

  @Test
  void shouldNotCreateHttpSessionHolderByDefault() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(CamundaAuthenticationHolder.class);
          assertThat(context)
              .getBean(CamundaAuthenticationHolder.class)
              .isInstanceOf(RequestContextBasedAuthenticationHolder.class);
        });
  }

  @Test
  void shouldCreateUnprotectedConverterWhenPropertySet() {
    contextRunner
        .withPropertyValues("camunda.auth.unprotected-api=true")
        .run(
            context -> {
              final var converters = context.getBeansOfType(CamundaAuthenticationConverter.class);
              assertThat(converters.values())
                  .anyMatch(c -> c instanceof UnprotectedCamundaAuthenticationConverter);
            });
  }

  @Test
  void shouldNotCreateUnprotectedConverterByDefault() {
    contextRunner.run(
        context -> {
          final var converters = context.getBeansOfType(CamundaAuthenticationConverter.class);
          assertThat(converters.values())
              .noneMatch(c -> c instanceof UnprotectedCamundaAuthenticationConverter);
        });
  }

  @Test
  void shouldBackOffWhenUserProvidesHolder() {
    contextRunner
        .withUserConfiguration(CustomHolderConfiguration.class)
        .run(
            context -> {
              assertThat(context).hasSingleBean(CamundaAuthenticationHolder.class);
              assertThat(context)
                  .getBean(CamundaAuthenticationHolder.class)
                  .isInstanceOf(RequestContextBasedAuthenticationHolder.class);
              assertThat(context).getBean("requestContextBasedAuthenticationHolder").isNotNull();
            });
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
  void shouldCollectAllHoldersInProvider() {
    contextRunner
        .withPropertyValues("camunda.auth.session.enabled=true")
        .run(
            context -> {
              assertThat(context).hasSingleBean(CamundaAuthenticationProvider.class);
              assertThat(context).getBeans(CamundaAuthenticationHolder.class).hasSize(2);
            });
  }

  @Configuration(proxyBeanMethods = false)
  static class CustomHolderConfiguration {
    @Bean
    public CamundaAuthenticationHolder requestContextBasedAuthenticationHolder() {
      return new RequestContextBasedAuthenticationHolder();
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class CustomProviderConfiguration {
    @Bean
    public CamundaAuthenticationProvider camundaAuthenticationProvider() {
      return new TestCamundaAuthenticationProvider();
    }
  }

  static class TestCamundaAuthenticationProvider implements CamundaAuthenticationProvider {
    @Override
    public io.camunda.auth.domain.model.CamundaAuthentication getCamundaAuthentication() {
      return null;
    }
  }
}
