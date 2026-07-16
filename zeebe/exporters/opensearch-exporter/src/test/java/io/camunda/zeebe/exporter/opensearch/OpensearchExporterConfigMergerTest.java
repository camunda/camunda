/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.opensearch;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.exporter.api.ExporterConfigMerger;
import java.util.Map;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;

final class OpensearchExporterConfigMergerTest {

  private final OpensearchExporterConfigMerger merger = new OpensearchExporterConfigMerger();

  @Test
  void shouldSupportExactlyTheOpensearchExporterClass() {
    assertThat(merger.supports(OpensearchExporter.class.getName())).isTrue();
    assertThat(merger.supports("io.camunda.zeebe.exporter.ElasticsearchExporter")).isFalse();
    assertThat(merger.supports("com.acme.CustomExporter")).isFalse();
  }

  @Test
  void shouldMergeTenantArgsOverRootArgsTypeAware() {
    // given — root sets url, bulk tuning and an index prefix; the tenant overrides only the prefix
    final Map<String, Object> rootArgs =
        Map.of(
            "url", "http://root-os:9200",
            "bulk", Map.of("size", 1000, "delay", 5),
            "index", Map.of("prefix", "root"));
    final Map<String, Object> tenantArgs = Map.of("index", Map.of("PREFIX", "tenant-a"));

    // when
    final Map<String, Object> merged = merger.merge(rootArgs, tenantArgs);

    // then — the target moves, everything else is inherited
    assertThat(merged)
        .containsEntry("url", "http://root-os:9200")
        .containsEntry("bulk", Map.of("size", 1000, "delay", 5))
        .containsEntry("index", Map.of("prefix", "tenant-a"));

    // and the merged args bind into the real config class with the merged values
    final var config =
        io.camunda.zeebe.broker.exporter.context.ExporterConfiguration.fromArgs(
            OpensearchExporterConfiguration.class, merged);
    assertThat(config.url).isEqualTo("http://root-os:9200");
    assertThat(config.index.prefix).isEqualTo("tenant-a");
    assertThat(config.bulk.size).isEqualTo(1000);
  }

  @Test
  void shouldBeDiscoverableViaServiceLoader() {
    assertThat(ServiceLoader.load(ExporterConfigMerger.class))
        .anySatisfy(m -> assertThat(m).isInstanceOf(OpensearchExporterConfigMerger.class));
  }
}
