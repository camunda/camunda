/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.beanoverrides.GatewayRestPropertiesOverride;
import io.camunda.configuration.beans.GatewayRestProperties;
import io.camunda.configuration.beans.LegacyGatewayRestProperties;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.mock.env.MockEnvironment;

class ApiRestWaitStatesTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(
              UnifiedConfiguration.class,
              UnifiedConfigurationHelper.class,
              GatewayRestPropertiesOverride.class);

  @Test
  void shouldEnableWaitStatesByDefault() {
    // given no wait-state configuration
    // when/then
    contextRunner.run(
        context ->
            assertThat(context.getBean(GatewayRestProperties.class).waitStatesEnabled()).isTrue());
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void shouldPropagateConfiguredWaitStatesEnabled(final boolean enabled) {
    // given
    final var runner =
        contextRunner.withPropertyValues("camunda.data.wait-states.enabled=" + enabled);

    // when/then
    runner.run(
        context ->
            assertThat(context.getBean(GatewayRestProperties.class).waitStatesEnabled())
                .isEqualTo(enabled));
  }

  @ParameterizedTest
  @ValueSource(strings = {"camunda.rest.wait-states-enabled", "CAMUNDA_REST_WAITSTATESENABLED"})
  void shouldNotExposeEffectiveWaitStatesAsALegacyRestProperty(final String property) {
    // given
    final var environment = new MockEnvironment();
    environment
        .getPropertySources()
        .addFirst(
            new SystemEnvironmentPropertySource("systemEnvironment", Map.of(property, "false")));

    // when
    final var bound =
        Binder.get(environment)
            .bind("camunda.rest", Bindable.of(LegacyGatewayRestProperties.class));

    // then
    assertThat(bound.isBound()).isFalse();
  }
}
