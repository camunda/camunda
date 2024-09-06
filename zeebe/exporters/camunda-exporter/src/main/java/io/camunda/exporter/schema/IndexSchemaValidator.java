/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.schema;

import io.camunda.exporter.exceptions.IndexSchemaValidationException;
import io.camunda.exporter.schema.descriptors.IndexDescriptor;
import io.camunda.exporter.schema.descriptors.IndexTemplateDescriptor;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IndexSchemaValidator {
  private static final Logger LOGGER = LoggerFactory.getLogger(IndexSchemaValidator.class);

  private final SchemaManager schemaManager;

  public IndexSchemaValidator(final SchemaManager schemaManager) {
    this.schemaManager = schemaManager;
  }

  //  /**
  //   * Validates existing indices mappings against index/index template mappings defined.
  //   *
  //   * @return newFields map with the new field definitions per index
  //   * @throws IndexSchemaValidationException if changes are ambiguous in that there are two
  // different
  //   *     schemas which match a single descriptor and their differences to the descriptor is
  //   *     different. Or an unsupported change is introduced in the descriptor. settings
  //   */

  /**
   * Validates existing indices mappings against index/index template mappings defined.
   *
   * @param mappings is a map of all the mappings to compare.
   * @param indexDescriptors is the set of all index descriptors representing desired schema states.
   * @return new mapping properties to add to schemas so they align with the descriptors.
   */
  public Map<IndexDescriptor, Set<IndexMappingProperty>> validateIndexMappings(
      final Map<String, IndexMapping> mappings, final Set<IndexDescriptor> indexDescriptors) {
    final Map<IndexDescriptor, Set<IndexMappingProperty>> newFields = new HashMap<>();
    for (final IndexDescriptor indexDescriptor : indexDescriptors) {
      final Map<String, IndexMapping> indexMappingsGroup =
          filterIndexMappings(mappings, indexDescriptor);
      //       we don't check indices that were not yet created
      if (!indexMappingsGroup.isEmpty()) {
        final IndexMappingDifference difference =
            getIndexMappingDifference(indexDescriptor, indexMappingsGroup);
        validateDifferenceAndCollectNewFields(indexDescriptor, difference, newFields);
      }
    }
    return newFields;
  }

  private void validateDifferenceAndCollectNewFields(
      final IndexDescriptor indexDescriptor,
      final IndexMappingDifference difference,
      final Map<IndexDescriptor, Set<IndexMappingProperty>> newFields) {
    if (difference != null && !difference.isEqual()) {
      LOGGER.debug(
          "Index fields differ from expected. Index name: {}. Difference: {}.",
          indexDescriptor.getIndexName(),
          difference);

      if (!difference.getEntriesDiffering().isEmpty()) {
        // This call will throw an exception unless the index is dynamic, in which case
        // field differences will be ignored. In the case of a dynamic index, we still want
        // to collect any new fields, so we should continue to the next checks instead of making
        // this part of the if/else block
        failIfIndexNotDynamic(difference, indexDescriptor);
      }

      if (!difference.getEntriesOnlyOnRight().isEmpty()) {
        LOGGER.info(
            "Index name: {}. Field deletion is requested, will be ignored. Fields: {}",
            indexDescriptor.getIndexName(),
            difference.getEntriesOnlyOnRight());

      } else if (!difference.getEntriesOnlyOnLeft().isEmpty()) {
        // Collect the new fields
        newFields.put(indexDescriptor, difference.getEntriesOnlyOnLeft());
      }
    } else {
      LOGGER.debug("Index fields are up to date. Index name: {}.", indexDescriptor.getIndexName());
    }
  }

  private IndexMappingDifference getIndexMappingDifference(
      final IndexDescriptor indexDescriptor, final Map<String, IndexMapping> indexMappingsGroup) {
    final IndexMapping indexMappingMustBe = schemaManager.readIndex(indexDescriptor);

    final var differences =
        indexMappingsGroup.values().stream()
            .map(
                mapping ->
                    new IndexMappingDifference.IndexMappingDifferenceBuilder()
                        .setLeft(indexMappingMustBe)
                        .setRight(mapping)
                        .build())
            .filter(difference -> !difference.isEqual())
            // Ensure all difference are the same
            .distinct() // Have to implement IndexMappingDifference.equals and hashcode
            .toList();

    if (differences.isEmpty()) {
      // No difference
      return null;
    }

    if (differences.size() > 1) {
      throw new IndexSchemaValidationException(
          "Ambiguous schema update. First bring runtime and date indices to one schema. Differences: "
              + differences);
    }

    return differences.getFirst();
  }

  /**
   * Given a {@link Map} of all index mappings, only return those which match the <code>
   * indexDescriptor</code>.
   *
   * <p>Mappings can be retrieved using {@link SearchEngineClient#getMappings}
   *
   * @param indexMappings represents mappings that will be checked
   * @param indexDescriptor represents the desired state of indices/index templates
   * @return a filtered map of all indexMappings matching the descriptor
   */
  private Map<String, IndexMapping> filterIndexMappings(
      final Map<String, IndexMapping> indexMappings, final IndexDescriptor indexDescriptor) {
    if (indexDescriptor instanceof IndexTemplateDescriptor) {
      return indexMappings.entrySet().stream()
          .filter(
              e -> e.getKey().equals(((IndexTemplateDescriptor) indexDescriptor).getTemplateName()))
          .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
    } else {
      return indexMappings.entrySet().stream()
          .filter(e -> e.getKey().matches(indexDescriptor.getAllVersionsIndexNameRegexPattern()))
          .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
    }
  }

  private boolean indexIsDynamic(final IndexMapping mapping) {
    if (mapping == null) {
      return false;
    }
    if (mapping.dynamic() == null) {
      return true;
    }

    return Boolean.parseBoolean(mapping.dynamic());
  }

  private void failIfIndexNotDynamic(
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
      throw new IndexSchemaValidationException(errorMsg);
    }
  }
}
