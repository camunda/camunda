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
import io.camunda.zeebe.exporter.api.ExporterConfigMerger.ExporterIsolationClaim;
import io.camunda.zeebe.exporter.support.ExporterIsolationClaims;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
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

  @Test
  void shouldClaimIndexWriteTargetFromConnect() {
    // given — a CamundaExporter's destination lives in its `connect` block
    final Map<String, Object> args =
        Map.of(
            "connect",
            Map.of("type", "opensearch", "url", "http://os-a:9200", "indexPrefix", "tenant-a"));

    // when
    final Set<ExporterIsolationClaim> claims = merger.isolationClaims(args);

    // then — exactly one claim, in the index-write-target domain; the engine type discriminates
    assertThat(claims).hasSize(1);
    final ExporterIsolationClaim index =
        only(claims, ExporterIsolationClaims.INDEX_WRITE_TARGET_DOMAIN);
    assertThat(index.description()).contains("opensearch", "http://os-a:9200", "tenant-a");
  }

  @Test
  void shouldPreferConnectUrlsListOverSingleUrl() {
    // given — connect carries both a urls list and a single url; the list wins when set
    final Map<String, Object> args =
        Map.of(
            "connect",
            Map.of(
                "urls",
                List.of("http://es-1:9200", "http://es-2:9200"),
                "url",
                "http://ignored:9200"));

    // when
    final ExporterIsolationClaim index =
        only(merger.isolationClaims(args), ExporterIsolationClaims.INDEX_WRITE_TARGET_DOMAIN);

    // then
    assertThat(index.description()).contains("http://es-1:9200", "http://es-2:9200");
    assertThat(index.description()).doesNotContain("ignored");
  }

  @Test
  void shouldFallBackToSingleUrlWhenConnectUrlsHasNoUsableEntry() {
    // given — a urls list that yields nothing usable (blank entries); without a fallback the claim
    // would carry an empty connection and every such exporter would look like it shares a cluster
    final Map<String, Object> args =
        Map.of("connect", Map.of("urls", List.of("", "   "), "url", "http://es-fallback:9200"));

    // when
    final ExporterIsolationClaim index =
        only(merger.isolationClaims(args), ExporterIsolationClaims.INDEX_WRITE_TARGET_DOMAIN);

    // then the single url is used instead
    assertThat(index.description()).contains("http://es-fallback:9200");
  }

  @Test
  void shouldClaimTheSameTargetRegardlessOfSurroundingWhitespaceInAUrl() {
    // given — the same cluster written with and without stray whitespace and a trailing slash
    final ExporterIsolationClaim padded =
        only(
            merger.isolationClaims(Map.of("connect", Map.of("url", "  http://es-a:9200/  "))),
            ExporterIsolationClaims.INDEX_WRITE_TARGET_DOMAIN);
    final ExporterIsolationClaim plain =
        only(
            merger.isolationClaims(Map.of("connect", Map.of("url", "http://es-a:9200"))),
            ExporterIsolationClaims.INDEX_WRITE_TARGET_DOMAIN);

    // when / then the identities are equal, so the two collide
    assertThat(padded.identity()).isEqualTo(plain.identity());
  }

  @Test
  void shouldClaimIndexWriteTargetWithConnectDefaultsWhenArgsOmitThem() {
    // given — empty args: the destination must come from ConnectConfiguration's own defaults
    // when
    final Set<ExporterIsolationClaim> claims = merger.isolationClaims(Map.of());

    // then
    assertThat(claims).hasSize(1);
    assertThat(only(claims, ExporterIsolationClaims.INDEX_WRITE_TARGET_DOMAIN).description())
        .contains("elasticsearch", "http://localhost:9200");
  }

  @Test
  void shouldNeverClaimALifecyclePolicy() {
    // given — even with a history/retention block, the CamundaExporter does not create/manage the
    // ILM/ISM policy itself (the schema manager does), so it never claims the lifecycle-policy
    // domain
    final Map<String, Object> args =
        Map.of(
            "connect", Map.of("url", "http://es-a:9200"),
            "history", Map.of("retention", Map.of("enabled", true, "policyName", "camunda-x")));

    // when / then — only an index-write-target claim, no lifecycle-policy claim
    assertThat(merger.isolationClaims(args))
        .singleElement()
        .satisfies(
            c ->
                assertThat(c.domain())
                    .isEqualTo(ExporterIsolationClaims.INDEX_WRITE_TARGET_DOMAIN));
  }

  private static ExporterIsolationClaim only(
      final Set<ExporterIsolationClaim> claims, final String domain) {
    return claims.stream().filter(c -> c.domain().equals(domain)).findFirst().orElseThrow();
  }
}
