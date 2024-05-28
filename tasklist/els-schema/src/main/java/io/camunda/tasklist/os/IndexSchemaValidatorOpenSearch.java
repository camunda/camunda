/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.os;

import static io.camunda.tasklist.util.CollectionUtil.map;

import com.google.common.collect.Maps;
import io.camunda.tasklist.data.conditionals.OpenSearchCondition;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.schema.IndexMapping;
import io.camunda.tasklist.schema.IndexMapping.IndexMappingProperty;
import io.camunda.tasklist.schema.IndexMappingDifference;
import io.camunda.tasklist.schema.IndexSchemaValidator;
import io.camunda.tasklist.schema.SemanticVersion;
import io.camunda.tasklist.schema.indices.IndexDescriptor;
import io.camunda.tasklist.schema.manager.SchemaManager;
import io.camunda.tasklist.util.IndexSchemaValidatorUtil;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.opensearch.client.opensearch.indices.IndexSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class IndexSchemaValidatorOpenSearch extends IndexSchemaValidatorUtil implements IndexSchemaValidator {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(IndexSchemaValidatorOpenSearch.class);

  private static final Pattern VERSION_PATTERN = Pattern.compile(".*-(\\d+\\.\\d+\\.\\d+.*)_.*");

  @Autowired Set<IndexDescriptor> indexDescriptors;

  @Autowired TasklistProperties tasklistProperties;

  @Autowired RetryOpenSearchClient retryOpenSearchClient;

  @Autowired SchemaManager schemaManager;

  private Set<String> getAllIndexNamesForIndex(final String index) {
    final String indexPattern = String.format("%s-%s*", getIndexPrefix(tasklistProperties), index);
    LOGGER.debug("Getting all indices for {}", indexPattern);
    final Set<String> indexNames = retryOpenSearchClient.getIndexNames(indexPattern);
    // since we have indices with similar names, we need to additionally filter index names
    // e.g. task and task-variable
    final String patternWithVersion = String.format("%s-%s-\\d.*", getIndexPrefix(tasklistProperties), index);
    return indexNames.stream()
        .filter(n -> n.matches(patternWithVersion))
        .collect(Collectors.toSet());
  }

  @Override
  public void validate() {
    if (!hasAnyTasklistIndices()) {
      return;
    }
    final Set<String> errors = new HashSet<>();
    indexDescriptors.forEach(
        indexDescriptor -> {
          final Set<String> oldVersions = olderVersionsForIndex(indexDescriptor, versionsForIndex(indexDescriptor));
          final Set<String> newerVersions = newerVersionsForIndex(indexDescriptor, versionsForIndex(indexDescriptor));
          if (oldVersions.size() > 1) {
            errors.add(
                String.format(
                    "More than one older version for %s (%s) found: %s",
                    indexDescriptor.getIndexName(), indexDescriptor.getVersion(), oldVersions));
          }
          if (!newerVersions.isEmpty()) {
            errors.add(
                String.format(
                    "Newer version(s) for %s (%s) already exists: %s",
                    indexDescriptor.getIndexName(), indexDescriptor.getVersion(), newerVersions));
          }
        });
    if (!errors.isEmpty()) {
      throw new TasklistRuntimeException("Error(s) in index schema: " + String.join(";", errors));
    }
  }

  @Override
  public boolean hasAnyTasklistIndices() {
    final Set<String> indices =
        retryOpenSearchClient.getIndexNames(
            tasklistProperties.getOpenSearch().getIndexPrefix() + "*");
    return !indices.isEmpty();
  }

  @Override
  public boolean schemaExists() {
    try {
      final Set<String> indices =
          retryOpenSearchClient.getIndexNames(
              tasklistProperties.getOpenSearch().getIndexPrefix() + "*");
      final List<String> allIndexNames =
          map(indexDescriptors, IndexDescriptor::getFullQualifiedName);
      return indices.containsAll(allIndexNames) && validateNumberOfReplicas(allIndexNames);
    } catch (final Exception e) {
      LOGGER.error("Check for existing schema failed", e);
      return false;
    }
  }

  @Override
  public void validateIndexVersions() {
    if (!hasAnyTasklistIndices()) {
      return;
    }
    final Set<String> errors = new HashSet<>();
    indexDescriptors.forEach(
        indexDescriptor -> {
          final Set<String> oldVersions = olderVersionsForIndex(indexDescriptor, versionsForIndex(indexDescriptor));
          final Set<String> newerVersions = newerVersionsForIndex(indexDescriptor, versionsForIndex(indexDescriptor));
          if (oldVersions.size() > 1) {
            errors.add(
                String.format(
                    "More than one older version for %s (%s) found: %s",
                    indexDescriptor.getIndexName(), indexDescriptor.getVersion(), oldVersions));
          }
          if (!newerVersions.isEmpty()) {
            errors.add(
                String.format(
                    "Newer version(s) for %s (%s) already exists: %s",
                    indexDescriptor.getIndexName(), indexDescriptor.getVersion(), newerVersions));
          }
        });
    if (!errors.isEmpty()) {
      throw new TasklistRuntimeException("Error(s) in index schema: " + String.join(";", errors));
    }
  }

  @Override
  public Map<IndexDescriptor, Set<IndexMappingProperty>> validateIndexMappings()
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
            getDifference(indexDescriptor, indexMappingsGroup);
        validateDifferenceAndCollectNewFields(indexDescriptor, difference, newFields);
      }
    }
    return newFields;
  }

  private Set<String> versionsForIndex(final IndexDescriptor indexDescriptor) {
    final Set<String> allIndexNames = getAllIndexNamesForIndex(indexDescriptor.getIndexName());
    return allIndexNames.stream()
        .map(this::getVersionFromIndexName)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .collect(Collectors.toSet());
  }

  private Optional<String> getVersionFromIndexName(final String indexName) {
    final Matcher matcher = VERSION_PATTERN.matcher(indexName);
    if (matcher.matches() && matcher.groupCount() > 0) {
      return Optional.of(matcher.group(1));
    }
    return Optional.empty();
  }

  public boolean validateNumberOfReplicas(final List<String> indexes) {
    for (final String index : indexes) {
      final IndexSettings response =
          retryOpenSearchClient.getIndexSettingsFor(
              index, RetryOpenSearchClient.NUMBERS_OF_REPLICA);
      if (!response
          .numberOfReplicas()
          .equals(String.valueOf(tasklistProperties.getOpenSearch().getNumberOfReplicas()))) {
        return false;
      }
    }
    return true;
  }

  /**
   * Leave only runtime and dated indices that correspond to the given IndexDescriptor.
   *
   * @param indexMappings
   * @param indexDescriptor
   * @return
   */
  private Map<String, IndexMapping> filterIndexMappings(
      final Map<String, IndexMapping> indexMappings, final IndexDescriptor indexDescriptor) {
    return Maps.filterEntries(
        indexMappings,
        e -> e.getKey().matches(indexDescriptor.getAllVersionsIndexNameRegexPattern()));
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

  private void validateDifferenceAndCollectNewFields(
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
}
