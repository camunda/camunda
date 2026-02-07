/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.security;

import static io.camunda.spring.utils.DatabaseTypeUtils.CAMUNDA_DATABASE_TYPE_NONE;
import static io.camunda.spring.utils.DatabaseTypeUtils.UNIFIED_CONFIG_PROPERTY_CAMUNDA_DATABASE_TYPE;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.application.commons.CommonsModuleConfiguration;
import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.configuration.beanoverrides.GatewayBasedPropertiesOverride;
import io.camunda.configuration.beans.SearchEngineConnectProperties;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.boot.SpringApplication;

public class CamundaSecurityConfigurationTest {

  final String mtProperty = "camunda.security.multiTenancy.checksEnabled";
  final String apiProperty = "camunda.security.authentication.unprotected-api";
  final String idPatternProperty = "camunda.security.id-validation-pattern";

  @Test
  public void whenMultiTenancyEnabledAndApiUnprotectedThenFailsToStart() {
    assertThatThrownBy(
            () -> {
              final SpringApplication app =
                  new SpringApplication(
                      CommonsModuleConfiguration.class,
                      UnifiedConfiguration.class,
                      UnifiedConfigurationHelper.class,
                      GatewayBasedPropertiesOverride.class,
                      SearchEngineConnectProperties.class);
              app.setDefaultProperties(
                  Map.of(
                      UNIFIED_CONFIG_PROPERTY_CAMUNDA_DATABASE_TYPE, CAMUNDA_DATABASE_TYPE_NONE,
                      mtProperty, "true",
                      apiProperty, "true"));
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
    final var idPatternValue = "[|";

    assertThatThrownBy(
            () -> {
              final SpringApplication app =
                  new SpringApplication(
                      CommonsModuleConfiguration.class,
                      UnifiedConfigurationHelper.class,
                      UnifiedConfiguration.class,
                      GatewayBasedPropertiesOverride.class,
                      SearchEngineConnectProperties.class);
              app.setDefaultProperties(
                  Map.of(
                      UNIFIED_CONFIG_PROPERTY_CAMUNDA_DATABASE_TYPE,
                      CAMUNDA_DATABASE_TYPE_NONE,
                      mtProperty,
                      "false",
                      apiProperty,
                      "true",
                      idPatternProperty,
                      idPatternValue));
              app.run();
            })
        .isInstanceOf(BeanCreationException.class)
        .cause()
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(
            "The configured identifier pattern (%s=%s) is invalid. Please use a different pattern."
                .formatted(idPatternProperty, idPatternValue));
  }

  @Test
  public void shouldFailToStartWhenIdPatternAllowsWildcardCharacter() {
    final var idPatternValue = "^[a-zA-Z0-9_@.+*-]+$";

    assertThatThrownBy(
            () -> {
              final SpringApplication app =
                  new SpringApplication(
                      CommonsModuleConfiguration.class,
                      UnifiedConfigurationHelper.class,
                      UnifiedConfiguration.class,
                      GatewayBasedPropertiesOverride.class,
                      SearchEngineConnectProperties.class);
              app.setDefaultProperties(
                  Map.of(
                      UNIFIED_CONFIG_PROPERTY_CAMUNDA_DATABASE_TYPE,
                      CAMUNDA_DATABASE_TYPE_NONE,
                      mtProperty,
                      "false",
                      apiProperty,
                      "true",
                      idPatternProperty,
                      idPatternValue));
              app.run();
            })
        .isInstanceOf(BeanCreationException.class)
        .cause()
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(
            "The configured identifier pattern (%s=%s) allows the asterisk ('*') which is a reserved character. Please use a different pattern."
                .formatted(idPatternProperty, idPatternValue));
  }
}
