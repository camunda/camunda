/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.schema;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.db.search.engine.config.IndexSettings;
import io.camunda.db.search.engine.config.RetentionConfiguration;
import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.agrona.LangUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SchemaManager {

  public static final int INDEX_CREATION_TIMEOUT_SECONDS = 60;
  private static final Logger LOG = LoggerFactory.getLogger(SchemaManager.class);
  private final SearchEngineClient searchEngineClient;
  private final Collection<IndexDescriptor> indexDescriptors;
  private final Collection<IndexTemplateDescriptor> indexTemplateDescriptors;
  private final ExporterConfiguration config;
  private final ObjectMapper objectMapper;
  private final ExecutorService virtualThreadExecutor;

  public SchemaManager(
      final SearchEngineClient searchEngineClient,
      final Collection<IndexDescriptor> indexDescriptors,
      final Collection<IndexTemplateDescriptor> indexTemplateDescriptors,
      final ExporterConfiguration config,
      final ObjectMapper objectMapper) {
    virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
    this.searchEngineClient = searchEngineClient;
    this.indexDescriptors = indexDescriptors;
    this.indexTemplateDescriptors = indexTemplateDescriptors;
    this.config = config;
    this.objectMapper = objectMapper;
  }

  public void startup() {
    if (!config.isCreateSchema()) {
      LOG.info(
          "Will not make any changes to indices and index templates as [createSchema] is false");
      return;
    }
    LOG.info("Schema creation is enabled. Start Schema management.");
    final var schemaValidator = new IndexSchemaValidator(objectMapper);
    final var newIndexProperties = validateIndices(schemaValidator);
    final var newIndexTemplateProperties = validateIndexTemplates(schemaValidator);
    //  used to create any indices/templates which don't exist
    initialiseResources();

    //  used to update existing indices/templates
    LOG.info("Update index schema. '{}' indices need to be updated", newIndexProperties.size());
    updateSchemaMappings(newIndexProperties);
    LOG.info(
        "Update index template schema. '{}' index templates need to be updated",
        newIndexTemplateProperties.size());
    updateSchemaMappings(newIndexTemplateProperties);
    updateSchemaSettings();

    final RetentionConfiguration retention = config.getArchiver().getRetention();
    if (retention.isEnabled()) {
      LOG.info(
          "Retention is enabled. Create ILM policy [name: '{}', retention: '{}']",
          retention.getPolicyName(),
          retention.getMinimumAge());
      searchEngineClient.putIndexLifeCyclePolicy(
          retention.getPolicyName(), retention.getMinimumAge());
    }
    LOG.info("Schema management completed.");
  }

  private void updateSchemaSettings() {
    final var existingTemplateNames =
        searchEngineClient
            .getMappings(config.getIndex().getPrefix() + "*", MappingSource.INDEX_TEMPLATE)
            .keySet();

    final var existingIndexNames =
        allIndexNames().isBlank()
            ? Set.of()
            : searchEngineClient.getMappings(allIndexNames(), MappingSource.INDEX).keySet();

    indexTemplateDescriptors.stream()
        .filter(desc -> existingTemplateNames.contains(desc.getTemplateName()))
        .forEach(
            desc -> {
              searchEngineClient.updateIndexTemplateSettings(
                  desc, getIndexSettingsFromConfig(desc.getIndexName()));
            });

    // update matching index for the index template descriptor
    indexTemplateDescriptors.stream()
        .filter(desc -> existingIndexNames.contains(desc.getFullQualifiedName()))
        .forEach(this::updateIndexReplicaCount);

    indexDescriptors.stream()
        .filter(desc -> existingIndexNames.contains(desc.getFullQualifiedName()))
        .forEach(this::updateIndexReplicaCount);
  }

  private void updateIndexReplicaCount(final IndexDescriptor indexDescriptor) {
    final var indexReplicaCount =
        String.valueOf(getNumberOfReplicasFromConfig(indexDescriptor.getIndexName()));
    searchEngineClient.putSettings(
        List.of(indexDescriptor), Map.of("index.number_of_replicas", indexReplicaCount));
  }

  public void initialiseResources() {
    initialiseIndices();
    initialiseIndexTemplates();
  }

  private void initialiseIndices() {
    if (indexDescriptors.isEmpty()) {
      LOG.info("Do not create any indices, as descriptors are missing");
      return;
    }

    final var existingIndexNames =
        Collections.synchronizedSet(
            searchEngineClient.getMappings(allIndexNames(), MappingSource.INDEX).keySet());

    LOG.info(
        "Found '{}' existing indices. Create missing index templates based on '{}' descriptors.",
        existingIndexNames.size(),
        indexTemplateDescriptors.size());
    final var futures =
        indexDescriptors.stream()
            .filter(descriptor -> !existingIndexNames.contains(descriptor.getFullQualifiedName()))
            .map(
                descriptor ->
                    // run creation of indices async as virtual thread
                    CompletableFuture.runAsync(
                        () -> {
                          LOG.info("Create missing index '{}'", descriptor.getFullQualifiedName());
                          searchEngineClient.createIndex(
                              descriptor, getIndexSettingsFromConfig(descriptor.getIndexName()));
                        },
                        virtualThreadExecutor))
            .toArray(CompletableFuture[]::new);

    // We need to wait for the completion, to make sure all indices has been created successfully
    // Doing this in parallel is still speeding up the bootstrap time
    joinOnFutures(futures);
  }

  private void initialiseIndexTemplates() {
    if (indexTemplateDescriptors.isEmpty()) {
      LOG.info("Do not create any index templates, as descriptors are missing");
      return;
    }

    final var existingTemplateNames =
        Collections.synchronizedSet(
            searchEngineClient
                .getMappings(config.getIndex().getPrefix() + "*", MappingSource.INDEX_TEMPLATE)
                .keySet());

    LOG.info(
        "Found '{}' existing index templates. Create missing index templates based on '{}' descriptors.",
        existingTemplateNames.size(),
        indexTemplateDescriptors.size());
    final var futures =
        indexTemplateDescriptors.stream()
            .filter(descriptor -> !existingTemplateNames.contains(descriptor.getTemplateName()))
            .map(
                descriptor ->
                    // run creation of indices async as virtual thread
                    CompletableFuture.runAsync(
                        () -> {
                          LOG.info(
                              "Create missing index template '{}'", descriptor.getTemplateName());
                          searchEngineClient.createIndexTemplate(
                              descriptor,
                              getIndexSettingsFromConfig(descriptor.getIndexName()),
                              true);
                          LOG.info(
                              "Create missing index '{}', for template '{}'",
                              descriptor.getFullQualifiedName(),
                              descriptor.getTemplateName());
                          searchEngineClient.createIndex(
                              descriptor, getIndexSettingsFromConfig(descriptor.getIndexName()));
                        },
                        virtualThreadExecutor))
            .toArray(CompletableFuture[]::new);

    // We need to wait for the completion, to make sure all indices and templates have been created
    // successfully
    // Doing this in parallel is still speeding up the bootstrap time
    joinOnFutures(futures);
  }

  /**
   * Join on given futures with {@link SchemaManager#INDEX_CREATION_TIMEOUT_SECONDS} as timeout.
   *
   * <p>All exceptions, including timeout exception, are rethrown as unchecked exception. To reduce
   * boilerplate (exception handling), but make sure startup fails.
   *
   * @param futures futures that be joined on
   */
  private void joinOnFutures(final CompletableFuture<?>[] futures) {
    try {
      CompletableFuture.allOf(futures).get(INDEX_CREATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    } catch (final Exception e) {
      LangUtil.rethrowUnchecked(e);
    }
  }

  public void updateSchemaMappings(
      final Map<IndexDescriptor, Collection<IndexMappingProperty>> newFields) {
    for (final var newFieldEntry : newFields.entrySet()) {
      final var descriptor = newFieldEntry.getKey();
      final var newProperties = newFieldEntry.getValue();

      if (descriptor instanceof IndexTemplateDescriptor) {
        LOG.info(
            "Updating template: '{}'", ((IndexTemplateDescriptor) descriptor).getTemplateName());
        searchEngineClient.createIndexTemplate(
            (IndexTemplateDescriptor) descriptor,
            getIndexSettingsFromConfig(descriptor.getIndexName()),
            false);
      } else {
        LOG.info(
            "Index alias: '{}'. New fields will be added '{}'",
            descriptor.getFullQualifiedName(),
            newProperties);
      }
      searchEngineClient.putMapping(descriptor, newProperties);
    }
  }

  public List<String> truncateIndices() {
    final var indices =
        indexDescriptors.stream().map(IndexDescriptor::getFullQualifiedName).toList();
    indices.forEach(searchEngineClient::truncateIndex);
    return indices;
  }

  public void deleteArchivedIndices() {
    final var liveIndices =
        indexDescriptors.stream().map(IndexDescriptor::getFullQualifiedName).toList();
    final var archivedIndices =
        liveIndices.stream()
            .map(indexName -> indexName + "*")
            .map(idxWildcard -> searchEngineClient.getMappings(idxWildcard, MappingSource.INDEX))
            .map(Map::keySet)
            .flatMap(Collection::stream)
            .filter(index -> !liveIndices.contains(index))
            .toList();
    archivedIndices.forEach(searchEngineClient::deleteIndex);
    LOG.debug("Deleted archived indices '{}'", archivedIndices);
  }

  private IndexSettings getIndexSettingsFromConfig(final String indexName) {
    final var templateReplicas = getNumberOfReplicasFromConfig(indexName);
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

  private int getNumberOfReplicasFromConfig(final String indexName) {
    return config
        .getIndex()
        .getReplicasByIndexName()
        .getOrDefault(indexName, config.getIndex().getNumberOfReplicas());
  }

  private Map<IndexDescriptor, Collection<IndexMappingProperty>> validateIndices(
      final IndexSchemaValidator schemaValidator) {
    if (indexDescriptors.isEmpty()) {
      LOG.info("No validation of indices, as there are no descriptors");
      return Map.of();
    }

    final var currentIndices = searchEngineClient.getMappings(allIndexNames(), MappingSource.INDEX);
    LOG.info(
        "Validate '{}' existing indices based on '{}' descriptors",
        currentIndices.size(),
        indexDescriptors.size());
    return schemaValidator.validateIndexMappings(currentIndices, indexDescriptors);
  }

  private Map<IndexDescriptor, Collection<IndexMappingProperty>> validateIndexTemplates(
      final IndexSchemaValidator schemaValidator) {
    if (indexTemplateDescriptors.isEmpty()) {
      LOG.info("No validation of index templates, as there are no descriptors");
      return Map.of();
    }

    final var currentTemplates =
        searchEngineClient.getMappings(
            config.getIndex().getPrefix() + "*", MappingSource.INDEX_TEMPLATE);

    LOG.info(
        "Validate '{}' existing index templates based on '{}' template descriptors",
        currentTemplates.size(),
        indexTemplateDescriptors.size());
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
