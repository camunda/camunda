/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.application.commons.CommonsModuleConfiguration;
import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.configuration.beanoverrides.GatewayBasedPropertiesOverride;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.boot.SpringApplication;

public class CamundaSecurityConfigurationTest {

  @Test
  public void whenMultiTenancyEnabledAndApiUnprotectedThenFailsToStart() {
    final var mtProperty = "camunda.security.multiTenancy.checksEnabled";
    final var apiProperty = "camunda.security.authentication.unprotected-api";
    System.setProperty(mtProperty, "true");
    System.setProperty(apiProperty, "true");

    assertThatThrownBy(
            () -> {
              final SpringApplication app =
                  new SpringApplication(
                      CommonsModuleConfiguration.class,
                      UnifiedConfiguration.class,
                      UnifiedConfigurationHelper.class,
                      GatewayBasedPropertiesOverride.class);
              app.run();
            })
        .isInstanceOf(BeanCreationException.class)
        .cause()
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(
            "Multi-tenancy is enabled (%s=true), but the API is unprotected (%s=true). Please enable API protection if you want to make use of multi-tenancy."
                .formatted(mtProperty, apiProperty));
  }
}
