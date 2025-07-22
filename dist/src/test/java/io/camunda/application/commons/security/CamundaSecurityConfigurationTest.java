/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.application.MainSupport;
import io.camunda.application.commons.CommonsModuleConfiguration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;

public class CamundaSecurityConfigurationTest {

  @Test
  public void whenMultiTenancyEnabledAndApiUnprotectedThenFailsToStart() {
    final var application =
        MainSupport.createDefaultApplicationBuilder()
            .sources(CommonsModuleConfiguration.class)
            .build();

    final var mtProperty = "camunda.security.multi-tenancy.enabled";
    final var apiProperty = "camunda.security.authentication.unprotected-api";
    application.setDefaultProperties(
        Map.of(
            mtProperty, true,
            apiProperty, true));

    assertThatThrownBy(application::run)
        .isInstanceOf(BeanCreationException.class)
        .cause()
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(
            "Multi-tenancy is enabled (%s=true), but the API is unprotected (%s=true). Please enable API protection if you want to make use of multi-tenancy."
                .formatted(mtProperty, apiProperty));
  }
}
