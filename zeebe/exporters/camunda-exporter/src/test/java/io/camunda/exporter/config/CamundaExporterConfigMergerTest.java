/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.exporter.CamundaExporter;
import io.camunda.zeebe.exporter.api.ExporterConfigMerger;
import java.util.Map;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;

final class CamundaExporterConfigMergerTest {

  private final CamundaExporterConfigMerger merger = new CamundaExporterConfigMerger();

  @Test
  void shouldSupportExactlyTheCamundaExporterClass() {
    assertThat(merger.supports(CamundaExporter.class.getName())).isTrue();
    assertThat(merger.supports("io.camunda.zeebe.exporter.ElasticsearchExporter")).isFalse();
    assertThat(merger.supports("com.acme.CustomExporter")).isFalse();
  }

  @Test
  void shouldMergeTenantArgsOverRootArgsTypeAware() {
    // given — an explicitly declared (multi-region duplication) CamundaExporter entry: the root
    // sets the connection and bulk tuning, the tenant redirects only the target
    final Map<String, Object> rootArgs =
        Map.of(
            "connect", Map.of("url", "http://region-b:9200", "indexPrefix", "root"),
            "bulk", Map.of("size", 500));
    final Map<String, Object> tenantArgs = Map.of("connect", Map.of("index-prefix", "tenant-a"));

    // when
    final Map<String, Object> merged = merger.merge(rootArgs, tenantArgs);

    // then — nested POJO merge: the untouched url and bulk tuning survive, the prefix moves,
    // and the differently spelled property keys collapse to one canonical key
    assertThat(merged)
        .containsEntry("connect", Map.of("url", "http://region-b:9200", "indexprefix", "tenant-a"))
        .containsEntry("bulk", Map.of("size", 500));
  }

  @Test
  void shouldBeDiscoverableViaServiceLoader() {
    assertThat(ServiceLoader.load(ExporterConfigMerger.class))
        .anySatisfy(m -> assertThat(m).isInstanceOf(CamundaExporterConfigMerger.class));
  }
}
