/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.beanoverrides;

import static org.assertj.core.api.Assertions.assertThatCode;

import io.camunda.configuration.Camunda;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.configuration.UnifiedConfigurationException;
import io.camunda.zeebe.broker.system.configuration.ExporterCfg;
import io.camunda.zeebe.exporter.api.ExporterConfigMerger;
import io.camunda.zeebe.exporter.api.ExporterConfigMerger.ExporterIsolationClaim;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class ExporterIsolationValidationTest {

  @Test
  void shouldNotFailWhenExportersClaimDistinctResources() {
    // given
    final Map<String, ExporterCfg> exporters = new LinkedHashMap<>();
    exporters.put("elasticsearch", exporterCfg("io.camunda.zeebe.exporter.ElasticsearchExporter"));
    exporters.put("camundaexporter", exporterCfg("io.camunda.exporter.CamundaExporter"));

    final List<ExporterConfigMerger> mergers =
        List.of(
            merger(
                "io.camunda.zeebe.exporter.ElasticsearchExporter",
                claim("index-write-target", Map.of("prefix", "zeebe-record"))),
            merger(
                "io.camunda.exporter.CamundaExporter",
                claim("index-write-target", Map.of("prefix", "operate-record"))));

    // when - then
    assertThatCode(() -> ExporterIsolationValidation.validate(exporters, new Camunda(), mergers))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldFailWhenTwoExportersClaimTheSameResource() {
    // given
    final Map<String, ExporterCfg> exporters = new LinkedHashMap<>();
    exporters.put("elasticsearch", exporterCfg("io.camunda.zeebe.exporter.ElasticsearchExporter"));
    exporters.put("camundaexporter", exporterCfg("io.camunda.exporter.CamundaExporter"));

    final List<ExporterConfigMerger> mergers =
        List.of(
            merger(
                "io.camunda.zeebe.exporter.ElasticsearchExporter",
                claim("index-write-target", Map.of("prefix", "zeebe-record"))),
            merger(
                "io.camunda.exporter.CamundaExporter",
                claim("index-write-target", Map.of("prefix", "zeebe-record"))));

    // when - then
    assertThatCode(() -> ExporterIsolationValidation.validate(exporters, new Camunda(), mergers))
        .isInstanceOf(UnifiedConfigurationException.class)
        .hasMessageContaining("elasticsearch")
        .hasMessageContaining("camundaexporter");
  }

  @Test
  void shouldSkipExportersWithNoResolvableMerger() {
    // given an exporter whose class has no registered merger (e.g. an external jar-loaded one)
    final Map<String, ExporterCfg> exporters = new LinkedHashMap<>();
    exporters.put("external", exporterCfg("com.example.CustomExporter"));
    exporters.put("camundaexporter", exporterCfg("io.camunda.exporter.CamundaExporter"));

    final List<ExporterConfigMerger> mergers =
        List.of(
            merger(
                "io.camunda.exporter.CamundaExporter",
                claim("index-write-target", Map.of("prefix", "operate-record"))));

    // when - then
    assertThatCode(() -> ExporterIsolationValidation.validate(exporters, new Camunda(), mergers))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldFailWhenGenericExporterSharesLifecyclePolicyWithSecondaryStorage() {
    // given
    final Camunda camunda = new Camunda();
    camunda.getData().getSecondaryStorage().setType(SecondaryStorageType.elasticsearch);
    camunda.getData().getSecondaryStorage().getRetention().setEnabled(true);
    final String policyName =
        camunda.getData().getSecondaryStorage().getElasticsearch().getHistory().getPolicyName();

    final Map<String, ExporterCfg> exporters = new LinkedHashMap<>();
    exporters.put("elasticsearch", exporterCfg("io.camunda.zeebe.exporter.ElasticsearchExporter"));

    final List<ExporterConfigMerger> mergers =
        List.of(
            merger(
                "io.camunda.zeebe.exporter.ElasticsearchExporter",
                claim(
                    "lifecycle-policy",
                    Map.of(
                        "engine",
                        "elasticsearch",
                        "connection",
                        List.of("http://localhost:9200"),
                        "policyName",
                        policyName))));

    // when - then
    assertThatCode(() -> ExporterIsolationValidation.validate(exporters, camunda, mergers))
        .isInstanceOf(UnifiedConfigurationException.class)
        .hasMessageContaining("elasticsearch")
        .hasMessageContaining("secondary-storage");
  }

  @Test
  void shouldFailWhenGenericExporterSharesUsageMetricsLifecyclePolicyWithSecondaryStorage() {
    // given
    final Camunda camunda = new Camunda();
    camunda.getData().getSecondaryStorage().setType(SecondaryStorageType.elasticsearch);
    camunda.getData().getSecondaryStorage().getRetention().setEnabled(true);
    final String usageMetricsPolicyName =
        camunda
            .getData()
            .getSecondaryStorage()
            .getElasticsearch()
            .getHistory()
            .getUsageMetricsPolicyName();

    final Map<String, ExporterCfg> exporters = new LinkedHashMap<>();
    exporters.put("elasticsearch", exporterCfg("io.camunda.zeebe.exporter.ElasticsearchExporter"));

    final List<ExporterConfigMerger> mergers =
        List.of(
            merger(
                "io.camunda.zeebe.exporter.ElasticsearchExporter",
                claim(
                    "lifecycle-policy",
                    Map.of(
                        "engine",
                        "elasticsearch",
                        "connection",
                        List.of("http://localhost:9200"),
                        "policyName",
                        usageMetricsPolicyName))));

    // when - then
    assertThatCode(() -> ExporterIsolationValidation.validate(exporters, camunda, mergers))
        .isInstanceOf(UnifiedConfigurationException.class)
        .hasMessageContaining("elasticsearch")
        .hasMessageContaining("secondary-storage");
  }

  @Test
  void shouldNotFailWhenSecondaryStorageRetentionIsDisabled() {
    // given
    final Camunda camunda = new Camunda();
    camunda.getData().getSecondaryStorage().setType(SecondaryStorageType.elasticsearch);
    // retention is disabled by default: the secondary storage creates no lifecycle policy at all

    final Map<String, ExporterCfg> exporters = new LinkedHashMap<>();
    exporters.put("elasticsearch", exporterCfg("io.camunda.zeebe.exporter.ElasticsearchExporter"));

    final List<ExporterConfigMerger> mergers =
        List.of(
            merger(
                "io.camunda.zeebe.exporter.ElasticsearchExporter",
                claim(
                    "lifecycle-policy",
                    Map.of(
                        "engine",
                        "elasticsearch",
                        "connection",
                        List.of("http://localhost:9200"),
                        "policyName",
                        "camunda-retention-policy"))));

    // when - then
    assertThatCode(() -> ExporterIsolationValidation.validate(exporters, camunda, mergers))
        .doesNotThrowAnyException();
  }

  private static ExporterCfg exporterCfg(final String className) {
    final ExporterCfg cfg = new ExporterCfg();
    cfg.setClassName(className);
    cfg.setArgs(Map.of());
    return cfg;
  }

  private static ExporterIsolationClaim claim(
      final String domain, final Map<String, Object> identity) {
    return new ExporterIsolationClaim(domain, identity, domain);
  }

  private static ExporterConfigMerger merger(
      final String className, final ExporterIsolationClaim claim) {
    return new ExporterConfigMerger() {
      @Override
      public boolean supports(final String candidateClassName) {
        return className.equals(candidateClassName);
      }

      @Override
      public Map<String, Object> merge(
          final Map<String, Object> legacyArgs, final Map<String, Object> unifiedArgs) {
        return Map.of();
      }

      @Override
      public Set<ExporterIsolationClaim> isolationClaims(final Map<String, Object> args) {
        return Set.of(claim);
      }
    };
  }
}
