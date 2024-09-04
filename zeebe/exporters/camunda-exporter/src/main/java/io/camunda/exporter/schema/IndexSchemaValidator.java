/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.schema;

import com.google.common.collect.Maps;
import io.camunda.exporter.exceptions.IndexSchemaValidationException;
import io.camunda.exporter.schema.descriptors.IndexDescriptor;
import io.camunda.exporter.schema.descriptors.IndexTemplateDescriptor;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IndexSchemaValidator {
  private static final Logger LOGGER = LoggerFactory.getLogger(IndexSchemaValidator.class);

  SchemaManager schemaManager;

  public IndexSchemaValidator(final SchemaManager schemaManager) {
    this.schemaManager = schemaManager;
  }

  /**
   * Validates existing indices mappings against schema files defined in codebase.
   *
   * @return newFields map with the new field definitions per index
   * @throws RuntimeException in case some fields would need to be deleted or have different
   *     settings
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
    final IndexMapping indexMappingMustBe = schemaManager.readIndex(indexDescriptor);

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
          // If there is a difference between the template and the existing runtime/data
          //     indices,
          // all those indices should have the same difference. Compare based only on the
          // differences (exclude the IndexMapping fields in the comparison)
        } else if (!difference.checkEqualityForDifferences(currentDifference)) {
          throw new IndexSchemaValidationException(
              "Ambiguous schema update. First bring runtime and date indices to one schema. Difference 1: "
                  + difference
                  + ". Difference 2: "
                  + currentDifference);
        }
      }
    }
    return difference;
  }

  //
  /**
   * Leave only runtime and dated indices that correspond to the given IndexDescriptor.
   *
   * @param indexMappings
   * @param indexDescriptor
   * @return
   */
  private Map<String, IndexMapping> filterIndexMappings(
      final Map<String, IndexMapping> indexMappings, final IndexDescriptor indexDescriptor) {
    if (indexDescriptor instanceof IndexTemplateDescriptor) {
      return Maps.filterEntries(
          indexMappings,
          e -> e.getKey().equals(((IndexTemplateDescriptor) indexDescriptor).getTemplateName()));
    } else {
      return Maps.filterEntries(
          indexMappings,
          e -> e.getKey().matches(indexDescriptor.getAllVersionsIndexNameRegexPattern()));
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
      throw new IndexSchemaValidationException(errorMsg);
    }
  }
}
