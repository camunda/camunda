/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.config;

import io.camunda.exporter.CamundaExporter;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.zeebe.exporter.api.ExporterConfigMerger;
import io.camunda.zeebe.exporter.support.ExporterConfigMergeSupport;
import io.camunda.zeebe.exporter.support.ExporterIsolationClaims;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Enables per-physical-tenant partial overrides of an <em>explicitly declared</em> CamundaExporter
 * catalog entry's {@code args} — the multi-region duplication setup, where a second CamundaExporter
 * is declared under {@code data.exporters} (ADR-0008 §3). The <em>autoconfigured</em> {@code
 * camundaexporter} entry never goes through a merger: its configuration is derived from the
 * tenant's (already per-tenant-resolved) secondary-storage properties. Registered via {@code
 * META-INF/services}, discovered with {@link java.util.ServiceLoader} at configuration-resolution
 * time.
 */
@NullMarked
public final class CamundaExporterConfigMerger implements ExporterConfigMerger {

  @Override
  public boolean supports(final String className) {
    return CamundaExporter.class.getName().equals(className);
  }

  @Override
  public Map<String, Object> merge(
      final Map<String, Object> rootArgs, final Map<String, Object> tenantArgs) {
    return ExporterConfigMergeSupport.merge(ExporterConfiguration.class, rootArgs, tenantArgs);
  }

  @Override
  public Set<ExporterIsolationClaim> isolationClaims(final Map<String, Object> args) {
    // a CamundaExporter writes into the ES/OS cluster named by its `connect` block, under connect's
    // index prefix; the engine type discriminates the location, just as it does for secondary
    // storage. The isolation check skips the autoconfigured `camundaexporter` id by id, so this
    // override only ever describes an explicitly declared (tenant-private/multi-region) entry.
    final ConnectConfiguration defaults = new ExporterConfiguration().getConnect();
    final Map<String, Object> normalized =
        ExporterConfigMergeSupport.normalize(ExporterConfiguration.class, args);
    final Map<?, ?> connect =
        normalized.get("connect") instanceof final Map<?, ?> map ? map : Map.of();
    final String engine =
        stringOrDefault(connect.get("type"), defaults.getTypeEnum().toString())
            .toLowerCase(Locale.ROOT);
    final String prefix = stringOrDefault(connect.get("indexprefix"), defaults.getIndexPrefix());
    // only an index-write-target claim: the CamundaExporter does not create/manage its own ILM/ISM
    // lifecycle policy (retention is driven by the schema manager, not this exporter), so it never
    // participates in the lifecycle-policy isolation domain.
    return Set.of(
        ExporterIsolationClaims.indexWriteTarget(engine, urls(connect, defaults.getUrl()), prefix));
  }

  private static List<String> urls(final Map<?, ?> connect, final String defaultUrl) {
    // connect carries both a `urls` list and a single `url`; prefer the list, but only when it
    // actually yields a usable url — an empty or blank-only list must fall back to `url` rather
    // than collapse the claim's connection to nothing, which would make unrelated exporters look
    // like they share a cluster (mirroring StorageIdentity.connectionOf)
    if (connect.get("urls") instanceof final List<?> list) {
      final List<String> usable =
          list.stream()
              .filter(String.class::isInstance)
              .map(String.class::cast)
              .filter(url -> !url.isBlank())
              .toList();
      if (!usable.isEmpty()) {
        return usable;
      }
    }
    return List.of(stringOrDefault(connect.get("url"), defaultUrl));
  }

  private static String stringOrDefault(final @Nullable Object value, final String fallback) {
    return value instanceof final String s ? s : fallback;
  }
}
