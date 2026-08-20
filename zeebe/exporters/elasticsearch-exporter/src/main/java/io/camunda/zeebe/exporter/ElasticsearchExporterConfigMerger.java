/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter;

import io.camunda.zeebe.exporter.api.ExporterConfigMerger;
import io.camunda.zeebe.exporter.support.ExporterConfigMergeSupport;
import io.camunda.zeebe.exporter.support.ExporterIsolationClaims;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Enables per-physical-tenant partial overrides of a root-declared Elasticsearch exporter's {@code
 * args} (ADR-0008 §3): registered via {@code META-INF/services}, discovered with {@link
 * java.util.ServiceLoader} at configuration-resolution time.
 */
@NullMarked
public final class ElasticsearchExporterConfigMerger implements ExporterConfigMerger {

  private static final String ENGINE = "elasticsearch";

  @Override
  public boolean supports(final String className) {
    return ElasticsearchExporter.class.getName().equals(className);
  }

  @Override
  public Map<String, Object> merge(
      final Map<String, Object> rootArgs, final Map<String, Object> tenantArgs) {
    return ExporterConfigMergeSupport.merge(
        ElasticsearchExporterConfiguration.class, rootArgs, tenantArgs);
  }

  @Override
  public Set<ExporterIsolationClaim> isolationClaims(final Map<String, Object> args) {
    // an Elasticsearch exporter occupies an index write target (cluster + index prefix) and, when
    // retention is enabled and self-managed, a cluster-global ILM policy; read those from the
    // normalized args and fall back to the config class's own defaults
    final ElasticsearchExporterConfiguration defaults = new ElasticsearchExporterConfiguration();
    final Map<String, Object> normalized =
        ExporterConfigMergeSupport.normalize(ElasticsearchExporterConfiguration.class, args);
    final List<String> urls = splitUrls(stringOrDefault(normalized.get("url"), defaults.url));
    final String prefix =
        nestedStringOrDefault(normalized.get("index"), "prefix", defaults.index.prefix);

    final Set<ExporterIsolationClaim> claims = new LinkedHashSet<>();
    claims.add(ExporterIsolationClaims.indexWriteTarget(ENGINE, urls, prefix));
    final String policyName = managedLifecyclePolicy(normalized, defaults);
    if (policyName != null) {
      claims.add(ExporterIsolationClaims.lifecyclePolicy(ENGINE, urls, policyName));
    }
    return claims;
  }

  /**
   * The ILM policy this exporter would create, or {@code null} if it manages none: only a
   * retention-{@code enabled} exporter that also {@code managePolicy} creates the cluster-global
   * policy; a disabled or externally-managed policy cannot collide.
   */
  private static @Nullable String managedLifecyclePolicy(
      final Map<String, Object> normalized, final ElasticsearchExporterConfiguration defaults) {
    final Map<?, ?> retention =
        normalized.get("retention") instanceof final Map<?, ?> map ? map : Map.of();
    final boolean enabled = boolOrDefault(retention.get("enabled"), defaults.retention.isEnabled());
    final boolean managePolicy =
        boolOrDefault(retention.get("managepolicy"), defaults.retention.isManagePolicy());
    if (!enabled || !managePolicy) {
      return null;
    }
    final Object policyName = retention.get("policyname");
    return policyName instanceof final String s ? s : defaults.retention.getPolicyName();
  }

  private static List<String> splitUrls(final String commaSeparated) {
    return Arrays.stream(commaSeparated.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .toList();
  }

  private static String stringOrDefault(final @Nullable Object value, final String fallback) {
    return value instanceof final String s ? s : fallback;
  }

  private static boolean boolOrDefault(final @Nullable Object value, final boolean fallback) {
    return switch (value) {
      case final Boolean b -> b;
      case final String s -> Boolean.parseBoolean(s);
      case null, default -> fallback;
    };
  }

  private static String nestedStringOrDefault(
      final @Nullable Object nested, final String key, final String fallback) {
    return nested instanceof final Map<?, ?> map && map.get(key) instanceof final String s
        ? s
        : fallback;
  }
}
