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

  public void initialiseResources() {
    final var existingTemplateNames =
        searchEngineClient.getMappings("*", MappingSource.INDEX_TEMPLATE).keySet();
    final var existingIndexNames =
        searchEngineClient
            .getMappings(config.getIndex().getPrefix() + "*", MappingSource.INDEX)
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

    indexDescriptors.stream()
        .filter(descriptor -> !existingIndexNames.contains(descriptor.getFullQualifiedName()))
        .forEach(
            descriptor ->
                searchEngineClient.createIndex(
                    descriptor, getIndexSettings(descriptor.getIndexName())));
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
            .getReplicasByIndexName()
            .getOrDefault(indexName, config.getIndex().getNumberOfReplicas());
    final var templateShards =
        config
            .getShardsByIndexName()
            .getOrDefault(indexName, config.getIndex().getNumberOfShards());

    final var settings = new IndexSettings();
    settings.setNumberOfShards(templateShards);
    settings.setNumberOfReplicas(templateReplicas);

    return settings;
  }
}
