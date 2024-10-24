/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.schema;

import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.exporter.config.ExporterConfiguration.IndexSettings;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SchemaManager {
  private static final Logger LOG = LoggerFactory.getLogger(SchemaManager.class);
  private final SearchEngineClient searchEngineClient;
  private final Collection<IndexDescriptor> indexDescriptors;
  private final Collection<IndexTemplateDescriptor> indexTemplateDescriptors;
  private final ExporterConfiguration config;

  public SchemaManager(
      final SearchEngineClient searchEngineClient,
      final Collection<IndexDescriptor> indexDescriptors,
      final Collection<IndexTemplateDescriptor> indexTemplateDescriptors,
      final ExporterConfiguration config) {
    this.searchEngineClient = searchEngineClient;
    this.indexDescriptors = indexDescriptors;
    this.indexTemplateDescriptors = indexTemplateDescriptors;
    this.config = config;
  }

  public void startup() {
    if (!config.isCreateSchema()) {
      LOG.info(
          "Will not make any changes to indices and index templates as [createSchema] is false");
      return;
    }
    final var schemaValidator = new IndexSchemaValidator();
    final var newIndexProperties = validateIndices(schemaValidator, searchEngineClient);
    final var newIndexTemplateProperties =
        validateIndexTemplates(schemaValidator, searchEngineClient);
    //  used to create any indices/templates which don't exist
    initialiseResources();

    //  used to update existing indices/templates
    updateSchema(newIndexProperties);
    updateSchema(newIndexTemplateProperties);

    if (config.getRetention().isEnabled()) {
      searchEngineClient.putIndexLifeCyclePolicy(
          config.getRetention().getPolicyName(), config.getRetention().getMinimumAge());
    }
  }

  public void initialiseResources() {
    initialiseIndices();
    initialiseIndexTemplates();
  }

  private void initialiseIndices() {
    if (indexDescriptors.isEmpty()) {
      return;
    }

    final var existingIndexNames =
        searchEngineClient.getMappings(allIndexNames(), MappingSource.INDEX).keySet();

    indexDescriptors.stream()
        .filter(descriptor -> !existingIndexNames.contains(descriptor.getFullQualifiedName()))
        .forEach(
            descriptor ->
                searchEngineClient.createIndex(
                    descriptor, getIndexSettings(descriptor.getIndexName())));
  }

  private void initialiseIndexTemplates() {
    if (indexTemplateDescriptors.isEmpty()) {
      return;
    }

    final var existingTemplateNames =
        searchEngineClient
            .getMappings(config.getIndex().getPrefix() + "*", MappingSource.INDEX_TEMPLATE)
            .keySet();

    indexTemplateDescriptors.stream()
        .filter(descriptor -> !existingTemplateNames.contains(descriptor.getTemplateName()))
        .forEach(
            descriptor -> {
              searchEngineClient.createIndexTemplate(
                  descriptor, getIndexSettings(descriptor.getIndexName()), true);

              searchEngineClient.createIndex(
                  descriptor, getIndexSettings(descriptor.getIndexName()));
            });
  }

  public void updateSchema(final Map<IndexDescriptor, Collection<IndexMappingProperty>> newFields) {
    for (final var newFieldEntry : newFields.entrySet()) {
      final var descriptor = newFieldEntry.getKey();
      final var newProperties = newFieldEntry.getValue();

      if (descriptor instanceof IndexTemplateDescriptor) {
        LOG.info("Updating template: {}", ((IndexTemplateDescriptor) descriptor).getTemplateName());
        searchEngineClient.createIndexTemplate(
            (IndexTemplateDescriptor) descriptor,
            getIndexSettings(descriptor.getIndexName()),
            false);
      } else {
        LOG.info(
            "Index alias: {}. New fields will be added {}", descriptor.getAlias(), newProperties);

        searchEngineClient.putMapping(descriptor, newProperties);
      }
    }
  }

  private IndexSettings getIndexSettings(final String indexName) {
    final var templateReplicas =
        config
            .getIndex()
            .getReplicasByIndexName()
            .getOrDefault(indexName, config.getIndex().getNumberOfReplicas());
    final var templateShards =
        config
            .getIndex()
            .getShardsByIndexName()
            .getOrDefault(indexName, config.getIndex().getNumberOfShards());

    final var settings = new IndexSettings();
    settings.setNumberOfShards(templateShards);
    settings.setNumberOfReplicas(templateReplicas);

    return settings;
  }

  private Map<IndexDescriptor, Collection<IndexMappingProperty>> validateIndices(
      final IndexSchemaValidator schemaValidator, final SearchEngineClient searchEngineClient) {
    if (indexDescriptors.isEmpty()) {
      return Map.of();
    }

    final var currentIndices = searchEngineClient.getMappings(allIndexNames(), MappingSource.INDEX);

    return schemaValidator.validateIndexMappings(currentIndices, indexDescriptors);
  }

  private Map<IndexDescriptor, Collection<IndexMappingProperty>> validateIndexTemplates(
      final IndexSchemaValidator schemaValidator, final SearchEngineClient searchEngineClient) {
    if (indexTemplateDescriptors.isEmpty()) {
      return Map.of();
    }

    final var currentTemplates =
        searchEngineClient.getMappings(
            config.getIndex().getPrefix() + "*", MappingSource.INDEX_TEMPLATE);

    return schemaValidator.validateIndexMappings(
        currentTemplates,
        indexTemplateDescriptors.stream()
            .map(IndexDescriptor.class::cast)
            .collect(Collectors.toSet()));
  }

  private String allIndexNames() {

    // The wildcard is required as without it, requests would fail if the index didn't exist.
    // this way all descriptors can be retrieved in one request without errors due to not created
    // indices

    return indexDescriptors.stream()
        .map(descriptor -> descriptor.getFullQualifiedName() + "*")
        .collect(Collectors.joining(","));
  }
}
