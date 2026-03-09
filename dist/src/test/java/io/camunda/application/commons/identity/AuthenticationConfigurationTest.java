/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.search.clients.reader.UserReader;
import io.camunda.search.entities.UserEntity;
import io.camunda.security.reader.ResourceAccessChecks;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Verifies that {@link AuthenticationConfiguration} properly registers the {@link
 * CamundaUserDetailsService} and that it backs off when a custom {@link UserDetailsService} is
 * already present.
 */
class AuthenticationConfigurationTest {

  private static final String PROFILE_ACTIVE = "spring.profiles.active=consolidated-auth";
  private static final String REST_ENABLED = "camunda.rest.enabled=true";

  private final WebApplicationContextRunner contextRunner =
      new WebApplicationContextRunner()
          .withUserConfiguration(TestDependencies.class, AuthenticationConfiguration.class)
          .withPropertyValues("spring.main.web-application-type=servlet", REST_ENABLED);

  @Test
  void shouldCreateCamundaUserDetailsService() {
    contextRunner
        .withPropertyValues(PROFILE_ACTIVE)
        .run(
            context -> {
              assertThat(context).hasSingleBean(UserDetailsService.class);
              assertThat(context).hasSingleBean(CamundaUserDetailsService.class);
            });
  }

  @Test
  void shouldNotCreateWhenProfileIsMissing() {
    contextRunner.run(
        context -> {
          assertThat(context).doesNotHaveBean(CamundaUserDetailsService.class);
        });
  }

  @Test
  void shouldBackOffWhenCustomUserDetailsServiceExists() {
    new WebApplicationContextRunner()
        .withUserConfiguration(
            TestDependencies.class,
            CustomUserDetailsServiceConfig.class,
            AuthenticationConfiguration.class)
        .withPropertyValues(
            "spring.main.web-application-type=servlet", REST_ENABLED, PROFILE_ACTIVE)
        .run(
            context -> {
              assertThat(context).hasSingleBean(UserDetailsService.class);
              assertThat(context).doesNotHaveBean(CamundaUserDetailsService.class);
              assertThat(context).hasBean("customUserDetailsService");
            });
  }

  @Configuration(proxyBeanMethods = false)
  static class TestDependencies {
    @Bean
    PasswordEncoder passwordEncoder() {
      return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    UserReader userReader() {
      final var mock = mock(UserReader.class);
      when(mock.getById(anyString(), any(ResourceAccessChecks.class)))
          .thenReturn(
              new UserEntity(1L, "testuser", "Test User", "test@test.com", "{noop}testpassword"));
      return mock;
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class CustomUserDetailsServiceConfig {
    @Bean
    UserDetailsService customUserDetailsService() {
      return mock(UserDetailsService.class);
    }
  }
}
