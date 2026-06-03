/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.schema;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class for cleaning up legacy Elasticsearch/OpenSearch indices that are no longer used.
 *
 * <p>These indices were created by Operate and Tasklist prior to the 8.8 architectural changes that
 * introduced the unified Camunda Exporter and harmonized index schema. They are no longer written
 * to or read from, but may still exist in clusters that have been upgraded.
 *
 * <p>Cleanup is performed asynchronously by the search engine, avoiding any blocking or performance
 * impact at startup.
 *
 * <p>When {@code performCleanup} is {@code false}, the helper runs in dry-run mode: it detects
 * legacy indices and logs them, but does not delete any data. Set {@code performCleanup=true} to
 * enable actual cleanup.
 */
public class SchemaCleanup {

  protected static final Set<String> CLEANUP_INDEXES =
      Set.of(
          "tasklist-process-8.4.0_",
          "operate-user-1.2.0_",
          "tasklist-variable-8.3.0_",
          "tasklist-import-position-8.2.0_",
          "tasklist-flownode-instance-8.3.0_",
          "operate-web-session-1.1.0_",
          "tasklist-metric-8.3.0_",
          "operate-import-position-8.3.0_",
          "operate-migration-steps-repository-1.1.0_",
          "tasklist-web-session-1.1.0_",
          "tasklist-user-1.4.0_",
          "operate-user-task-8.5.0_",
          "tasklist-process-instance-8.3.0_",
          "operate-metric-8.3.0_",
          "tasklist-migration-steps-repository-1.1.0_",
          "tasklist-list-view-8.6.0_");

  private static final String INSTRUCTIONS_1 =
      "The following indices are legacy and could be cleaned up:\n";
  private static final String INSTRUCTIONS_2 =
      "Delete these indices or set camunda.data.secondary-storage.%s.perform-cleanup=true"
          + " to perform the cleanup.\n";

  private static final Logger LOGGER = LoggerFactory.getLogger(SchemaCleanup.class);

  private final boolean performCleanup;
  private final SearchEngineClient searchEngineClient;

  public SchemaCleanup(final boolean performCleanup, final SearchEngineClient searchEngineClient) {
    this.performCleanup = performCleanup;
    this.searchEngineClient = searchEngineClient;
  }

  public Set<String> performCleanup() {
    /* Find the potential legacy indexes */

    final Set<String> legacyIndexes =
        searchEngineClient.getIndexNames(String.join(",", CLEANUP_INDEXES));

    if (legacyIndexes.isEmpty()) {
      LOGGER.debug("No legacy indexes found.");
      return legacyIndexes;
    }

    final StringBuilder logMessageBuilder = new StringBuilder();
    if (!performCleanup) {
      /* Sort the list, so that they are grouped by index prefix */
      final List<String> sortedLegacyIndexes = legacyIndexes.stream().sorted().toList();
      logMessageBuilder.append(INSTRUCTIONS_1);

      for (final String legacyIndex : sortedLegacyIndexes) {
        logMessageBuilder.append(legacyIndex);
        logMessageBuilder.append("\n");
      }

      logMessageBuilder.append(
          String.format(INSTRUCTIONS_2, searchEngineClient.getEngineName().toLowerCase()));
      LOGGER.warn(logMessageBuilder.toString());
      return legacyIndexes;
    }

    /* Do the cleanups */

    final Set<String> deletedIndexes = new HashSet<>();
    for (final String legacyIndex : legacyIndexes) {
      if (searchEngineClient.deleteIndexIfExists(legacyIndex)) {
        deletedIndexes.add(legacyIndex);
        LOGGER.info("Legacy index '{}' cleaned up.", legacyIndex);
      }
    }

    return deletedIndexes;
  }
}
