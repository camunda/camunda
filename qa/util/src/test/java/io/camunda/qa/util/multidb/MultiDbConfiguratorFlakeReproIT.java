/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.qa.util.multidb;

import static org.assertj.core.api.Assertions.assertThatCode;

import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.qa.util.cluster.TestCamundaApplication;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

/**
 * Reproduces the cross-test config leak behind the DocumentStoreS3CompatibleIT flakes: a previous
 * test's app pins its Environment (containing legacy 'none' database properties) into the static
 * field of {@link UnifiedConfigurationHelper}; the next test's MultiDbConfigurator then trips the
 * legacy-vs-unified validation while merely building its configuration.
 */
class MultiDbConfiguratorFlakeReproIT {

  @AfterEach
  void unpinStaleEnvironment() {
    new UnifiedConfigurationHelper(new StandardEnvironment());
  }

  @Test
  void shouldConfigureWhenPreviousTestPinnedLegacyNoneEnvironment() {
    // given -- some earlier test in the same JVM booted an app whose Spring Environment
    // declares a legacy database property. Constructing the helper is exactly what Spring
    // does when that app's context starts; it pins the environment statically.
    final var previousTestsEnvironment = new StandardEnvironment();
    previousTestsEnvironment
        .getPropertySources()
        .addFirst(
            new MapPropertySource("previous-test-app", Map.of("camunda.database.type", "none")));
    new UnifiedConfigurationHelper(previousTestsEnvironment);

    // when -- the next test configures a fresh, unrelated app for Elasticsearch
    final var configurator = new MultiDbConfigurator(new TestCamundaApplication());

    // then -- the stale environment must not leak into this app's configuration
    assertThatCode(() -> configurator.configureElasticsearchSupport("http://localhost", "repro"))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldConfigureWhenNoStaleEnvironmentIsPinned() {
    // given -- fresh JVM fork: no previous app ever pinned an environment with legacy values
    final var configurator = new MultiDbConfigurator(new TestCamundaApplication());

    // then -- the exact same call succeeds, which is why the flake is order-dependent
    assertThatCode(() -> configurator.configureElasticsearchSupport("http://localhost", "repro"))
        .doesNotThrowAnyException();
  }
}
