/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.schema;

import static java.util.Optional.ofNullable;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.search.schema.config.IndexConfiguration;
import io.camunda.search.schema.config.RetentionConfiguration;
import io.camunda.search.schema.config.SearchEngineConfiguration;
import io.camunda.search.schema.exceptions.SearchEngineException;
import io.camunda.search.schema.metrics.SchemaManagerMetrics;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import io.camunda.zeebe.util.retry.RetryDecorator;
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
import java.util.stream.Stream;
import org.agrona.LangUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SchemaManager {

  public static final int INDEX_CREATION_TIMEOUT_SECONDS = 60;
  private static final Logger LOG = LoggerFactory.getLogger(SchemaManager.class);
  private final SearchEngineClient searchEngineClient;
  private final Collection<IndexDescriptor> indexDescriptors;
  private final Collection<IndexTemplateDescriptor> indexTemplateDescriptors;
  private final SearchEngineConfiguration config;
  private final IndexSchemaValidator schemaValidator;
  private final ExecutorService virtualThreadExecutor;
  private final RetryDecorator retryDecorator;
  private final SchemaManagerMetrics schemaManagerMetrics;

  public SchemaManager(
      final SearchEngineClient searchEngineClient,
      final Collection<IndexDescriptor> indexDescriptors,
      final Collection<IndexTemplateDescriptor> indexTemplateDescriptors,
      final SearchEngineConfiguration config,
      final ObjectMapper objectMapper) {
    this(
        searchEngineClient,
        indexDescriptors,
        indexTemplateDescriptors,
        config,
        new IndexSchemaValidator(objectMapper),
        null);
  }

  private SchemaManager(
      final SearchEngineClient searchEngineClient,
      final Collection<IndexDescriptor> indexDescriptors,
      final Collection<IndexTemplateDescriptor> indexTemplateDescriptors,
      final SearchEngineConfiguration config,
      final IndexSchemaValidator schemaValidator,
      final SchemaManagerMetrics schemaManagerMetrics) {
    virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
    this.searchEngineClient = searchEngineClient;
    this.indexDescriptors = indexDescriptors;
    this.indexTemplateDescriptors = indexTemplateDescriptors;
    this.config = config;
    this.schemaValidator = schemaValidator;
    retryDecorator = new RetryDecorator(config.schemaManager().getRetry());
    this.schemaManagerMetrics = schemaManagerMetrics;
  }

  public SchemaManager withMetrics(final SchemaManagerMetrics schemaManagerMetrics) {
    return new SchemaManager(
        searchEngineClient,
        indexDescriptors,
        indexTemplateDescriptors,
        config,
        schemaValidator,
        schemaManagerMetrics);
  }

  /**
   * This method will retry with exponential backoff until the schema is successfully initialized.
   * By default, retries are effectively unlimited to prevent pods from crashing when Elasticsearch
   * is temporarily unavailable during startup.
   */
  public void startup() {
    if (!config.schemaManager().isCreateSchema()) {
      LOG.info(
          "Will not make any changes to indices and index templates as [createSchema] is false");
      return;
    }
    final var timer =
        ofNullable(schemaManagerMetrics)
            .map(SchemaManagerMetrics::startSchemaInitTimer)
            .orElse(() -> {});
    // even that initializeSchema does not declare throwing any exception, it may still do sneaky
    // throws (see #joinOnFutures) which are retried only by
    // io.github.resilience4j.retry.Retry.decorateCheckedRunnable
    retryDecorator.decorateCheckedRunnable("init schema", this::initializeSchema);
    // record the time taken to initialize schema only if it was successful
    timer.close();
  }

  private void initializeSchema() {
    LOG.info("Schema creation is enabled. Start Schema management.");
    final var newIndexProperties = validateIndices(getAllIndexDescriptors());
    //  used to create any indices/templates which don't exist
    initialiseResources();

    //  used to update existing indices/templates
    LOG.info("Update index schema. '{}' indices need to be updated", newIndexProperties.size());
    updateSchemaMappings(newIndexProperties);
    updateSchemaSettings();

    final RetentionConfiguration retention = config.retention();
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
    getAllIndexDescriptors().forEach(this::updateIndexReplicaCount);
  }

  private Collection<IndexDescriptor> getAllIndexDescriptors() {
    return Stream.concat(indexDescriptors.stream(), indexTemplateDescriptors.stream()).toList();
  }

  private void updateIndexReplicaCount(final IndexDescriptor indexDescriptor) {
    final var indexSettingsFromConfig = getIndexSettingsFromConfig(indexDescriptor.getIndexName());
    if (indexDescriptor instanceof final IndexTemplateDescriptor indexTemplateDescriptor) {
      searchEngineClient.updateIndexTemplateSettings(
          indexTemplateDescriptor, indexSettingsFromConfig);
    }
    searchEngineClient.putSettings(
        List.of(indexDescriptor),
        Map.of(
            "index.number_of_replicas",
            String.valueOf(indexSettingsFromConfig.getNumberOfReplicas())));
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

    final var missingIndices = getMissingIndices(indexDescriptors);
    LOG.info("Found '{}' missing indices", missingIndices.size());
    final var futures =
        missingIndices.stream()
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

  private List<IndexDescriptor> getMissingIndices(
      final Collection<IndexDescriptor> indexDescriptors) {
    if (indexDescriptors.isEmpty()) {
      return Collections.emptyList();
    }
    final var existingIndexNames = existingIndexNames(indexDescriptors);

    return indexDescriptors.stream()
        .filter(descriptor -> !existingIndexNames.contains(descriptor.getFullQualifiedName()))
        .toList();
  }

  private void initialiseIndexTemplates() {
    if (indexTemplateDescriptors.isEmpty()) {
      LOG.info("Do not create any index templates, as descriptors are missing");
      return;
    }
    LOG.info(
        "Creating index templates based on '{}' descriptors.", indexTemplateDescriptors.size());
    final var futures =
        indexTemplateDescriptors.stream()
            .map(
                descriptor ->
                    // run creation of indices async as virtual thread
                    CompletableFuture.runAsync(
                        () -> createIndexTemplate(descriptor), virtualThreadExecutor))
            .toArray(CompletableFuture[]::new);

    // We need to wait for the completion, to make sure all indices and templates have been created
    // successfully
    // Doing this in parallel is still speeding up the bootstrap time
    joinOnFutures(futures);
  }

  private List<IndexTemplateDescriptor> getMissingIndexTemplates(
      final Collection<IndexTemplateDescriptor> indexTemplateDescriptors) {
    if (indexTemplateDescriptors.isEmpty()) {
      return Collections.emptyList();
    }
    final var existingTemplateNames =
        searchEngineClient
            .getMappings(config.connect().getIndexPrefix() + "*", MappingSource.INDEX_TEMPLATE)
            .keySet();

    return indexTemplateDescriptors.stream()
        .filter(descriptor -> !existingTemplateNames.contains(descriptor.getTemplateName()))
        .toList();
  }

  /**
   * Create an index template and it's matching index.
   *
   * @param descriptor a description of the index template to create
   */
  private void createIndexTemplate(final IndexTemplateDescriptor descriptor) {
    try {
      searchEngineClient.createIndexTemplate(
          descriptor, getIndexSettingsFromConfig(descriptor.getIndexName()), true);
      LOG.info(
          "Index template '{}', has been created / already exists", descriptor.getTemplateName());

      searchEngineClient.createIndex(
          descriptor, getIndexSettingsFromConfig(descriptor.getIndexName()));

      LOG.info(
          "Index '{}', for template '{}' has been created / already exists",
          descriptor.getFullQualifiedName(),
          descriptor.getTemplateName());

    } catch (final SearchEngineException e) {
      final var errMsg =
          String.format("Index template '%s' could not be created", descriptor.getTemplateName());
      LOG.error(errMsg, e);
      throw new IllegalStateException(errMsg, e);
    }
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
        getAllIndexDescriptors().stream().map(IndexDescriptor::getFullQualifiedName).toList();
    indices.forEach(searchEngineClient::truncateIndex);
    return indices;
  }

  public void deleteArchivedIndices() {
    final var liveIndices =
        indexTemplateDescriptors.stream()
            .map(IndexDescriptor::getFullQualifiedName)
            .collect(Collectors.toSet());
    final String indexPatterns =
        indexTemplateDescriptors.stream()
            .map(IndexTemplateDescriptor::getIndexPattern)
            .collect(Collectors.joining(","));
    final var archivedIndices =
        searchEngineClient.getMappings(indexPatterns, MappingSource.INDEX).keySet().stream()
            .filter(index -> !liveIndices.contains(index))
            .toList();
    archivedIndices.forEach(searchEngineClient::deleteIndex);
    LOG.debug("Deleted archived indices '{}'", archivedIndices);
  }

  private IndexConfiguration getIndexSettingsFromConfig(final String indexName) {
    final var templateReplicas = getNumberOfReplicasFromConfig(indexName);
    final var templateShards =
        config
            .index()
            .getShardsByIndexName()
            .getOrDefault(indexName, config.index().getNumberOfShards());

    final var settings = new IndexConfiguration();
    settings.setNumberOfShards(templateShards);
    settings.setNumberOfReplicas(templateReplicas);
    settings.setTemplatePriority(config.index().getTemplatePriority());

    return settings;
  }

  private int getNumberOfReplicasFromConfig(final String indexName) {
    return config
        .index()
        .getReplicasByIndexName()
        .getOrDefault(indexName, config.index().getNumberOfReplicas());
  }

  private Map<IndexDescriptor, Collection<IndexMappingProperty>> validateIndices(
      final Collection<IndexDescriptor> indexDescriptors) {
    if (indexDescriptors.isEmpty()) {
      LOG.info("No validation of indices, as there are no descriptors");
      return Map.of();
    }

    final var currentIndices =
        searchEngineClient.getMappings(allIndexNames(indexDescriptors), MappingSource.INDEX);
    LOG.info(
        "Validate '{}' existing indices based on '{}' descriptors",
        currentIndices.size(),
        indexDescriptors.size());
    return schemaValidator.validateIndexMappings(currentIndices, indexDescriptors);
  }

  private Set<String> existingIndexNames(final Collection<IndexDescriptor> indexDescriptors) {
    final String allIndexNames = allIndexNames(indexDescriptors);
    return allIndexNames.isBlank()
        ? Set.of()
        : searchEngineClient.getMappings(allIndexNames, MappingSource.INDEX).keySet();
  }

  private String allIndexNames(final Collection<IndexDescriptor> indexDescriptors) {

    // The wildcard is required as without it, requests would fail if the index didn't exist.
    // this way all descriptors can be retrieved in one request without errors due to not created
    // indices

    return indexDescriptors.stream()
        .map(descriptor -> descriptor.getFullQualifiedName() + "*")
        .collect(Collectors.joining(","));
  }

  public boolean isSchemaReadyForUse() {
    if (!config.schemaManager().isCreateSchema()) {
      return true;
    }
    return getMissingIndices(getAllIndexDescriptors()).isEmpty()
        && getMissingIndexTemplates(indexTemplateDescriptors).isEmpty()
        && validateIndices(getAllIndexDescriptors()).isEmpty();
  }

  public boolean isAllIndicesExist() {
    return getMissingIndices(getAllIndexDescriptors()).isEmpty();
  }
}
