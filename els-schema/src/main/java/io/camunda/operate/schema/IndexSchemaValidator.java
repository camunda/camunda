/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.schema;

import io.camunda.operate.es.RetryElasticsearchClient;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.indices.IndexDescriptor;
import io.camunda.operate.schema.migration.SemanticVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static io.camunda.operate.util.CollectionUtil.map;

@Component
public class IndexSchemaValidator {

  private static final Logger logger = LoggerFactory.getLogger(IndexSchemaValidator.class);

  private static final Pattern VERSION_PATTERN = Pattern.compile(".*-(\\d+\\.\\d+\\.\\d+.*)_.*");

  @Autowired
  Set<IndexDescriptor> indexDescriptors;

  @Autowired
  OperateProperties operateProperties;

  @Autowired
  RetryElasticsearchClient retryElasticsearchClient;

  private Set<String> getAllIndexNamesForIndex(String index) {
    final String indexPattern = String.format("%s-%s*", getIndexPrefix(), index);
    logger.debug("Getting all indices for {}", indexPattern);
    final Set<String> indexNames = retryElasticsearchClient.getIndexNames(indexPattern);
    // since we have indices with similar names, we need to additionally filter index names
    // e.g. task and task-variable
    final String patternWithVersion = String.format("%s-%s-\\d.*", getIndexPrefix(), index);
    return indexNames.stream()
        .filter(n -> n.matches(patternWithVersion))
        .collect(Collectors.toSet());
  }

  private String getIndexPrefix() {
    return operateProperties.getElasticsearch().getIndexPrefix();
  }

  public Set<String> newerVersionsForIndex(IndexDescriptor indexDescriptor) {
    SemanticVersion currentVersion = SemanticVersion.fromVersion(indexDescriptor.getVersion());
    Set<String> versions = versionsForIndex(indexDescriptor);
    return versions.stream()
        .filter(version -> SemanticVersion.fromVersion(version).isNewerThan(currentVersion))
        .collect(Collectors.toSet());
  }

  public Set<String> olderVersionsForIndex(IndexDescriptor indexDescriptor) {
    SemanticVersion currentVersion = SemanticVersion.fromVersion(indexDescriptor.getVersion());
    Set<String> versions = versionsForIndex(indexDescriptor);
    return versions.stream()
        .filter(version -> currentVersion.isNewerThan(SemanticVersion.fromVersion(version)))
        .collect(Collectors.toSet());
  }

  private Set<String> versionsForIndex(IndexDescriptor indexDescriptor) {
    Set<String> allIndexNames = getAllIndexNamesForIndex(indexDescriptor.getIndexName());
    return allIndexNames.stream()
        .map(this::getVersionFromIndexName)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .collect(Collectors.toSet());
  }

  private Optional<String> getVersionFromIndexName(String indexName) {
    Matcher matcher = VERSION_PATTERN.matcher(indexName);
    if (matcher.matches() && matcher.groupCount() > 0) {
      return Optional.of(matcher.group(1));
    }
    return Optional.empty();
  }

  public void validate() {
    if (!hasAnyOperateIndices())
      return;
    Set<String> errors = new HashSet<>();
    indexDescriptors.forEach(indexDescriptor -> {
      Set<String> oldVersions = olderVersionsForIndex(indexDescriptor);
      Set<String> newerVersions = newerVersionsForIndex(indexDescriptor);
      if (oldVersions.size() > 1) {
        errors
            .add(String.format("More than one older version for %s (%s) found: %s", indexDescriptor.getIndexName(), indexDescriptor.getVersion(), oldVersions));
      }
      if (!newerVersions.isEmpty()) {
        errors
            .add(String.format("Newer version(s) for %s (%s) already exists: %s", indexDescriptor.getIndexName(), indexDescriptor.getVersion(), newerVersions));
      }
    });
    if (!errors.isEmpty()) {
      throw new OperateRuntimeException("Error(s) in index schema: " + String.join(";", errors));
    }
  }

  public boolean hasAnyOperateIndices() {
    final Set<String> indices = retryElasticsearchClient
        .getIndexNames(operateProperties.getElasticsearch().getIndexPrefix() + "*");
    return !indices.isEmpty();
  }

  public boolean schemaExists() {
    try {
      final Set<String> indices = retryElasticsearchClient
          .getIndexNames(operateProperties.getElasticsearch().getIndexPrefix() + "*");
      List<String> allIndexNames = map(indexDescriptors, IndexDescriptor::getFullQualifiedName);
      return indices.containsAll(allIndexNames);
    } catch (Exception e) {
      logger.error("Check for existing schema failed", e);
      return false;
    }
  }
}
