/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.beanoverrides;

import io.camunda.configuration.ExporterArgsMergers;
import io.camunda.configuration.ExporterResourceCollisions;
import io.camunda.configuration.UnifiedConfigurationException;
import io.camunda.zeebe.broker.system.configuration.ExporterCfg;
import io.camunda.zeebe.exporter.api.ExporterConfigMerger;
import io.camunda.zeebe.exporter.api.ExporterConfigMerger.ExporterIsolationClaim;
import io.camunda.zeebe.util.VisibleForTesting;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.NullMarked;

/**
 * Rejects two exporters in one resolved {@code BrokerBasedProperties.getExporters()} map —
 * including the autoconfigured {@code camundaexporter}/{@code rdbms} — that would write into the
 * same index-write-target or share a lifecycle policy.
 *
 * <p>Independent of the physical-tenants cross-tenant rules ({@code
 * GenericExporterIsolationValidation}): those compare across tenants and no-op for a single tenant.
 */
@NullMarked
public final class ExporterIsolationValidation {

  private ExporterIsolationValidation() {}

  public static void validate(final Map<String, ExporterCfg> exporters) {
    validate(exporters, ExporterArgsMergers.load());
  }

  @VisibleForTesting
  static void validate(
      final Map<String, ExporterCfg> exporters, final List<ExporterConfigMerger> mergers) {
    final ExporterResourceCollisions.Accumulator accumulator =
        new ExporterResourceCollisions.Accumulator();
    exporters.forEach(
        (exporterId, exporter) ->
            claimsOf(mergers, exporterId, exporter)
                .forEach(claim -> accumulator.add(exporterId, claim)));

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
}
