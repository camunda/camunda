/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.schema;

import io.camunda.webapps.schema.descriptors.index.MetadataIndex;
import io.camunda.zeebe.util.SemanticVersion;
import io.camunda.zeebe.util.VisibleForTesting;
import io.camunda.zeebe.util.migration.CurrentSchemaVersion;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tracks the Elasticsearch/OpenSearch schema version of a single physical tenant (one search engine
 * connection + index prefix) via the {@code metadata} index's {@code schema-version} document, for
 * the upgrade-readiness endpoint (camunda/product-hub#3067).
 *
 * <p>This is a read-only counterpart to {@link SchemaMetadataStore}, which already owns
 * reading/writing that document as part of {@link SchemaManager}'s own startup/upgrade flow. Kept
 * as a separate, narrowly-scoped class so the upgrade-readiness check never throws and never
 * writes, regardless of what {@link SchemaManager} itself is doing concurrently.
 */
public class ElasticsearchSchemaVersionStore {

  private static final Logger LOG = LoggerFactory.getLogger(ElasticsearchSchemaVersionStore.class);

  private final SchemaMetadataStore schemaMetadataStore;
  private final String prefix;

  /**
   * The current application version. Used to validate the upgrade path from the stored schema
   * version.
   */
  private final String applicationVersion;

  public ElasticsearchSchemaVersionStore(
      final SearchEngineClient searchEngineClient,
      final String prefix,
      final boolean isElasticsearch,
      final String applicationVersion) {
    this.prefix = prefix;
    this.applicationVersion = applicationVersion;
    schemaMetadataStore =
        new SchemaMetadataStore(
            searchEngineClient, new MetadataIndex(prefix, isElasticsearch), LOG);
  }

  public CurrentSchemaVersion getCurrentSchemaVersion() {
    if (applicationVersion == null) {
      return CurrentSchemaVersion.readFailure(
          prefix, new IllegalStateException("applicationVersion is not configured."));
    }

    try {
      final var schemaVersion = schemaMetadataStore.getSchemaVersion();
      if (schemaVersion == null) {
        return CurrentSchemaVersion.freshDatabase(prefix);
      }

      final var comparableSchemaVersion = toStableVersion(schemaVersion).orElse(schemaVersion);

      final var stableAppVersion = toStableVersion(applicationVersion);
      return stableAppVersion
          .map(s -> CurrentSchemaVersion.available(prefix, comparableSchemaVersion, s))
          .orElseThrow(
              () ->
                  new IllegalStateException(
                      "[Elasticsearch Schema] cannot parse application version '"
                          + applicationVersion
                          + "' as a semantic version"));
    } catch (final Exception e) {
      LOG.warn(
          "[Elasticsearch Schema] Failed to determine current schema version for prefix '{}' "
              + "during upgrade-readiness check.",
          prefix,
          e);
      return CurrentSchemaVersion.readFailure(prefix, e);
    }
  }

  /**
   * Normalizes {@code version} to a stable {@code major.minor.patch} string by stripping any
   * pre-release or build-metadata suffix (e.g. {@code 8.11.0-SNAPSHOT} → {@code 8.11.0}).
   *
   * @return the stable version string, or {@link Optional#empty()} if {@code version} cannot be
   *     parsed as a semantic version (e.g. {@code "development"})
   */
  @VisibleForTesting
  static Optional<String> toStableVersion(final String version) {
    return SemanticVersion.parse(version)
        .map(sv -> sv.major() + "." + sv.minor() + "." + sv.patch());
  }
}
