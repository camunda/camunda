/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.schema;

import static io.camunda.operate.util.CollectionUtil.map;
import static io.camunda.webapps.schema.descriptors.AbstractIndexDescriptor.formatIndexPrefix;
import static io.camunda.webapps.schema.descriptors.ComponentNames.OPERATE;

import com.google.common.collect.Maps;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.schema.IndexMapping.IndexMappingProperty;
import io.camunda.operate.schema.migration.SemanticVersion;
import io.camunda.webapps.schema.descriptors.AbstractIndexDescriptor;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class IndexSchemaValidator {

  private static final Logger LOGGER = LoggerFactory.getLogger(IndexSchemaValidator.class);

  private static final Pattern VERSION_PATTERN = Pattern.compile("(\\d+\\.\\d+\\.\\d+)_.*");

  @Autowired Set<IndexDescriptor> indexDescriptors;

  @Autowired SchemaManager schemaManager;

  private Set<String> getAllIndexNamesForIndex(final IndexDescriptor index) {
    final String indexPattern = getOperateIndexPattern();
    LOGGER.debug("Getting all indices for {}", indexPattern);
    final Set<String> indexNames = schemaManager.getIndexNames(indexPattern);
    // since we have indices with similar names, we need to additionally filter index names
    // e.g. task and task-variable
    final String patternWithVersion = index.getAllVersionsIndexNameRegexPattern();
    return indexNames.stream()
        .filter(n -> n.matches(patternWithVersion))
        .collect(Collectors.toSet());
  }

  public Set<String> newerVersionsForIndex(final IndexDescriptor indexDescriptor) {
    final SemanticVersion currentVersion =
        SemanticVersion.fromVersion(indexDescriptor.getVersion());
    final Set<String> versions = versionsForIndex(indexDescriptor);
    return versions.stream()
        .filter(version -> SemanticVersion.fromVersion(version).isNewerThan(currentVersion))
        .collect(Collectors.toSet());
  }

  public Set<String> olderVersionsForIndex(final IndexDescriptor indexDescriptor) {
    final SemanticVersion currentVersion =
        SemanticVersion.fromVersion(indexDescriptor.getVersion());
    final Set<String> versions = versionsForIndex(indexDescriptor);
    return versions.stream()
        .filter(version -> currentVersion.isNewerThan(SemanticVersion.fromVersion(version)))
        .collect(Collectors.toSet());
  }

  private Set<String> versionsForIndex(final IndexDescriptor indexDescriptor) {
    final Set<String> allIndexNames = getAllIndexNamesForIndex(indexDescriptor);
    return allIndexNames.stream()
        .map(name -> getVersionFromIndexName(indexDescriptor, name))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .collect(Collectors.toSet());
  }

  private Optional<String> getVersionFromIndexName(
      final IndexDescriptor index, final String indexName) {
    final String truncatedIndexName =
        indexName.substring(index.getIndexNameWithoutVersion().length() + 1); // +1 for dash
    final Matcher matcher = VERSION_PATTERN.matcher(truncatedIndexName);
    if (matcher.matches() && matcher.groupCount() > 0) {
      return Optional.of(matcher.group(1));
    }
    return Optional.empty();
  }

  /**
   * Validates whether there are no more than one older versions, which would mean not finished
   * migration. Or whether there are newer index versions, meaning that older Operate version is
   * run.
   */
  public void validateIndexVersions() {
    if (!hasAnyOperateIndices()) {
      return;
    }
    final Set<String> errors = new HashSet<>();
    getOperateIndexDescriptors()
        .forEach(
            indexDescriptor -> {
              final Set<String> oldVersions = olderVersionsForIndex(indexDescriptor);
              final Set<String> newerVersions = newerVersionsForIndex(indexDescriptor);
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
                        indexDescriptor.getIndexName(),
                        indexDescriptor.getVersion(),
                        newerVersions));
              }
            });
    if (!errors.isEmpty()) {
      throw new OperateRuntimeException("Error(s) in index schema: " + String.join(";", errors));
    }
  }

  public boolean hasAnyOperateIndices() {
    final Set<String> indices = schemaManager.getIndexNames(getOperateIndexPattern());
    return !indices.isEmpty();
  }

  public boolean schemaExists() {
    try {
      final Set<String> indices = schemaManager.getIndexNames(getOperateIndexPattern());
      final var operateIndexDescriptors = getOperateIndexDescriptors();
      final List<String> allIndexNames =
          map(operateIndexDescriptors, IndexDescriptor::getFullQualifiedName);

      final Set<String> aliases = schemaManager.getAliasesNames(getOperateIndexPattern());
      final List<String> allAliasesNames = map(operateIndexDescriptors, IndexDescriptor::getAlias);

      return indices.containsAll(allIndexNames) && aliases.containsAll(allAliasesNames);
    } catch (final Exception e) {
      LOGGER.error("Check for existing schema failed", e);
      return false;
    }
  }

  /**
   * Validates existing indices mappings against schema files defined in codebase.
   *
   * @return newFields map with the new field definitions per index
   * @throws OperateRuntimeException in case some fields would need to be deleted or have different
   *     settings
   */
  public Map<IndexDescriptor, Set<IndexMappingProperty>> validateIndexMappings() {
    final Map<IndexDescriptor, Set<IndexMappingProperty>> newFields = new HashMap<>();
    final Map<String, IndexMapping> indexMappings =
        schemaManager.getIndexMappings(getOperateIndexPattern());
    for (final IndexDescriptor indexDescriptor : getOperateIndexDescriptors()) {
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

  private String getOperateIndexPattern() {
    return formatIndexPrefix(schemaManager.getIndexPrefix()) + OPERATE + "*";
  }

  private IndexMappingDifference getDifference(
      final IndexDescriptor indexDescriptor, final Map<String, IndexMapping> indexMappingsGroup) {
    return getIndexMappingDifference(indexDescriptor, indexMappingsGroup);
  }

  private void validateDifferenceAndCollectNewFields(
      final IndexDescriptor indexDescriptor,
      final IndexMappingDifference difference,
      final Map<IndexDescriptor, Set<IndexMappingProperty>> newFields) {
    if (difference != null && !difference.isEqual()) {
      LOGGER.debug(
          String.format(
              "Index fields differ from expected. Index name: %s. Difference: %s.",
              indexDescriptor.getIndexName(), difference));

      if (!difference.getEntriesDiffering().isEmpty()) {
        // This call will throw an exception unless the index is dynamic, in which case
        // field differences will be ignored. In the case of a dynamic index, we still want
        // to collect any new fields, so we should continue to the next checks instead of making
        // this part of the if/else block
        validateFieldsDifferBetweenIndices(difference, indexDescriptor);
      }

      if (!difference.getEntriesOnlyOnRight().isEmpty()) {
        final String message =
            String.format(
                "Index name: %s. Field deletion is requested, will be ignored. Fields: %s",
                indexDescriptor.getIndexName(), difference.getEntriesOnlyOnRight());
        LOGGER.info(message);

      } else if (!difference.getEntriesOnlyOnLeft().isEmpty()) {
        // Collect the new fields
        newFields.put(indexDescriptor, difference.getEntriesOnlyOnLeft());
      }
    } else {
      LOGGER.debug(
          String.format(
              "Index fields are up to date. Index name: %s.", indexDescriptor.getIndexName()));
    }
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
          throw new OperateRuntimeException(
              "Ambiguous schema update. First bring runtime and date indices to one schema. Difference 1: "
                  + difference
                  + ". Difference 2: "
                  + currentDifference);
        }
      }
    }
    return difference;
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

  private boolean indexIsDynamic(final IndexMapping mapping) {
    if (mapping == null) {
      return false;
    }
    if (mapping.getDynamic() == null) {
      return true;
    }

    return Boolean.parseBoolean(mapping.getDynamic());
  }

  private void validateFieldsDifferBetweenIndices(
      final IndexMappingDifference difference, final IndexDescriptor indexDescriptor) {
    if (indexIsDynamic(difference.getLeftIndexMapping())) {
      LOGGER.debug(
          String.format(
              "Left index name: %s is dynamic, ignoring changes found: %s",
              indexDescriptor.getIndexName(), difference.getEntriesDiffering()));
    } else if (indexIsDynamic(difference.getRightIndexMapping())) {
      LOGGER.debug(
          String.format(
              "Right index name: %s is dynamic, ignoring changes found: %s",
              indexDescriptor.getIndexName(), difference.getEntriesDiffering()));
    } else {
      final String errorMsg =
          String.format(
              "Index name: %s. Not supported index changes are introduced. Data migration is required. Changes found: %s",
              indexDescriptor.getIndexName(), difference.getEntriesDiffering());
      LOGGER.error(errorMsg);
      throw new OperateRuntimeException(errorMsg);
    }
  }

  private List<IndexDescriptor> getOperateIndexDescriptors() {
    return indexDescriptors.stream()
        .filter(AbstractIndexDescriptor.class::isInstance)
        .map(AbstractIndexDescriptor.class::cast)
        .filter(a -> a.getComponentName().equals(OPERATE.toString()))
        .collect(Collectors.toList());
  }
}
