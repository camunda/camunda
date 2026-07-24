/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.exporter.api.ExporterConfigMerger;
import java.util.Map;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;

final class ElasticsearchExporterConfigMergerTest {

  private final ElasticsearchExporterConfigMerger merger = new ElasticsearchExporterConfigMerger();

  @Test
  void shouldSupportExactlyTheElasticsearchExporterClass() {
    assertThat(merger.supports(ElasticsearchExporter.class.getName())).isTrue();
    assertThat(merger.supports("io.camunda.zeebe.exporter.opensearch.OpensearchExporter"))
        .isFalse();
    assertThat(merger.supports("com.acme.CustomExporter")).isFalse();
  }

  @Test
  void shouldMergeTenantArgsOverRootArgsTypeAware() {
    // given — root sets url, bulk tuning and an index prefix; the tenant overrides only the prefix
    // (spelled differently), the headline use case of per-tenant exporter config
    final Map<String, Object> rootArgs =
        Map.of(
            "url", "http://root-es:9200",
            "bulk", Map.of("size", 1000, "delay", 5),
            "index", Map.of("prefix", "root"));
    final Map<String, Object> tenantArgs = Map.of("index", Map.of("PREFIX", "tenant-a"));

    // when
    final Map<String, Object> merged = merger.merge(rootArgs, tenantArgs);

    // then — the target moves, everything else is inherited
    assertThat(merged)
        .containsEntry("url", "http://root-es:9200")
        .containsEntry("bulk", Map.of("size", 1000, "delay", 5))
        .containsEntry("index", Map.of("prefix", "tenant-a"));

    // and the merged args bind into the real config class with the merged values
    final var config =
        io.camunda.zeebe.broker.exporter.context.ExporterConfiguration.fromArgs(
            ElasticsearchExporterConfiguration.class, merged);
    assertThat(config.url).isEqualTo("http://root-es:9200");
    assertThat(config.index.prefix).isEqualTo("tenant-a");
    assertThat(config.bulk.size).isEqualTo(1000);
  }

  @Test
  void shouldPreserveCustomerLegacyIndexArgWhenHelmChartMigratesEsExporterToUnifiedConfig() {
    // Reproduces the Helm migration that drives PR #55950: the chart moves the Elasticsearch
    // exporter from the legacy zeebe.broker.exporters.* path to the unified
    // camunda.data.exporters.* path. BrokerBasedPropertiesOverride.populateFromExporters merges
    // these as merge(legacyArgs, unifiedArgs) — legacy is the base (root position), the chart's
    // unified config is the overlay (tenant position).

    // base = a customer's leftover LEGACY override (zeebe.broker.exporters.elasticsearch.args.*):
    // they disabled ES template management via an env var; the chart does not set
    // index.createTemplate.
    final Map<String, Object> legacyCustomerArgs = Map.of("index", Map.of("createTemplate", false));

    // overlay = the ACTUAL args the 8.10 chart emits for the Elasticsearch exporter
    // (charts/camunda-platform-8.10/templates/orchestration/files/_application.yaml). Values are
    // quoted in the template, so numeric/boolean args arrive as strings — kept as-is here to stay
    // faithful.
    final Map<String, Object> helmUnifiedArgs =
        Map.of(
            "url", "http://camunda-elasticsearch:9200",
            "index", Map.of("prefix", "custom-prefix", "numberOfReplicas", "2"),
            "retention", Map.of("enabled", "true", "minimumAge", "30d", "policyName", "zeebe-ilm"));

    // when — exactly what populateFromExporters does
    final Map<String, Object> merged = merger.merge(legacyCustomerArgs, helmUnifiedArgs);

    // then — bind the merged args into the real config class and assert the resolved end state
    final var config =
        io.camunda.zeebe.broker.exporter.context.ExporterConfiguration.fromArgs(
            ElasticsearchExporterConfiguration.class, merged);
    assertThat(config.url).isEqualTo("http://camunda-elasticsearch:9200"); // chart value applied
    assertThat(config.index.prefix).isEqualTo("custom-prefix"); // chart value applied
    assertThat(config.index.getNumberOfReplicas()).isEqualTo(2); // chart-only index arg applied
    assertThat(config.index.createTemplate).isFalse(); // CUSTOMER legacy sibling SURVIVES
    assertThat(config.retention.isEnabled()).isTrue(); // chart-only nested block applied
    assertThat(config.retention.getMinimumAge()).isEqualTo("30d");
  }

  @Test
  void shouldBeDiscoverableViaServiceLoader() {
    assertThat(ServiceLoader.load(ExporterConfigMerger.class))
        .anySatisfy(m -> assertThat(m).isInstanceOf(ElasticsearchExporterConfigMerger.class));
  }
}
