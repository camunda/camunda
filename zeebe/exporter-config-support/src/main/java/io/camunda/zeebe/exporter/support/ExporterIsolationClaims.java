/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.support;

import io.camunda.zeebe.exporter.api.ExporterConfigMerger.ExporterIsolationClaim;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.NullMarked;

/**
 * Builders for the {@link ExporterIsolationClaim}s common to the search-store exporters
 * (Elasticsearch, OpenSearch, and the CamundaExporter) — kept here, shared, so those exporters
 * produce <em>equal</em> keys for the same resource and their claims collide cross-exporter (an ES
 * exporter and a CamundaExporter both pointed at the same Elasticsearch cluster/prefix must be
 * detected). Other exporters with different notions of "where they write" build their own {@link
 * ExporterIsolationClaim}s directly.
 *
 * <p>Each key is a structured map of already-normalized fields (no fragile delimiter join): urls
 * are trimmed, stripped of a trailing slash, lowercased and sorted; the index prefix is taken
 * as-is; the lifecycle-policy name is taken as-is (policy names are case-sensitive on both
 * engines). Values are used verbatim otherwise — the application consumes them unmodified, so the
 * identity must too. Normalization mirrors {@code StorageIdentity} so every physical-tenant
 * isolation rule agrees on what "the same cluster" means.
 */
@NullMarked
public final class ExporterIsolationClaims {

  /** Two exporters writing records into the same cluster under the same index prefix collide. */
  public static final String INDEX_WRITE_TARGET_DOMAIN = "index-write-target";

  /**
   * Two exporters managing the same cluster-global ILM/ISM lifecycle policy collide — independently
   * of the index prefix, because a lifecycle policy is not scoped to any prefix.
   */
  public static final String LIFECYCLE_POLICY_DOMAIN = "lifecycle-policy";

  private ExporterIsolationClaims() {}

  /**
   * The index write target: the {@code (engine, cluster, indexPrefix)} an exporter writes records
   * into.
   */
  public static ExporterIsolationClaim indexWriteTarget(
      final String engine, final List<String> urls, final String indexPrefix) {
    final String kind = engine.toLowerCase(Locale.ROOT);
    final List<String> connection = normalizeUrls(urls);
    final Map<String, Object> identity =
        Map.of("engine", kind, "connection", connection, "namespace", indexPrefix);
    final String description =
        String.format(
            "generic-exporter write target [kind=%s, connection=%s, namespace='%s']",
            kind, render(connection), indexPrefix);
    return new ExporterIsolationClaim(INDEX_WRITE_TARGET_DOMAIN, identity, description);
  }

  /**
   * The lifecycle policy an exporter creates and manages: the {@code (engine, cluster,
   * policyName)}, deliberately excluding the index prefix (the policy is cluster-global).
   */
  public static ExporterIsolationClaim lifecyclePolicy(
      final String engine, final List<String> urls, final String policyName) {
    final String kind = engine.toLowerCase(Locale.ROOT);
    final List<String> connection = normalizeUrls(urls);
    final Map<String, Object> identity =
        Map.of("engine", kind, "connection", connection, "policyName", policyName);
    final String description =
        String.format(
            "generic-exporter lifecycle policy [kind=%s, connection=%s, policyName='%s']",
            kind, render(connection), policyName);
    return new ExporterIsolationClaim(LIFECYCLE_POLICY_DOMAIN, identity, description);
  }

  private static List<String> normalizeUrls(final List<String> rawUrls) {
    return rawUrls.stream().map(ExporterIsolationClaims::normalizeUrl).sorted().toList();
  }

  private static String normalizeUrl(final String url) {
    // trim first, exactly as StorageIdentity does: a stray space around a url must not make two
    // exporters look like they point at different clusters
    String normalized = url.trim();
    if (normalized.endsWith("/")) {
      normalized = normalized.substring(0, normalized.length() - 1);
    }
    return normalized.toLowerCase(Locale.ROOT);
  }

  private static String render(final List<String> connection) {
    return connection.size() == 1 ? connection.get(0) : connection.toString();
  }
}
