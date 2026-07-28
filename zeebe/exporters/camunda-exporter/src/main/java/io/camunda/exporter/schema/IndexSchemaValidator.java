/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.schema;

import io.camunda.exporter.exceptions.IndexSchemaValidationException;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import java.util.*;
<<<<<<< HEAD:zeebe/exporters/camunda-exporter/src/main/java/io/camunda/exporter/schema/IndexSchemaValidator.java
import java.util.Map.Entry;
=======
>>>>>>> 8a859b9a (fix: name specific index in schema validation error messages):schema-manager/src/main/java/io/camunda/search/schema/IndexSchemaValidator.java
import java.util.stream.Collectors;
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

  private final SchemaManager schemaManager;

  public IndexSchemaValidator(final SchemaManager schemaManager) {
    this.schemaManager = schemaManager;
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
  public Map<IndexDescriptor, Set<IndexMappingProperty>> validateIndexMappings(
      final Map<String, IndexMapping> mappings, final Set<IndexDescriptor> indexDescriptors)
      throws IndexSchemaValidationException {
    final Map<IndexDescriptor, Set<IndexMappingProperty>> newFields = new HashMap<>();
    for (final IndexDescriptor indexDescriptor : indexDescriptors) {
      final Map<String, IndexMapping> indexMappingsGroup =
          filterIndexMappings(mappings, indexDescriptor);
      // we don't check indices that were not yet created
      if (!indexMappingsGroup.isEmpty()) {
        final DifferingIndices differingIndices =
            getIndexMappingDifference(indexDescriptor, indexMappingsGroup);
<<<<<<< HEAD:zeebe/exporters/camunda-exporter/src/main/java/io/camunda/exporter/schema/IndexSchemaValidator.java
        validateDifferenceAndCollectNewFields(indexDescriptor, difference, newFields);
=======
        validateDifferenceAndCollectNewFields(indexDescriptor, differingIndices, newFields);
>>>>>>> 8a859b9a (fix: name specific index in schema validation error messages):schema-manager/src/main/java/io/camunda/search/schema/IndexSchemaValidator.java
      }
    }
    return newFields;
  }

  private void validateDifferenceAndCollectNewFields(
      final IndexDescriptor indexDescriptor,
<<<<<<< HEAD:zeebe/exporters/camunda-exporter/src/main/java/io/camunda/exporter/schema/IndexSchemaValidator.java
      final IndexMappingDifference difference,
      final Map<IndexDescriptor, Set<IndexMappingProperty>> newFields) {
    if (difference != null && !difference.equal()) {
      LOGGER.debug(
          "Index fields differ from expected. Index name: {}. Difference: {}.",
          indexDescriptor.getIndexName(),
          difference);

      if (!difference.entriesDiffering().isEmpty()) {
        // This call will throw an exception unless the index is dynamic, in which case
        // field differences will be ignored. In the case of a dynamic index, we still want
        // to collect any new fields, so we should continue to the next checks instead of making
        // this part of the if/else block
        failIfIndexNotDynamic(difference, indexDescriptor);
=======
      final DifferingIndices differingIndices,
      final Map<IndexDescriptor, Collection<IndexMappingProperty>> newFields) {
    if (differingIndices != null) {
      final IndexMappingDifference difference = differingIndices.difference();
      LOGGER.debug(
          "Index fields differ from expected. Index names: {}. Difference: {}.",
          differingIndices.indexNames(),
          difference);

      if (!difference.entriesDiffering().isEmpty()) {
        final String errorMsg =
            String.format(
                "Index names: %s. Unsupported index changes have been introduced. Data migration is required. Changes found: %s",
                differingIndices.indexNames(), difference.entriesDiffering());
        LOGGER.error(errorMsg);
        throw new IndexSchemaValidationException(errorMsg);
>>>>>>> 8a859b9a (fix: name specific index in schema validation error messages):schema-manager/src/main/java/io/camunda/search/schema/IndexSchemaValidator.java
      }

      if (!difference.entriesOnlyOnRight().isEmpty()) {
        LOGGER.info(
<<<<<<< HEAD:zeebe/exporters/camunda-exporter/src/main/java/io/camunda/exporter/schema/IndexSchemaValidator.java
            "Index '{}': Field deletion is requested, will be ignored. Fields: {}",
            indexDescriptor.getIndexName(),
=======
            "Index names '{}': Field deletion is requested, will be ignored. Fields: {}",
            differingIndices.indexNames(),
>>>>>>> 8a859b9a (fix: name specific index in schema validation error messages):schema-manager/src/main/java/io/camunda/search/schema/IndexSchemaValidator.java
            difference.entriesOnlyOnRight());

      } else if (!difference.entriesOnlyOnLeft().isEmpty()) {
        // Collect the new fields
        newFields.put(indexDescriptor, difference.entriesOnlyOnLeft());
      }
    } else {
      LOGGER.debug("Index fields are up to date for Index '{}'.", indexDescriptor.getIndexName());
    }
  }

  private DifferingIndices getIndexMappingDifference(
      final IndexDescriptor indexDescriptor, final Map<String, IndexMapping> indexMappingsGroup) {
    final IndexMapping indexMappingMustBe = schemaManager.readIndex(indexDescriptor);

<<<<<<< HEAD:zeebe/exporters/camunda-exporter/src/main/java/io/camunda/exporter/schema/IndexSchemaValidator.java
    final var differences =
        indexMappingsGroup.values().stream()
            .map(mapping -> IndexMappingDifference.of(indexMappingMustBe, mapping))
            .filter(difference -> !difference.equal())
            .distinct()
            .toList();
=======
    // sorted by index name so grouping and the reported index names are stable regardless of
    // the source map's (HashMap) iteration order
    final Map<IndexMappingDifference, List<String>> differencesByIndexName =
        indexMappingsGroup.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(
                entry ->
                    Map.entry(
                        entry.getKey(),
                        filterOutDynamicProperties(
                            IndexMappingDifference.of(indexMappingMustBe, entry.getValue()))))
            // filtered after dynamic properties are stripped: `equal` is fixed at construction
            // and won't reflect a diff that only turned out to be dynamic-property noise
            .filter(entry -> hasRealDifference(entry.getValue()))
            .collect(
                Collectors.groupingBy(
                    Map.Entry::getValue,
                    LinkedHashMap::new,
                    Collectors.mapping(Map.Entry::getKey, Collectors.toList())));
>>>>>>> 8a859b9a (fix: name specific index in schema validation error messages):schema-manager/src/main/java/io/camunda/search/schema/IndexSchemaValidator.java

    if (differencesByIndexName.isEmpty()) {
      return null;
    }

<<<<<<< HEAD:zeebe/exporters/camunda-exporter/src/main/java/io/camunda/exporter/schema/IndexSchemaValidator.java
    if (differences.size() > 1) {
=======
    if (differencesByIndexName.size() > 1) {
      LOGGER.debug(
          "Ambiguous schema update. Index names: {}. Difference: {}.",
          indexMappingsGroup.keySet(),
          differencesByIndexName);
>>>>>>> 8a859b9a (fix: name specific index in schema validation error messages):schema-manager/src/main/java/io/camunda/search/schema/IndexSchemaValidator.java
      throw new IndexSchemaValidationException(
          String.format(
              "Ambiguous schema update. Multiple indices for mapping '%s' have different fields. Differences by index: %s",
              indexDescriptor.getIndexName(), differencesByIndexName));
    }

    final var onlyEntry = differencesByIndexName.entrySet().iterator().next();
    return new DifferingIndices(onlyEntry.getKey(), onlyEntry.getValue());
  }

  /**
   * {@link IndexMappingDifference#equal()} is fixed at construction time and isn't recomputed by
   * {@link #filterOutDynamicProperties}, so it can no longer be trusted after filtering. Checks the
   * actual remaining entries instead.
   */
  private boolean hasRealDifference(final IndexMappingDifference difference) {
    return !difference.entriesDiffering().isEmpty()
        || !difference.entriesOnlyOnLeft().isEmpty()
        || !difference.entriesOnlyOnRight().isEmpty();
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

  private void failIfIndexNotDynamic(
      final IndexMappingDifference difference, final IndexDescriptor indexDescriptor) {
    if (difference.isLeftDynamic() || difference.isRightDynamic()) {
      LOGGER.debug(
          "Index '{}' is dynamic, ignoring changes found: {}",
          indexDescriptor.getIndexName(),
          difference.entriesDiffering());
    } else {
      final String errorMsg =
          String.format(
              "Index name: %s. Not supported index changes are introduced. Data migration is required. Changes found: %s",
              indexDescriptor.getIndexName(), difference.entriesDiffering());
      LOGGER.error(errorMsg);
      throw new IndexSchemaValidationException(errorMsg);
    }
  }

  private record DifferingIndices(IndexMappingDifference difference, List<String> indexNames) {}
}
