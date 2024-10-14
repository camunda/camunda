/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.schema;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.exporter.config.ExporterConfiguration.IndexSettings;
import io.camunda.exporter.exceptions.ElasticsearchExporterException;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElasticsearchSchemaManager implements SchemaManager {
  private static final Logger LOG = LoggerFactory.getLogger(ElasticsearchSchemaManager.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private final SearchEngineClient elasticsearchClient;
  private final Set<IndexDescriptor> indexDescriptors;
  private final Set<IndexTemplateDescriptor> indexTemplateDescriptors;
  private final ExporterConfiguration config;

  public ElasticsearchSchemaManager(
      final SearchEngineClient elasticsearchClient,
      final Set<IndexDescriptor> indexDescriptors,
      final Set<IndexTemplateDescriptor> indexTemplateDescriptors,
      final ExporterConfiguration config) {
    this.elasticsearchClient = elasticsearchClient;
    this.indexDescriptors = indexDescriptors;
    this.indexTemplateDescriptors = indexTemplateDescriptors;
    this.config = config;
  }

  @Override
  public void initialiseResources() {
    final var existingTemplateNames =
        elasticsearchClient.getMappings("*", MappingSource.INDEX_TEMPLATE).keySet();
    final var existingIndexNames =
        elasticsearchClient
            .getMappings(config.getIndex().getPrefix() + "*", MappingSource.INDEX)
            .keySet();
    indexTemplateDescriptors.stream()
        .filter(descriptor -> !existingTemplateNames.contains(descriptor.getTemplateName()))
        .forEach(
            descriptor ->
                elasticsearchClient.createIndexTemplate(
                    descriptor, getIndexSettings(descriptor.getIndexName()), true));

    indexDescriptors.stream()
        .filter(descriptor -> !existingIndexNames.contains(descriptor.getFullQualifiedName()))
        .forEach(
            descriptor ->
                elasticsearchClient.createIndex(
                    descriptor, getIndexSettings(descriptor.getIndexName())));
  }

  @Override
  public void updateSchema(final Map<IndexDescriptor, Set<IndexMappingProperty>> newFields) {
    for (final var newFieldEntry : newFields.entrySet()) {
      final var descriptor = newFieldEntry.getKey();
      final var newProperties = newFieldEntry.getValue();

      if (descriptor instanceof IndexTemplateDescriptor) {
        LOG.info("Updating template: {}", ((IndexTemplateDescriptor) descriptor).getTemplateName());
        elasticsearchClient.createIndexTemplate(
            (IndexTemplateDescriptor) descriptor,
            getIndexSettings(descriptor.getIndexName()),
            false);
      } else {
        LOG.info(
            "Index alias: {}. New fields will be added {}", descriptor.getAlias(), newProperties);

        elasticsearchClient.putMapping(descriptor, newProperties);
      }
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public IndexMapping readIndex(final IndexDescriptor indexDescriptor) {
    try (final var mappingsStream =
        getClass().getResourceAsStream(indexDescriptor.getMappingsClasspathFilename())) {
      final var nestedType = new TypeReference<Map<String, Map<String, Object>>>() {};
      final Map<String, Object> mappings =
          MAPPER.readValue(mappingsStream, nestedType).get("mappings");
      final Map<String, Object> properties = (Map<String, Object>) mappings.get("properties");
      final var dynamic = mappings.get("dynamic");

      return new IndexMapping.Builder()
          .indexName(indexDescriptor.getIndexName())
          .dynamic(dynamic == null ? "strict" : dynamic.toString())
          .properties(
              properties.entrySet().stream()
                  .map(IndexMappingProperty::createIndexMappingProperty)
                  .collect(Collectors.toSet()))
          .build();
    } catch (final IOException e) {
      throw new ElasticsearchExporterException(
          String.format(
              "Failed to parse index json [%s]", indexDescriptor.getMappingsClasspathFilename()),
          e);
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
