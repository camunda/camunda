/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.zeebe.mediator.factory;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.ConfigurationServiceBuilder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ZeebeAgentRunImportMediatorFactoryTest {

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void shouldBeEnabledOnlyWhenAgenticPhase2IsEnabled(final boolean phase2Enabled) {
    // given
    final ConfigurationService configurationService =
        ConfigurationServiceBuilder.createDefaultConfiguration();
    configurationService.getAgenticPhase2Configuration().setEnabled(phase2Enabled);

    // when
    final ZeebeAgentRunImportMediatorFactory factory =
        new ZeebeAgentRunImportMediatorFactory(
            null, null, configurationService, null, null, null, null);

    // then
    assertThat(factory.isEnabled()).isEqualTo(phase2Enabled);
  }
}
