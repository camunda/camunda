/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.beanoverrides.GatewayBasedPropertiesOverride;
import io.camunda.configuration.beans.GatewayBasedProperties;
import io.camunda.zeebe.gateway.impl.configuration.FilterCfg;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig({
  UnifiedConfiguration.class,
  GatewayBasedPropertiesOverride.class,
  UnifiedConfigurationHelper.class
})
public class ApiRestTest {

  private FilterCfg createFilterCfg(final String id, final String jarPath, final String className) {
    final var filterCfg = new FilterCfg();
    filterCfg.setId(id);
    filterCfg.setJarPath(jarPath);
    filterCfg.setClassName(className);
    return filterCfg;
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.api.rest.filters.0.id=0IdNew",
        "camunda.api.rest.filters.0.jar-path=0JarPathNew",
        "camunda.api.rest.filters.0.class-name=0ClassNameNew",
        "camunda.api.rest.filters.1.id=1IdNew",
        "camunda.api.rest.filters.1.jar-path=1JarPathNew",
        "camunda.api.rest.filters.1.class-name=1ClassNameNew"
      })
  class WithOnlyUnifiedConfigSet {
    final GatewayBasedProperties gatewayCfg;

    WithOnlyUnifiedConfigSet(@Autowired final GatewayBasedProperties gatewayCfg) {
      this.gatewayCfg = gatewayCfg;
    }

    @Test
    void shouldContainFilters() {
      final var expectedFilterCfg0 = createFilterCfg("0IdNew", "0JarPathNew", "0ClassNameNew");
      final var expectedFilterCfg1 = createFilterCfg("1IdNew", "1JarPathNew", "1ClassNameNew");

      assertThat(gatewayCfg.getFilters())
          .hasSize(2)
          .usingRecursiveFieldByFieldElementComparator()
          .containsExactly(expectedFilterCfg0, expectedFilterCfg1);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "zeebe.gateway.filters.0.id=0IdLegacy",
        "zeebe.gateway.filters.0.jarPath=0JarPathLegacy",
        "zeebe.gateway.filters.0.className=0ClassNameLegacy",
        "zeebe.gateway.filters.1.id=1IdLegacy",
        "zeebe.gateway.filters.1.jarPath=1JarPathLegacy",
        "zeebe.gateway.filters.1.className=1ClassNameLegacy"
      })
  class WithOnlyLegacySet {
    final GatewayBasedProperties gatewayCfg;

    WithOnlyLegacySet(@Autowired final GatewayBasedProperties gatewayCfg) {
      this.gatewayCfg = gatewayCfg;
    }

    @Test
    void shouldContainFilters() {
      final var expectedFilterCfg0 =
          createFilterCfg("0IdLegacy", "0JarPathLegacy", "0ClassNameLegacy");
      final var expectedFilterCfg1 =
          createFilterCfg("1IdLegacy", "1JarPathLegacy", "1ClassNameLegacy");

      assertThat(gatewayCfg.getFilters())
          .hasSize(2)
          .usingRecursiveFieldByFieldElementComparator()
          .containsExactly(expectedFilterCfg0, expectedFilterCfg1);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        // new
        "camunda.api.rest.filters.0.id=0IdNew",
        "camunda.api.rest.filters.0.jar-path=0JarPathNew",
        "camunda.api.rest.filters.0.class-name=0ClassNameNew",
        "camunda.api.rest.filters.1.id=1IdNew",
        "camunda.api.rest.filters.1.jar-path=1JarPathNew",
        "camunda.api.rest.filters.1.class-name=1ClassNameNew",
        // legacy
        "zeebe.gateway.filters.0.id=0IdLegacy",
        "zeebe.gateway.filters.0.jarPath=0JarPathLegacy",
        "zeebe.gateway.filters.0.className=0ClassNameLegacy",
        "zeebe.gateway.filters.1.id=1IdLegacy",
        "zeebe.gateway.filters.1.jarPath=1JarPathLegacy",
        "zeebe.gateway.filters.1.className=1ClassNameLegacy"
      })
  class WithNewAndLegacySet {
    final GatewayBasedProperties gatewayCfg;

    WithNewAndLegacySet(@Autowired final GatewayBasedProperties gatewayCfg) {
      this.gatewayCfg = gatewayCfg;
    }

    @Test
    void shouldContainFiltersFromNew() {
      final var expectedFilterCfg0 = createFilterCfg("0IdNew", "0JarPathNew", "0ClassNameNew");
      final var expectedFilterCfg1 = createFilterCfg("1IdNew", "1JarPathNew", "1ClassNameNew");

      assertThat(gatewayCfg.getFilters())
          .hasSize(2)
          .usingRecursiveFieldByFieldElementComparator()
          .containsExactly(expectedFilterCfg0, expectedFilterCfg1);
    }
  }
}
