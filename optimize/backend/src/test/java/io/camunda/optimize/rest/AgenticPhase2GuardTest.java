/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.optimize.rest.exceptions.NotFoundException;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.ConfigurationServiceBuilder;
import org.junit.jupiter.api.Test;

class AgenticPhase2GuardTest {

  @Test
  void shouldHideEndpointsWhenDisabledByDefault() {
    // given the shipped configuration
    final AgenticPhase2Guard guard =
        new AgenticPhase2Guard(ConfigurationServiceBuilder.createDefaultConfiguration());

    // when / then the endpoint answers 404
    assertThatThrownBy(guard::requireEnabled).isInstanceOf(NotFoundException.class);
  }

  @Test
  void shouldAllowEndpointsWhenEnabled() {
    // given
    final ConfigurationService configurationService =
        ConfigurationServiceBuilder.createDefaultConfiguration();
    configurationService.getAgenticPhase2Configuration().setEnabled(true);

    // when / then
    assertThatCode(new AgenticPhase2Guard(configurationService)::requireEnabled)
        .doesNotThrowAnyException();
  }
}
