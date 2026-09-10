/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.beanoverrides;

import io.camunda.configuration.Camunda;
import io.camunda.configuration.DocumentBasedSecondaryStorageDatabase;
import io.camunda.configuration.ExporterArgsMergers;
import io.camunda.configuration.ExporterResourceCollisions;
import io.camunda.configuration.SecondaryStorage;
import io.camunda.configuration.UnifiedConfigurationException;
import io.camunda.zeebe.broker.system.configuration.ExporterCfg;
import io.camunda.zeebe.exporter.api.ExporterConfigMerger;
import io.camunda.zeebe.exporter.api.ExporterConfigMerger.ExporterIsolationClaim;
import io.camunda.zeebe.exporter.support.ExporterIsolationClaims;
import io.camunda.zeebe.util.VisibleForTesting;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.NullMarked;

/**
 * Rejects two exporters in one resolved {@code BrokerBasedProperties.getExporters()} map —
 * including the autoconfigured {@code camundaexporter}/{@code rdbms} — that would write into the
 * same index-write-target or share a lifecycle policy, and rejects a generic exporter sharing a
 * lifecycle policy with the secondary storage's own (schema-manager-owned) retention or
 * usage-metrics policy.
 *
 * <p>Independent of the physical-tenants cross-tenant rules ({@code
 * GenericExporterIsolationValidation}/{@code RetentionPolicyIsolationValidation}): those compare
 * across tenants and no-op for a single tenant.
 */
@NullMarked
public final class ExporterIsolationValidation {

  private static final String SECONDARY_STORAGE_OWNER_ID = "secondary-storage";

  private ExporterIsolationValidation() {}

  public static void validate(final Map<String, ExporterCfg> exporters, final Camunda camunda) {
    validate(exporters, camunda, ExporterArgsMergers.load());
  }

  @VisibleForTesting
  static void validate(
      final Map<String, ExporterCfg> exporters,
      final Camunda camunda,
      final List<ExporterConfigMerger> mergers) {
    final ExporterResourceCollisions.Accumulator accumulator =
        new ExporterResourceCollisions.Accumulator();
    exporters.forEach(
        (exporterId, exporter) ->
            claimsOf(mergers, exporterId, exporter)
                .forEach(claim -> accumulator.add(exporterOwnerId(exporterId), claim)));
    secondaryStorageLifecyclePolicyClaims(camunda)
        .forEach(claim -> accumulator.add(SECONDARY_STORAGE_OWNER_ID, claim));

    final List<String> collisions = accumulator.collisions();
    if (!collisions.isEmpty()) {
      throw new UnifiedConfigurationException(
          "Exporters must not share an index-write-target or lifecycle-policy resource, or they "
              + "would silently collide — two exporters writing into the same indices, or one "
              + "exporter's retention policy deleting another's indices. Give each exporter that "
              + "shares a cluster a distinct index prefix and lifecycle-policy name. Conflicts: "
              + String.join("; ", collisions));
    }
  }

  // namespace the exporterId so a user-defined exporter named "secondary-storage"
  // can never collide, string-for-string, with "secondary-storage" synthetic owner
  private static String exporterOwnerId(final String exporterId) {
    return String.format("exporter '%s'", exporterId);
  }

  private static Set<ExporterIsolationClaim> claimsOf(
      final List<ExporterConfigMerger> mergers,
      final String exporterId,
      final ExporterCfg exporter) {
    final String context = String.format("exporter '%s'", exporterId);
    final ExporterConfigMerger merger =
        ExporterArgsMergers.find(mergers, exporter.getClassName(), context);
    if (merger == null) {
      return Set.of();
    }
    return ExporterArgsMergers.isolationClaims(merger, exporter.getArgs(), context);
  }

  /**
   * The secondary storage's own ILM/ISM lifecycle policies — created directly by the schema
   * manager, not by any {@link ExporterConfigMerger} — so a generic exporter that manages a policy
   * under the same name would silently overwrite it. Skipped for {@code rdbms}/{@code none}
   * secondary storage (no ILM/ISM policy involved) and whenever retention is disabled (no policy is
   * created at all).
   */
  private static Set<ExporterIsolationClaim> secondaryStorageLifecyclePolicyClaims(
      final Camunda camunda) {
    final SecondaryStorage secondaryStorage = camunda.getData().getSecondaryStorage();
    if (!secondaryStorage.getRetention().isEnabled()) {
      return Set.of();
    }
    return secondaryStorage
        .elasticsearchOrOpensearch()
        .map(
            database -> {
              final String engine = secondaryStorage.getType().name();
              final List<String> urls = urlsOf(database);
              final Set<ExporterIsolationClaim> claims = new LinkedHashSet<>();
              claims.add(
                  ExporterIsolationClaims.lifecyclePolicy(
                      engine, urls, database.getHistory().getPolicyName()));
              claims.add(
                  ExporterIsolationClaims.lifecyclePolicy(
                      engine, urls, database.getHistory().getUsageMetricsPolicyName()));
              return claims;
            })
        .orElse(Set.of());
  }

  private static List<String> urlsOf(final DocumentBasedSecondaryStorageDatabase database) {
    final List<String> urls = database.getUrls();
    return (urls != null && !urls.isEmpty()) ? urls : List.of(database.getUrl());
  }
}
