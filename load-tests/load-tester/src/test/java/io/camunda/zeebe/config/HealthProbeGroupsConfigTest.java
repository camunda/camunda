/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/** Loads {@code application.yaml} with the {@code starter} profile and reads the health groups. */
@SpringJUnitConfig
@ContextConfiguration(
    classes = HealthProbeGroupsConfigTest.class,
    initializers = ConfigDataApplicationContextInitializer.class)
@ActiveProfiles("starter")
class HealthProbeGroupsConfigTest {

  @Autowired private Environment environment;

  @Test
  void shouldIncludeSpringAvailabilityAndConnectionInReadinessGroup() {
    // given / when
    final var readiness = healthGroup("readiness");

    // then
    assertThat(readiness).containsExactly("readinessState", "connectionReady");
  }

  @Test
  void shouldIncludeOnlyStarterLivenessInLivenessGroup() {
    // given / when
    final var liveness = healthGroup("liveness");

    // then
    assertThat(liveness).containsExactly("starterLiveness");
  }

  private List<String> healthGroup(final String group) {
    return Binder.get(environment)
        .bind(
            "management.endpoint.health.group." + group + ".include", Bindable.listOf(String.class))
        .get();
  }
}
