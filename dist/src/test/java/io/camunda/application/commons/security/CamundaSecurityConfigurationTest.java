/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.security;

import static io.camunda.application.commons.security.CamundaSecurityConfiguration.TENANTS_API_DISABLED_WARNING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import java.util.List;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.test.appender.ListAppender;
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

  @Test
  public void shouldWarnWhenMultiTenancyChecksEnabledButTenantsApiDisabled() {
    // given
    final var properties = new CamundaSecurityLibraryProperties();
    properties.getMultiTenancy().setChecksEnabled(true);
    properties.getMultiTenancy().setApiEnabled(false);

    // when
    final var logEvents = validateAndCaptureLogs(properties);

    // then
    assertThat(logEvents)
        .singleElement()
        .satisfies(event -> assertThat(event.getLevel()).isEqualTo(Level.WARN))
        .satisfies(
            event ->
                assertThat(event.getMessage().getFormattedMessage())
                    .isEqualTo(TENANTS_API_DISABLED_WARNING));
  }

  @Test
  public void shouldNotWarnWhenMultiTenancyChecksAndTenantsApiAreBothEnabled() {
    // given
    final var properties = new CamundaSecurityLibraryProperties();
    properties.getMultiTenancy().setChecksEnabled(true);
    properties.getMultiTenancy().setApiEnabled(true);

    // when
    final var logEvents = validateAndCaptureLogs(properties);

    // then
    assertThat(logEvents)
        .extracting(event -> event.getMessage().getFormattedMessage())
        .doesNotContain(TENANTS_API_DISABLED_WARNING);
  }

  @Test
  public void shouldNotWarnWhenMultiTenancyChecksAreDisabled() {
    // given
    final var properties = new CamundaSecurityLibraryProperties();
    properties.getMultiTenancy().setChecksEnabled(false);
    properties.getMultiTenancy().setApiEnabled(false);

    // when
    final var logEvents = validateAndCaptureLogs(properties);

    // then
    assertThat(logEvents)
        .extracting(event -> event.getMessage().getFormattedMessage())
        .doesNotContain(TENANTS_API_DISABLED_WARNING);
  }

  private static List<LogEvent> validateAndCaptureLogs(
      final CamundaSecurityLibraryProperties properties) {
    final var loggerName = CamundaSecurityConfiguration.class.getName();
    final var appender = new ListAppender("security-configuration-appender");
    appender.start();
    final var context = (LoggerContext) LogManager.getContext(false);
    final var loggerConfig = new LoggerConfig(loggerName, Level.ALL, true);
    loggerConfig.addAppender(appender, null, null);
    context.getConfiguration().addLogger(loggerName, loggerConfig);
    context.updateLoggers();

    try {
      new CamundaSecurityConfiguration(properties).validate();
      return appender.getEvents();
    } finally {
      context.getConfiguration().removeLogger(loggerName);
      context.updateLoggers();
      appender.stop();
    }
  }

  @Configuration
  @Import(CamundaSecurityConfiguration.class)
  static class TestConfig {}
}
