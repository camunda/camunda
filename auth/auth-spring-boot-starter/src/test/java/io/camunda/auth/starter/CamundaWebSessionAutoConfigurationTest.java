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

import io.camunda.auth.domain.spi.SessionPersistencePort;
import io.camunda.auth.spring.session.WebSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.support.GenericConversionService;

class CamundaWebSessionAutoConfigurationTest {

  private final WebApplicationContextRunner contextRunner =
      new WebApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(CamundaWebSessionAutoConfiguration.class));

  @Test
  void shouldNotLoadWhenSessionsDisabled() {
    contextRunner.run(
        context -> {
          assertThat(context).doesNotHaveBean(WebSessionRepository.class);
        });
  }

  @Test
  void shouldNotLoadWithoutSessionPersistencePort() {
    contextRunner
        .withPropertyValues("camunda.persistent.sessions.enabled=true")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(WebSessionRepository.class);
            });
  }

  @Test
  void shouldCreateRepositoryWhenPortAvailable() {
    contextRunner
        .withPropertyValues("camunda.persistent.sessions.enabled=true")
        .withUserConfiguration(SessionBeansConfiguration.class)
        .run(
            context -> {
              assertThat(context).hasSingleBean(WebSessionRepository.class);
            });
  }

  @Test
  void shouldCreateDeletionTaskExecutor() {
    contextRunner
        .withPropertyValues("camunda.persistent.sessions.enabled=true")
        .withUserConfiguration(SessionBeansConfiguration.class)
        .run(
            context -> {
              assertThat(context).hasBean("persistentWebSessionDeletionTaskExecutor");
            });
  }

  @Configuration(proxyBeanMethods = false)
  static class SessionBeansConfiguration {
    @Bean
    SessionPersistencePort sessionPersistencePort() {
      return mock(SessionPersistencePort.class);
    }

    @Bean
    GenericConversionService conversionService() {
      return new GenericConversionService();
    }
  }
}
