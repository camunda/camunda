/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

public class CamundaSecurityConfigurationTest {

  @BeforeEach
  void setUp() {
    // Reset system properties before each test to avoid side effects
    final var mtProperty = "camunda.security.multiTenancy.checksEnabled";
    final var apiProperty = "camunda.security.authentication.unprotected-api";
    System.setProperty(mtProperty, "false");
    System.setProperty(apiProperty, "true");
  }

  @AfterEach
  void tearDown() {
    System.clearProperty("camunda.security.multiTenancy.checksEnabled");
    System.clearProperty("camunda.security.authentication.unprotected-api");
    System.clearProperty("camunda.security.id-validation-pattern");
  }

  @Test
  public void whenMultiTenancyEnabledAndApiUnprotectedThenFailsToStart() {
    final var mtProperty = "camunda.security.multiTenancy.checksEnabled";
    final var apiProperty = "camunda.security.authentication.unprotected-api";
    System.setProperty(mtProperty, "true");
    System.setProperty(apiProperty, "true");

    assertThatThrownBy(
            () -> {
              final SpringApplication app = new SpringApplication(TestConfig.class);
              app.setWebApplicationType(WebApplicationType.NONE);
              app.run();
            })
        .isInstanceOf(BeanCreationException.class)
        .cause()
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(
            "Multi-tenancy is enabled (%s=true), but the API is unprotected (%s=true). Please enable API protection if you want to make use of multi-tenancy."
                .formatted(mtProperty, apiProperty));
  }

  @Test
  public void shouldFailToStartWhenIdPatternIsInvalid() {
    final var idPatternProperty = "camunda.security.id-validation-pattern";
    final var idPatternValue = "[|";
    System.setProperty(idPatternProperty, idPatternValue);

    assertThatThrownBy(
            () -> {
              final SpringApplication app = new SpringApplication(TestConfig.class);
              app.setWebApplicationType(WebApplicationType.NONE);
              app.run();
            })
        .isInstanceOf(BeanCreationException.class)
        .cause()
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Invalid regex for %s: %s".formatted(idPatternProperty, idPatternValue));
  }

  @Test
  public void shouldFailToStartWhenIdPatternAllowsWildcardCharacter() {
    final var idPatternProperty = "camunda.security.id-validation-pattern";
    final var idPatternValue = "^[a-zA-Z0-9_@.+*-]+$";
    System.setProperty(idPatternProperty, idPatternValue);

    assertThatThrownBy(
            () -> {
              final SpringApplication app = new SpringApplication(TestConfig.class);
              app.setWebApplicationType(WebApplicationType.NONE);
              app.run();
            })
        .isInstanceOf(BeanCreationException.class)
        .cause()
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(
            "The configured identifier pattern (%s=%s) allows the asterisk ('*') which is a reserved character. Please use a different pattern."
                .formatted(idPatternProperty, idPatternValue));
  }

  @Configuration
  @Import(CamundaSecurityConfiguration.class)
  static class TestConfig {}
}
