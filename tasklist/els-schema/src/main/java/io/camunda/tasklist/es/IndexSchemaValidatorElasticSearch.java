/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.es;

import static io.camunda.tasklist.util.CollectionUtil.map;
import static io.camunda.webapps.schema.descriptors.AbstractIndexDescriptor.formatIndexPrefix;
import static io.camunda.webapps.schema.descriptors.ComponentNames.TASK_LIST;

import io.camunda.tasklist.data.conditionals.ElasticSearchCondition;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.schema.IndexMapping.IndexMappingProperty;
import io.camunda.tasklist.schema.IndexSchemaValidator;
import io.camunda.tasklist.schema.manager.SchemaManager;
import io.camunda.tasklist.util.IndexSchemaValidatorUtil;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class IndexSchemaValidatorElasticSearch extends IndexSchemaValidatorUtil
    implements IndexSchemaValidator {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(IndexSchemaValidatorElasticSearch.class);

  @Autowired Set<IndexDescriptor> indexDescriptors;

  @Autowired TasklistProperties tasklistProperties;

  @Autowired SchemaManager schemaManager;

  @Autowired RetryElasticsearchClient retryElasticsearchClient;

  private Set<String> getAllIndexNamesForIndex(final IndexDescriptor index) {
    final String indexPattern = getTasklistIndexPattern();
    LOGGER.debug("Getting all indices for {}", indexPattern);
    final Set<String> indexNames = retryElasticsearchClient.getIndexNames(indexPattern);
    // since we have indices with similar names, we need to additionally filter index names
    // e.g. task and task-variable
    final String patternWithVersion = index.getAllVersionsIndexNameRegexPattern();
    return indexNames.stream()
        .filter(n -> n.matches(patternWithVersion))
        .collect(Collectors.toSet());
  }

  private String getTasklistIndexPattern() {
    return formatIndexPrefix(getIndexPrefix()) + TASK_LIST + "*";
  }

  @Override
  public boolean isHealthCheckEnabled() {
    return tasklistProperties.getElasticsearch().isHealthCheckEnabled();
  }

  @Override
  public boolean hasAnyTasklistIndices() {
    final Set<String> indices =
        retryElasticsearchClient.getIndexNames(
            tasklistProperties.getElasticsearch().getIndexPrefix() + "*");
    return !indices.isEmpty();
  }

  @Override
  public boolean schemaExists() {
    try {
      final Set<String> indices =
          retryElasticsearchClient.getIndexNames(
              tasklistProperties.getElasticsearch().getIndexPrefix() + "*");
      final List<String> allIndexNames =
          map(indexDescriptors, IndexDescriptor::getFullQualifiedName);

      final Set<String> aliases =
          retryElasticsearchClient.getAliasesNames(
              tasklistProperties.getElasticsearch().getIndexPrefix() + "*");
      final List<String> allAliasesNames = map(indexDescriptors, IndexDescriptor::getAlias);

      return indices.containsAll(allIndexNames) && aliases.containsAll(allAliasesNames);
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
          final Set<String> oldVersions =
              olderVersionsForIndex(indexDescriptor, versionsForIndex(indexDescriptor));
          final Set<String> newerVersions =
              newerVersionsForIndex(indexDescriptor, versionsForIndex(indexDescriptor));
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
  public boolean isValidNumberOfReplicas() {
    final List<String> allIndexNames = map(indexDescriptors, IndexDescriptor::getFullQualifiedName);
    for (final String index : allIndexNames) {
      final Map<String, String> response =
          retryElasticsearchClient.getIndexSettingsFor(
              index, RetryElasticsearchClient.NUMBERS_OF_REPLICA);
      if (!response
          .get(RetryElasticsearchClient.NUMBERS_OF_REPLICA)
          .equals(String.valueOf(tasklistProperties.getElasticsearch().getNumberOfReplicas()))) {
        return false;
      }
    }
    return true;
  }

  /**
   * Validates existing indices mappings against schema files defined in codebase.
   *
   * @return newFields map with the new field definitions per index
   * @throws TasklistRuntimeException in case some fields would need to be deleted or have different
   *     settings
   */
  @Override
  public Map<IndexDescriptor, Set<IndexMappingProperty>> validateIndexMappings()
      throws IOException {
    return validateIndexMappings(indexDescriptors);
  }

  @Override
  public Set<String> olderVersionsForIndex(final IndexDescriptor indexDescriptor) {
    final Set<String> versions = getAllIndexNamesForIndex(indexDescriptor);
    return olderVersionsForIndex(indexDescriptor, versions);
  }

  @Override
  public Set<String> newerVersionsForIndex(final IndexDescriptor indexDescriptor) {
    final Set<String> versions = getAllIndexNamesForIndex(indexDescriptor);
    return newerVersionsForIndex(indexDescriptor, versions);
  }

  private Set<String> versionsForIndex(final IndexDescriptor indexDescriptor) {
    final Set<String> allIndexNames = getAllIndexNamesForIndex(indexDescriptor);
    return allIndexNames.stream()
        .map(this::getVersionFromIndexName)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .collect(Collectors.toSet());
  }
}
