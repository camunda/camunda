/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.util;

import static io.camunda.zeebe.client.impl.Loggers.LOGGER;

import com.google.common.collect.Maps;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.schema.IndexMapping;
import io.camunda.tasklist.schema.IndexMapping.IndexMappingProperty;
import io.camunda.tasklist.schema.IndexMappingDifference;
import io.camunda.tasklist.schema.SemanticVersion;
import io.camunda.tasklist.schema.indices.IndexDescriptor;
import io.camunda.tasklist.schema.manager.SchemaManager;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public abstract class IndexSchemaValidatorUtil {

  private static final Pattern VERSION_PATTERN = Pattern.compile(".*-(\\d+\\.\\d+\\.\\d+.*)_.*");

  public static String getIndexPrefix(final TasklistProperties tasklistProperties) {
    return TasklistProperties.OPEN_SEARCH.equals(tasklistProperties.getDatabaseType())
        ? tasklistProperties.getOpenSearch().getIndexPrefix()
        : tasklistProperties.getElasticsearch().getIndexPrefix();
  }

  public static Set<String> newerVersionsForIndex(
      final IndexDescriptor indexDescriptor, final Set<String> versions) {
    final SemanticVersion currentVersion =
        SemanticVersion.fromVersion(indexDescriptor.getVersion());
    return versions.stream()
        .filter(version -> SemanticVersion.fromVersion(version).isNewerThan(currentVersion))
        .collect(Collectors.toSet());
  }

  public static Set<String> olderVersionsForIndex(
      final IndexDescriptor indexDescriptor, final Set<String> versions) {
    final SemanticVersion currentVersion =
        SemanticVersion.fromVersion(indexDescriptor.getVersion());
    return versions.stream()
        .filter(version -> currentVersion.isNewerThan(SemanticVersion.fromVersion(version)))
        .collect(Collectors.toSet());
  }

  public Optional<String> getVersionFromIndexName(final String indexName) {
    final Matcher matcher = VERSION_PATTERN.matcher(indexName);
    if (matcher.matches() && matcher.groupCount() > 0) {
      return Optional.of(matcher.group(1));
    }
    return Optional.empty();
  }

  public static Map<String, IndexMapping> filterIndexMappings(
      final Map<String, IndexMapping> indexMappings, final IndexDescriptor indexDescriptor) {
    return Maps.filterEntries(
        indexMappings,
        e -> e.getKey().matches(indexDescriptor.getAllVersionsIndexNameRegexPattern()));
  }

  public static void validateDifferenceAndCollectNewFields(
      final IndexDescriptor indexDescriptor,
      final IndexMappingDifference difference,
      final Map<IndexDescriptor, Set<IndexMappingProperty>> newFields) {

    if (difference == null || difference.isEqual()) {
      LOGGER.debug(
          String.format(
              "Index fields are up to date. Index name: %s.", indexDescriptor.getIndexName()));
      return;
    }

    LOGGER.debug(
        String.format(
            "Index fields differ from expected. Index name: %s. Difference: %s.",
            indexDescriptor.getIndexName(), difference));

    if (!difference.getEntriesDiffering().isEmpty()) {
      final String errorMsg =
          String.format(
              "Index name: %s. Not supported index changes are introduced. Data migration is required. Changes found: %s",
              indexDescriptor.getIndexName(), difference.getEntriesDiffering());
      LOGGER.error(errorMsg);
      throw new TasklistRuntimeException(errorMsg);
    } else if (!difference.getEntriesOnlyOnRight().isEmpty()) {
      final String message =
          String.format(
              "Index name: %s. Field deletion is requested, will be ignored. Fields: %s",
              indexDescriptor.getIndexName(), difference.getEntriesOnlyOnRight());
      LOGGER.info(message);
    } else if (!difference.getEntriesOnlyOnLeft().isEmpty()) {
      newFields.put(indexDescriptor, difference.getEntriesOnlyOnLeft());
    }
  }

  public Map<IndexDescriptor, Set<IndexMappingProperty>> validateIndexMappings(
      final SchemaManager schemaManager, final Set<IndexDescriptor> indexDescriptors)
      throws IOException {
    final Map<IndexDescriptor, Set<IndexMappingProperty>> newFields = new HashMap<>();
    final Map<String, IndexMapping> indexMappings =
        schemaManager.getIndexMappings(schemaManager.getIndexPrefix() + "*");
    for (final IndexDescriptor indexDescriptor : indexDescriptors) {
      final Map<String, IndexMapping> indexMappingsGroup =
          filterIndexMappings(indexMappings, indexDescriptor);
      // we don't check indices that were not yet created
      if (!indexMappingsGroup.isEmpty()) {
        final IndexMappingDifference difference =
            getDifference(indexDescriptor, indexMappingsGroup, schemaManager);
        validateDifferenceAndCollectNewFields(indexDescriptor, difference, newFields);
      }
    }
    return newFields;
  }

  private IndexMappingDifference getDifference(
      final IndexDescriptor indexDescriptor,
      final Map<String, IndexMapping> indexMappingsGroup,
      final SchemaManager schemaManager) {
    return getIndexMappingDifference(indexDescriptor, indexMappingsGroup, schemaManager);
  }

  private IndexMappingDifference getIndexMappingDifference(
      final IndexDescriptor indexDescriptor,
      final Map<String, IndexMapping> indexMappingsGroup,
      final SchemaManager schemaManager) {
    final IndexMapping indexMappingMustBe = schemaManager.getExpectedIndexFields(indexDescriptor);

    IndexMappingDifference difference = null;
    // compare every index in group
    for (final Map.Entry<String, IndexMapping> singleIndexMapping : indexMappingsGroup.entrySet()) {
      final IndexMappingDifference currentDifference =
          new IndexMappingDifference.IndexMappingDifferenceBuilder()
              .setLeft(indexMappingMustBe)
              .setRight(singleIndexMapping.getValue())
              .build();
      if (!currentDifference.isEqual()) {
        if (difference == null) {
          difference = currentDifference;
          // If there is a difference between the template and the existing runtime/data indices,
          // all those indices should have the same difference. Compare based only on the
          // differences (exclude the IndexMapping fields in the comparison)
        } else if (!difference.checkEqualityForDifferences(currentDifference)) {
          throw new TasklistRuntimeException(
              "Ambiguous schema update. First bring runtime and date indices to one schema. Difference 1: "
                  + difference
                  + ". Difference 2: "
                  + currentDifference);
        }
      }
    }
    return difference;
  }
}
