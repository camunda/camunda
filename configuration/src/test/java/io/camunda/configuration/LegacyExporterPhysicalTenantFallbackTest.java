/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.configuration.beanoverrides.BrokerBasedPropertiesOverride;
import io.camunda.configuration.beans.BrokerBasedProperties;
import io.camunda.configuration.physicaltenants.PhysicalTenantResolver;
import io.camunda.zeebe.broker.system.configuration.ExporterCfg;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

class LegacyExporterPhysicalTenantFallbackTest {

  private static final String ES_CLASS = "io.camunda.zeebe.exporter.ElasticsearchExporter";
  private static final String LEGACY_ONLY_ID = "legacyes";

  private static Map<String, ExporterCfg> resolveThroughResolverAlone(
      final Environment environment, final Camunda rootCamunda, final String tenantId) {
    final PhysicalTenantResolver resolver = PhysicalTenantResolver.of(environment, rootCamunda);
    return BrokerBasedPropertiesOverride.convert(resolver.forPhysicalTenant(tenantId))
        .getExporters();
  }

  @Nested
  @SpringJUnitConfig({
    UnifiedConfiguration.class,
    BrokerBasedPropertiesOverride.class,
    UnifiedConfigurationHelper.class
  })
  @ActiveProfiles("broker")
  @TestPropertySource(
      properties = {
        "zeebe.broker.exporters." + LEGACY_ONLY_ID + ".class-name=" + ES_CLASS,
        "zeebe.broker.exporters." + LEGACY_ONLY_ID + ".args.url=http://legacy:9200"
      })
  class LegacyOnlyExporter {

    @Autowired private BrokerBasedProperties rootBrokerProperties;
    @Autowired private Camunda rootCamunda;
    @Autowired private Environment environment;

    @Test
    void shouldSupportALegacyOnlyExporterOnTheRootBean() {
      // then
      assertThat(rootBrokerProperties.getExporters()).containsKey(LEGACY_ONLY_ID);
      assertThat(rootBrokerProperties.getExporters().get(LEGACY_ONLY_ID).getArgs())
          .containsEntry("url", "http://legacy:9200");
    }

    @Test
    void shouldRecordWhetherTheLegacyNamespaceSurvivesTheResolver() {
      // when
      final var resolved =
          resolveThroughResolverAlone(
              environment, rootCamunda, PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID);

      // then
      assertThat(resolved).doesNotContainKey(LEGACY_ONLY_ID);
    }

    @Test
    void shouldRestoreTheLegacyExporterByFillingGapsFromTheLegacyProperties() {
      // given
      final var resolved =
          new LinkedHashMap<>(
              resolveThroughResolverAlone(
                  environment, rootCamunda, PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID));

      // when
      rootBrokerProperties.getExporters().forEach(resolved::putIfAbsent);

      // then
      assertThat(resolved).containsKey(LEGACY_ONLY_ID);
      assertThat(resolved.get(LEGACY_ONLY_ID).getArgs()).containsEntry("url", "http://legacy:9200");
    }
  }

  @Nested
  @SpringJUnitConfig({
    UnifiedConfiguration.class,
    BrokerBasedPropertiesOverride.class,
    UnifiedConfigurationHelper.class
  })
  @ActiveProfiles("broker")
  @TestPropertySource(
      properties = {
        "camunda.data.exporters.rootes.class-name=" + ES_CLASS,
        "camunda.data.exporters.rootes.args.url=http://root:9200",
        "camunda.physical-tenants.default.data.exporters-assigned=",
        "camunda.physical-tenants.tenanta.security.authorizations.enabled=false",
        "camunda.physical-tenants.tenanta.data.exporters-assigned[0]=rootes",
        "camunda.physical-tenants.tenanta.data.secondary-storage.elasticsearch.index-prefix=tenanta"
      })
  class DeassignedRootExporter {

    @Autowired private Camunda rootCamunda;
    @Autowired private Environment environment;

    @Test
    void shouldNarrowARootExporterAwayFromTheDefaultTenantThatDeassignedIt() {
      // when
      final var forDefault =
          resolveThroughResolverAlone(
              environment, rootCamunda, PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID);

      // then
      assertThat(forDefault).doesNotContainKey("rootes");
    }

    @Test
    void shouldKeepARootExporterForTheTenantThatAssignedIt() {
      // when
      final var forTenantA = resolveThroughResolverAlone(environment, rootCamunda, "tenanta");

      // then
      assertThat(forTenantA).containsKey("rootes");
    }
  }
}
