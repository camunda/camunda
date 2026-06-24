/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.beanoverrides.BrokerBasedPropertiesOverride;
import io.camunda.configuration.beans.BrokerBasedProperties;
import io.camunda.zeebe.gateway.impl.configuration.FilterCfg;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig({
  UnifiedConfiguration.class,
  BrokerBasedPropertiesOverride.class,
  UnifiedConfigurationHelper.class
})
@ActiveProfiles("broker")
public class ApiRestBrokerFiltersTest {

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
    final BrokerBasedProperties brokerCfg;

    WithOnlyUnifiedConfigSet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetFilters() {
      final var expectedFilterCfg0 = createFilterCfg("0IdNew", "0JarPathNew", "0ClassNameNew");
      final var expectedFilterCfg1 = createFilterCfg("1IdNew", "1JarPathNew", "1ClassNameNew");

      assertThat(brokerCfg.getGateway().getFilters())
          .hasSize(2)
          .usingRecursiveFieldByFieldElementComparator()
          .containsExactly(expectedFilterCfg0, expectedFilterCfg1);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "zeebe.gateway.filters.0.id=0IdLegacyGateway",
        "zeebe.gateway.filters.0.jarPath=0JarPathLegacyGateway",
        "zeebe.gateway.filters.0.className=0ClassNameLegacyGateway",
        "zeebe.gateway.filters.1.id=1IdLegacyGateway",
        "zeebe.gateway.filters.1.jarPath=1JarPathLegacyGateway",
        "zeebe.gateway.filters.1.className=1ClassNameLegacyGateway"
      })
  class WithOnlyLegacyGatewayFiltersSet {
    final BrokerBasedProperties brokerCfg;

    WithOnlyLegacyGatewayFiltersSet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldNotSetFiltersFromLegacyGatewaysFilters() {
      assertThat(brokerCfg.getGateway().getFilters()).isEmpty();
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "zeebe.broker.gateway.filters.0.id=0IdLegacyBroker",
        "zeebe.broker.gateway.filters.0.jarPath=0JarPathLegacyBroker",
        "zeebe.broker.gateway.filters.0.className=0ClassNameLegacyBroker",
        "zeebe.broker.gateway.filters.1.id=1IdLegacyBroker",
        "zeebe.broker.gateway.filters.1.jarPath=1JarPathLegacyBroker",
        "zeebe.broker.gateway.filters.1.className=1ClassNameLegacyBroker"
      })
  class WithOnlyLegacyBrokerFiltersSet {
    final BrokerBasedProperties brokerCfg;

    WithOnlyLegacyBrokerFiltersSet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetFiltersFromLegacyBrokerFilters() {
      final var expectedFilterCfg0 =
          createFilterCfg("0IdLegacyBroker", "0JarPathLegacyBroker", "0ClassNameLegacyBroker");
      final var expectedFilterCfg1 =
          createFilterCfg("1IdLegacyBroker", "1JarPathLegacyBroker", "1ClassNameLegacyBroker");

      assertThat(brokerCfg.getGateway().getFilters())
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
        // legacy gateway filters
        "zeebe.gateway.filters.0.id=0IdLegacyGateway",
        "zeebe.gateway.filters.0.jarPath=0JarPathLegacyGateway",
        "zeebe.gateway.filters.0.className=0ClassNameLegacyGateway",
        "zeebe.gateway.filters.1.id=1IdLegacyGateway",
        "zeebe.gateway.filters.1.jarPath=1JarPathLegacyGateway",
        "zeebe.gateway.filters.1.className=1ClassNameLegacyGateway",
        // legacy broker filters
        "zeebe.broker.gateway.filters.0.id=0IdLegacyBroker",
        "zeebe.broker.gateway.filters.0.jarPath=0JarPathLegacyBroker",
        "zeebe.broker.gateway.filters.0.className=0ClassNameLegacyBroker",
        "zeebe.broker.gateway.filters.1.id=1IdLegacyBroker",
        "zeebe.broker.gateway.filters.1.jarPath=1JarPathLegacyBroker",
        "zeebe.broker.gateway.filters.1.className=1ClassNameLegacyBroker",
      })
  class WithNewAndLegacySet {
    final BrokerBasedProperties brokerCfg;

    WithNewAndLegacySet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetFiltersFromNew() {
      final var expectedFilterCfg0 = createFilterCfg("0IdNew", "0JarPathNew", "0ClassNameNew");
      final var expectedFilterCfg1 = createFilterCfg("1IdNew", "1JarPathNew", "1ClassNameNew");

      assertThat(brokerCfg.getGateway().getFilters())
          .hasSize(2)
          .usingRecursiveFieldByFieldElementComparator()
          .containsExactly(expectedFilterCfg0, expectedFilterCfg1);
    }
  }
}
