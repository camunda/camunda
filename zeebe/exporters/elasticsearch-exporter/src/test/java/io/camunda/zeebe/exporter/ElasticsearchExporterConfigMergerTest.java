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
import io.camunda.zeebe.exporter.api.ExporterConfigMerger.ExporterIsolationClaim;
import io.camunda.zeebe.exporter.support.ExporterIsolationClaims;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
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

  @Test
  void shouldClaimIndexWriteTargetFromArgs() {
    // given — an exporter pointed at a specific cluster and index prefix, retention off (default)
    final Map<String, Object> args =
        Map.of("url", "http://es-a:9200", "index", Map.of("prefix", "tenant-a"));

    // when
    final Set<ExporterIsolationClaim> claims = merger.isolationClaims(args);

    // then — exactly one claim, in the index-write-target domain, naming the cluster and prefix
    assertThat(claims).hasSize(1);
    final ExporterIsolationClaim index =
        only(claims, ExporterIsolationClaims.INDEX_WRITE_TARGET_DOMAIN);
    assertThat(index.description()).contains("elasticsearch", "http://es-a:9200", "tenant-a");
  }

  @Test
  void shouldClaimIndexWriteTargetWithConfigDefaultsWhenArgsOmitThem() {
    // given — empty args: the destination must come from the config class's own defaults
    // when
    final Set<ExporterIsolationClaim> claims = merger.isolationClaims(Map.of());

    // then
    assertThat(claims).hasSize(1);
    assertThat(only(claims, ExporterIsolationClaims.INDEX_WRITE_TARGET_DOMAIN).description())
        .contains("http://localhost:9200", "zeebe-record");
  }

  @Test
  void shouldSplitCommaSeparatedUrlsIntoTheClaimConnection() {
    // given — the ES exporter takes a comma-separated url list
    final Map<String, Object> args = Map.of("url", "http://es-1:9200, http://es-2:9200");

    // when
    final ExporterIsolationClaim index =
        only(merger.isolationClaims(args), ExporterIsolationClaims.INDEX_WRITE_TARGET_DOMAIN);

    // then — both nodes appear in the (normalized) connection of the claim
    assertThat(index.description()).contains("http://es-1:9200", "http://es-2:9200");
  }

  @Test
  void shouldClaimLifecyclePolicyWhenRetentionEnabledAndManaged() {
    // given — retention enabled (managePolicy defaults to true), with a custom policy name
    final Map<String, Object> args =
        Map.of("retention", Map.of("enabled", true, "policyName", "tenant-a-policy"));

    // when
    final Set<ExporterIsolationClaim> claims = merger.isolationClaims(args);

    // then — an index claim AND a lifecycle-policy claim naming the managed ILM policy
    assertThat(claims).hasSize(2);
    assertThat(only(claims, ExporterIsolationClaims.LIFECYCLE_POLICY_DOMAIN).description())
        .contains("tenant-a-policy");
  }

  @Test
  void shouldClaimDefaultLifecyclePolicyNameWhenRetentionEnabledWithoutName() {
    // given — retention enabled but no explicit policy name: the fixed default applies
    final Set<ExporterIsolationClaim> claims =
        merger.isolationClaims(Map.of("retention", Map.of("enabled", true)));

    // then
    assertThat(only(claims, ExporterIsolationClaims.LIFECYCLE_POLICY_DOMAIN).description())
        .contains("zeebe-record-retention-policy");
  }

  @Test
  void shouldNotClaimLifecyclePolicyWhenRetentionDisabled() {
    // given — retention disabled (the default): the exporter creates no policy
    // when / then — only the index-write-target claim
    assertThat(merger.isolationClaims(Map.of()))
        .singleElement()
        .satisfies(
            c ->
                assertThat(c.domain())
                    .isEqualTo(ExporterIsolationClaims.INDEX_WRITE_TARGET_DOMAIN));
  }

  @Test
  void shouldNotClaimLifecyclePolicyWhenPolicyIsExternallyManaged() {
    // given — retention enabled but managePolicy=false: an externally-managed policy cannot collide
    final Map<String, Object> args =
        Map.of(
            "retention",
            Map.of("enabled", true, "managePolicy", false, "policyName", "shared-policy"));

    // when / then — no lifecycle-policy claim
    assertThat(merger.isolationClaims(args))
        .noneMatch(c -> c.domain().equals(ExporterIsolationClaims.LIFECYCLE_POLICY_DOMAIN));
  }

  private static ExporterIsolationClaim only(
      final Set<ExporterIsolationClaim> claims, final String domain) {
    return claims.stream().filter(c -> c.domain().equals(domain)).findFirst().orElseThrow();
  }
}
