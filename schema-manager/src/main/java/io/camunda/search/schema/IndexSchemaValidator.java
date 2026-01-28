/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.schema;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Maps;
import io.camunda.search.schema.exceptions.IndexSchemaValidationException;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link IndexSchemaValidator} validates existing indices mappings against index/index template
 * mappings defined.
 *
 * <p>Mappings are valid if
 *
 * <ul>
 *   <li>The existing indices corresponding to an {@link IndexDescriptor} or {@link
 *       IndexTemplateDescriptor} has the same mappings as provided by the descriptor
 *   <li>The mapping provided by the descriptor has new fields compared to the existing indices
 *       corresponding to an {@link IndexDescriptor} or {@link IndexTemplateDescriptor}.
 *   <li>The mapping provided by the descriptor has removed some fields compared to the existing
 *       indices corresponding to an {@link IndexDescriptor} or {@link IndexTemplateDescriptor}.
 * </ul>
 *
 * <p>Mappings are invalid if
 *
 * <ul>
 *   <li/>The mapping provided by the descriptor has same fields with different types compared to
 *       the existing indices corresponding to an {@link IndexDescriptor} or {@link
 *       IndexTemplateDescriptor}. This indicates that the existing indices cannot be updated to new
 *       mappings. If the index is set to allow dynamic mapping, then this case is ignored and the
 *       mapping will be considered as valid.
 *   <li/>If multiple indices corresponding to the {@link IndexDescriptor} or {@link
 *       IndexTemplateDescriptor} has different mappings and the differences are not the same. In
 *       this case, it is not clear how to update multiple indices for the same descriptor to the
 *       provided mapping.
 * </ul>
 */
public class IndexSchemaValidator {
  private static final Logger LOGGER = LoggerFactory.getLogger(IndexSchemaValidator.class);

  private final ObjectMapper objectMapper;

  public IndexSchemaValidator(final ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /**
   * Validates existing indices mappings against index/index template mappings defined.
   *
   * @param mappings is a map of all the mappings to compare.
   * @param indexDescriptors is the set of all index descriptors representing desired schema states.
   * @return new mapping properties to add to schemas, so they align with the descriptors.
   * @throws IndexSchemaValidationException if the existing indices cannot be updated with the given
   *     mappings.
   */
  public Map<IndexDescriptor, Collection<IndexMappingProperty>> validateIndexMappings(
      final Map<String, IndexMapping> mappings, final Collection<IndexDescriptor> indexDescriptors)
      throws IndexSchemaValidationException {
    final Map<IndexDescriptor, Collection<IndexMappingProperty>> newFields = new HashMap<>();
    for (final IndexDescriptor indexDescriptor : indexDescriptors) {
      final Map<String, IndexMapping> indexMappingsGroup =
          filterIndexMappings(mappings, indexDescriptor);
      // we don't check indices that were not yet created
      if (!indexMappingsGroup.isEmpty()) {
        final IndexMappingDifference difference =
            getIndexMappingDifference(indexDescriptor, indexMappingsGroup);
        validateDifferenceAndCollectNewFields(
            indexDescriptor, indexMappingsGroup.keySet(), difference, newFields);
      }
    }
    return newFields;
  }

  private void validateDifferenceAndCollectNewFields(
      final IndexDescriptor indexDescriptor,
      final Set<String> indexNames,
      final IndexMappingDifference difference,
      final Map<IndexDescriptor, Collection<IndexMappingProperty>> newFields) {
    if (difference != null && !difference.equal()) {
      LOGGER.debug(
          "Index fields differ from expected. Index names: {}. Difference: {}.",
          indexNames,
          difference);

      if (!difference.entriesDiffering().isEmpty()) {
        final String errorMsg =
            String.format(
                "Index name: %s. Unsupported index changes have been introduced. Data migration is required. Changes found: %s",
                indexDescriptor.getFullQualifiedName(), difference.entriesDiffering());
        LOGGER.error(errorMsg);
        throw new IndexSchemaValidationException(errorMsg);
      }

      if (!difference.entriesOnlyOnRight().isEmpty()) {
        LOGGER.info(
            "Index names '{}': Field deletion is requested, will be ignored. Fields: {}",
            indexNames,
            difference.entriesOnlyOnRight());

      } else if (!difference.entriesOnlyOnLeft().isEmpty()) {
        // Collect the new fields
        newFields.put(indexDescriptor, difference.entriesOnlyOnLeft());
      } else {
        LOGGER.debug("Index fields are up to date for Index '{}'.", indexDescriptor.getIndexName());
      }
    } else {
      LOGGER.debug("Index fields are up to date for Index '{}'.", indexDescriptor.getIndexName());
    }
  }

  private IndexMappingDifference getIndexMappingDifference(
      final IndexDescriptor indexDescriptor, final Map<String, IndexMapping> indexMappingsGroup) {
    final IndexMapping indexMappingMustBe = IndexMapping.from(indexDescriptor, objectMapper);

    final var differences =
        indexMappingsGroup.values().stream()
            .map(mapping -> IndexMappingDifference.of(indexMappingMustBe, mapping))
            .filter(difference -> !difference.equal())
            .map(this::filterOutDynamicProperties)
            .distinct()
            .toList();

    if (differences.isEmpty()) {
      return null;
    }

    if (differences.size() > 1) {
      LOGGER.debug(
          "Ambiguous schema update. Index names: {}. Difference: {}.",
          indexMappingsGroup.keySet(),
          differences);
      throw new IndexSchemaValidationException(
          String.format(
              "Ambiguous schema update. Multiple indices for mapping '%s' has different fields. Differences: '%s'",
              indexDescriptor.getIndexName(), differences));
    }

    return differences.getFirst();
  }

  /** Filters out differences that are only related to dynamic properties. */
  private IndexMappingDifference filterOutDynamicProperties(
      final IndexMappingDifference difference) {
    return difference
        .filterEntriesInCommon(indexMappingProperty -> !isDynamicProperty(indexMappingProperty))
        .filterEntriesDiffering(
            propertyDifference -> !isDynamicProperty(propertyDifference.leftValue()));
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
    return Maps.filterKeys(
        indexMappings, k -> k.matches(indexDescriptor.getAllVersionsIndexNameRegexPattern()));
  }

  private boolean isDynamicProperty(final IndexMappingProperty indexMappingProperty) {
    return indexMappingProperty.typeDefinition() instanceof final Map typeDefMap
        && Boolean.parseBoolean(typeDefMap.getOrDefault("dynamic", "false").toString());
  }
}
