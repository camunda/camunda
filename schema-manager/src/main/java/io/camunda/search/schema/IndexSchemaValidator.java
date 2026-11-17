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

  private final ObjectMapper objectMapper;

  public IndexSchemaValidator(final ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /**
   * Validates existing indices mappings against index/index template mappings defined. For an
   * {@link IndexTemplateDescriptor}, the template's own stored mapping is additionally validated
   * against the descriptor, independently of whether a backing index currently exists.
   *
   * @param mappings is a map of all the mappings to compare.
   * @param indexDescriptors is the set of all index descriptors representing desired schema states.
   * @param templateMappings existing index template mappings, keyed by template name
   * @return new mapping properties to add to schemas, so they align with the descriptors.
   * @throws IndexSchemaValidationException if the existing indices cannot be updated with the given
   *     mappings.
   */
  public Map<IndexDescriptor, Collection<IndexMappingProperty>> validateIndexMappings(
      final Map<String, IndexMapping> mappings,
      final Collection<IndexDescriptor> indexDescriptors,
      final Map<String, IndexMapping> templateMappings)
      throws IndexSchemaValidationException {
    final Map<IndexDescriptor, Collection<IndexMappingProperty>> newFields = new HashMap<>();
    for (final IndexDescriptor indexDescriptor : indexDescriptors) {
      final Map<String, IndexMapping> indexMappingsGroup =
          filterIndexMappings(mappings, indexDescriptor);
      if (!indexMappingsGroup.isEmpty()) {
        final DifferingIndices differingIndices =
            getIndexMappingDifference(indexDescriptor, indexMappingsGroup);
        validateDifferenceAndCollectNewFields(indexDescriptor, differingIndices, newFields);
      }
      // Validated independently of the backing-index comparison above because an up-to-date backing
      // index does not imply an up-to-date template.
      if (indexDescriptor instanceof final IndexTemplateDescriptor templateDescriptor) {
        compareWithExistingTemplates(
            templateDescriptor, templateMappings, !indexMappingsGroup.isEmpty(), newFields);
      }
    }
    return newFields;
  }

  private void validateDifferenceAndCollectNewFields(
      final IndexDescriptor indexDescriptor,
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
      }

      if (!difference.entriesOnlyOnRight().isEmpty()) {
        LOGGER.info(
            "Index names '{}': Field deletion is requested, will be ignored. Fields: {}",
            differingIndices.indexNames(),
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

  private void compareWithExistingTemplates(
      final IndexTemplateDescriptor templateDescriptor,
      final Map<String, IndexMapping> templateMappings,
      final boolean hasBackingIndex,
      final Map<IndexDescriptor, Collection<IndexMappingProperty>> newFields) {
    final IndexMapping existingTemplateMapping =
        templateMappings.get(templateDescriptor.getTemplateName());
    if (existingTemplateMapping == null) {
      // template itself does not exist either - it will be created from scratch
      return;
    }

    final IndexMappingDifference difference =
        filterOutDynamicProperties(
            IndexMappingDifference.of(
                IndexMapping.from(templateDescriptor, objectMapper), existingTemplateMapping));
    if (!hasRealDifference(difference)) {
      LOGGER.debug(
          "Template fields are up to date for template '{}'.",
          templateDescriptor.getTemplateName());
      return;
    }

    if (!difference.entriesOnlyOnRight().isEmpty()) {
      if (hasBackingIndex) {
        // A backing index exists and is the source of truth: a field only in the stored template
        // may simply mean this node's descriptor is stale (e.g. an older node racing after a newer
        // node already upgraded the schema). This comparison alone will not touch the template,
        // but if the backing-index comparison already scheduled a template update, that update
        // still fully overwrites the template from the descriptor and will drop this field.
        if (newFields.containsKey(templateDescriptor)) {
          LOGGER.info(
              "Template '{}': Field(s) only present in the stored template will be dropped, as"
                  + " the template is being updated for a different reason. Fields: {}",
              templateDescriptor.getTemplateName(),
              difference.entriesOnlyOnRight());
        } else {
          LOGGER.info(
              "Template '{}': Field(s) only present in the stored template, will be left as is."
                  + " Fields: {}",
              templateDescriptor.getTemplateName(),
              difference.entriesOnlyOnRight());
        }
        return;
      }
      // No backing index/data exists for this descriptor at all, so it is safe to fully overwrite
      // the template, including dropping this field.
      LOGGER.info(
          "Template '{}': removing field(s) no longer present in the descriptor. Fields: {}",
          templateDescriptor.getTemplateName(),
          difference.entriesOnlyOnRight());
    }
    if (!difference.entriesDiffering().isEmpty()) {
      LOGGER.info(
          "Template '{}': Field types differ from expected. Changes found: {}",
          templateDescriptor.getTemplateName(),
          difference.entriesDiffering());
    }

    final var changedProperties = new HashSet<>(difference.entriesOnlyOnLeft());
    difference.entriesDiffering().forEach(d -> changedProperties.add(d.leftValue()));
    newFields.merge(
        templateDescriptor,
        changedProperties,
        (existing, added) -> {
          final var merged = new HashSet<>(existing);
          merged.addAll(added);
          return merged;
        });
  }

  private DifferingIndices getIndexMappingDifference(
      final IndexDescriptor indexDescriptor, final Map<String, IndexMapping> indexMappingsGroup) {
    final IndexMapping indexMappingMustBe = IndexMapping.from(indexDescriptor, objectMapper);

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

    if (differencesByIndexName.isEmpty()) {
      return null;
    }

    if (differencesByIndexName.size() > 1) {
      LOGGER.debug(
          "Ambiguous schema update. Index names: {}. Difference: {}.",
          indexMappingsGroup.keySet(),
          differencesByIndexName);
      throw new IndexSchemaValidationException(
          String.format(
              "Ambiguous schema update. Multiple indices for mapping '%s' have different fields. Differences by index: %s",
              indexDescriptor.getIndexName(), differencesByIndexName));
    }

    final var onlyEntry = differencesByIndexName.entrySet().iterator().next();
    return new DifferingIndices(onlyEntry.getKey(), onlyEntry.getValue());
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
    return Maps.filterKeys(
        indexMappings, k -> k.matches(indexDescriptor.getAllVersionsIndexNameRegexPattern()));
  }

  private boolean isDynamicProperty(final IndexMappingProperty indexMappingProperty) {
    return indexMappingProperty.typeDefinition() instanceof final Map typeDefMap
        && Boolean.parseBoolean(typeDefMap.getOrDefault("dynamic", "false").toString());
  }

  private record DifferingIndices(IndexMappingDifference difference, List<String> indexNames) {}
}
