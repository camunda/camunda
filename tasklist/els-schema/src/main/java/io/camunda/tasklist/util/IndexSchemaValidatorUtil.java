/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.util;

import static io.camunda.tasklist.schema.indices.AbstractIndexDescriptor.formatTasklistIndexPattern;

import com.google.common.collect.Maps;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.schema.IndexMapping;
import io.camunda.tasklist.schema.IndexMapping.IndexMappingProperty;
import io.camunda.tasklist.schema.IndexMappingDifference;
import io.camunda.tasklist.schema.IndexMappingDifference.PropertyDifference;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

// TO-DO: This class will replace after a refactor of retryElasticsearchClient  and
// retryOpenSearchClient
// For while is placed on Util, but after the refactor on Spring Cleaning will be moved to a
// validator package
// Refactor Issue: https://github.com/camunda/team-hto/issues/626
public class IndexSchemaValidatorUtil {

  private static final Logger LOGGER = LoggerFactory.getLogger(IndexSchemaValidatorUtil.class);
  private static final Pattern VERSION_PATTERN = Pattern.compile(".*-(\\d+\\.\\d+\\.\\d+.*)_.*");
  private static final String STRICT_DYNAMIC_POLICY = "strict";
  @Autowired TasklistProperties tasklistProperties;
  @Autowired SchemaManager schemaManager;

  public String getIndexPrefix() {
    return TasklistProperties.OPEN_SEARCH.equals(tasklistProperties.getDatabase())
        ? tasklistProperties.getOpenSearch().getIndexPrefix()
        : tasklistProperties.getElasticsearch().getIndexPrefix();
  }

  public Set<String> newerVersionsForIndex(
      final IndexDescriptor indexDescriptor, final Set<String> versions) {
    final SemanticVersion currentVersion =
        SemanticVersion.fromVersion(indexDescriptor.getVersion());
    return versions.stream()
        .filter(version -> SemanticVersion.fromVersion(version).isNewerThan(currentVersion))
        .collect(Collectors.toSet());
  }

  public Set<String> olderVersionsForIndex(
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

  public Map<String, IndexMapping> filterIndexMappings(
      final Map<String, IndexMapping> indexMappings, final IndexDescriptor indexDescriptor) {
    return Maps.filterEntries(
        indexMappings,
        e ->
            STRICT_DYNAMIC_POLICY.equals(e.getValue().getDynamic())
                && // filter out dynamic mappings - not supported by schema migration
                e.getKey().matches(indexDescriptor.getAllVersionsIndexNameRegexPattern()));
  }

  public void validateDifferenceAndCollectNewFields(
      final IndexDescriptor indexDescriptor,
      final IndexMappingDifference difference,
      final Map<IndexDescriptor, Set<IndexMappingProperty>> newFields) {

    if (difference == null || difference.isEqual()) {
      LOGGER.debug(
          String.format(
              "Index fields are up to date. Index name: %s.", indexDescriptor.getIndexName()));
      return;
    }

    // Validate if the difference is dynamic
    if (difference.getEntriesDiffering() != null) {
      for (final PropertyDifference propertyDifference : difference.getEntriesDiffering()) {
        final Object typeDefinition = propertyDifference.getLeftValue().getTypeDefinition();
        if (propertyDifference.getLeftValue().getTypeDefinition() instanceof Map) {
          final Map<String, Object> typeDefMap = (Map<String, Object>) typeDefinition;
          final Object dynamicValue = typeDefMap.getOrDefault("dynamic", false);
          if (dynamicValue.equals(true)) {
            LOGGER.debug(
                String.format(
                    "Difference is on dynamic field - continue initialization: %s.",
                    indexDescriptor.getIndexName()));
            return;
          }
        }
      }
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
      final Set<IndexDescriptor> indexDescriptors) throws IOException {
    final Map<IndexDescriptor, Set<IndexMappingProperty>> newFields = new HashMap<>();
    final Map<String, IndexMapping> indexMappings =
        schemaManager.getIndexMappings(formatTasklistIndexPattern(schemaManager.getIndexPrefix()));
    for (final IndexDescriptor indexDescriptor : indexDescriptors) {
      final Map<String, IndexMapping> indexMappingsGroup =
          filterIndexMappings(indexMappings, indexDescriptor);
      // we don't check indices that were not yet created
      if (!indexMappingsGroup.isEmpty()) {
        final IndexMappingDifference difference =
            getDifference(indexDescriptor, indexMappingsGroup);
        validateDifferenceAndCollectNewFields(indexDescriptor, difference, newFields);
      }
    }
    return newFields;
  }

  private IndexMappingDifference getDifference(
      final IndexDescriptor indexDescriptor, final Map<String, IndexMapping> indexMappingsGroup) {
    return getIndexMappingDifference(indexDescriptor, indexMappingsGroup);
  }

  private IndexMappingDifference getIndexMappingDifference(
      final IndexDescriptor indexDescriptor, final Map<String, IndexMapping> indexMappingsGroup) {
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
