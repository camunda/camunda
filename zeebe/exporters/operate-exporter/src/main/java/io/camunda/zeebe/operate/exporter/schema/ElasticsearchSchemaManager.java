/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.operate.exporter.schema;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.operate.exporter.schema.IndexMapping.IndexMappingProperty;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.elasticsearch.action.admin.indices.alias.Alias;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.PutComponentTemplateRequest;
import org.elasticsearch.client.indices.PutComposableIndexTemplateRequest;
import org.elasticsearch.client.indices.PutIndexTemplateRequest;
import org.elasticsearch.client.indices.PutMappingRequest;
import org.elasticsearch.cluster.metadata.AliasMetadata;
import org.elasticsearch.cluster.metadata.ComponentTemplate;
import org.elasticsearch.cluster.metadata.ComposableIndexTemplate;
import org.elasticsearch.cluster.metadata.Template;
import org.elasticsearch.common.compress.CompressedXContent;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElasticsearchSchemaManager implements SchemaManager {

  private static final Logger LOGGER = LoggerFactory.getLogger(ElasticsearchSchemaManager.class);

  private static final String NUMBER_OF_SHARDS = "index.number_of_shards";
  private static final String NUMBER_OF_REPLICAS = "index.number_of_replicas";

  protected RetryElasticsearchClient retryElasticsearchClient;
  protected final ElasticSearchProperties elasticSearchProperties;
  private final List<IndexDescriptor> indexDescriptors;
  private final List<TemplateDescriptor> templateDescriptors;
  private final ObjectMapper objectMapper;

  public ElasticsearchSchemaManager(
      final RetryElasticsearchClient retryElasticsearchClient,
      final ElasticSearchProperties elasticSearchProperties,
      final List<IndexDescriptor> indexDescriptors,
      final List<TemplateDescriptor> templateDescriptors,
      final ObjectMapper objectMapper) {
    this.retryElasticsearchClient = retryElasticsearchClient;
    this.elasticSearchProperties = elasticSearchProperties;
    this.indexDescriptors = indexDescriptors;
    this.templateDescriptors = templateDescriptors;
    this.objectMapper = objectMapper;
  }

  @Override
  public void createSchema() {
    // TODO ILM
    //    if (operateProperties.getArchiver().isIlmEnabled()) {
    //      createIndexLifeCycles();
    //    }
    createDefaults();
    createTemplates();
    createIndices();
  }

  @Override
  public void checkAndUpdateIndices() {
    // TODO ILM
    //    indexDescriptors.forEach(
    //        descriptor -> {
    //          updateILM(descriptor.getDerivedIndexNamePattern());
    //        });
    //
    //    templateDescriptors.forEach(
    //        descriptor -> {
    //          updateILM(descriptor.getDerivedIndexNamePattern());
    //        });
  }

  @Override
  public void createDefaults() {
    final String settingsTemplate = settingsTemplateName();
    LOGGER.info(
        "Create default settings from '{}' with {} shards and {} replicas per index.",
        settingsTemplate,
        elasticSearchProperties.getNumberOfShards(),
        elasticSearchProperties.getNumberOfReplicas());

    final Settings settings = getDefaultIndexSettings();

    final Template template = new Template(settings, null, null);
    final ComponentTemplate componentTemplate = new ComponentTemplate(template, null, null);
    final PutComponentTemplateRequest request =
        new PutComponentTemplateRequest()
            .name(settingsTemplate)
            .componentTemplate(componentTemplate);
    retryElasticsearchClient.createComponentTemplate(request);
  }

  @Override
  public void createIndex(
      final IndexDescriptor indexDescriptor, final String indexClasspathResource) {
    final Map<String, Object> indexDescription =
        ElasticsearchJSONUtil.readJSONFileToMap(indexClasspathResource);
    createIndex(
        new CreateIndexRequest(indexDescriptor.getFullQualifiedName())
            .source(indexDescription)
            .aliases(Set.of(new Alias(indexDescriptor.getAlias()).writeIndex(false)))
            .settings(getIndexSettings(indexDescriptor.getIndexName())),
        indexDescriptor.getFullQualifiedName());
  }

  @Override
  public void createTemplate(
      final TemplateDescriptor templateDescriptor, final String templateClasspathResource) {
    final PutComposableIndexTemplateRequest request =
        prepareComposableTemplateRequest(templateDescriptor, templateClasspathResource);
    putIndexTemplate(request);

    // This is necessary, otherwise operate won't find indexes at startup
    final String indexName = templateDescriptor.getFullQualifiedName();
    final var createIndexRequest =
        new CreateIndexRequest(indexName)
            .aliases(Set.of(new Alias(templateDescriptor.getAlias()).writeIndex(false)))
            .settings(getIndexSettings(templateDescriptor.getIndexName()));
    createIndex(createIndexRequest, indexName);
  }

  @Override
  public void updateSchema(final Map<IndexDescriptor, Set<IndexMappingProperty>> newFields) {
    for (final Map.Entry<IndexDescriptor, Set<IndexMappingProperty>> indexNewFields :
        newFields.entrySet()) {
      if (indexNewFields.getKey() instanceof TemplateDescriptor) {
        LOGGER.info(
            "Update template: " + ((TemplateDescriptor) indexNewFields.getKey()).getTemplateName());
        final TemplateDescriptor templateDescriptor = (TemplateDescriptor) indexNewFields.getKey();
        final PutComposableIndexTemplateRequest request =
            prepareComposableTemplateRequest(templateDescriptor, null);
        putIndexTemplate(request, true);
      }
      final PutMappingRequest request = new PutMappingRequest(indexNewFields.getKey().getAlias());
      request.source(
          "{\"properties\":"
              + IndexMappingProperty.toJsonString(indexNewFields.getValue(), objectMapper)
              + "}",
          XContentType.JSON);
      LOGGER.info(
          String.format(
              "Index alias: %s. New fields will be added: %s",
              indexNewFields.getKey().getAlias(), indexNewFields.getValue()));
      retryElasticsearchClient.putMapping(request);
    }
  }

  private String settingsTemplateName() {
    return String.format("%s_template", elasticSearchProperties.getIndexPrefix());
  }

  private Settings getDefaultIndexSettings() {
    return Settings.builder()
        .put(NUMBER_OF_SHARDS, elasticSearchProperties.getNumberOfShards())
        .put(NUMBER_OF_REPLICAS, elasticSearchProperties.getNumberOfReplicas())
        .build();
  }

  private Settings getIndexSettings(final String indexName) {
    final var shards =
        elasticSearchProperties
            .getNumberOfShardsForIndices()
            .getOrDefault(indexName, elasticSearchProperties.getNumberOfShards());
    final var replicas =
        elasticSearchProperties
            .getNumberOfReplicasForIndices()
            .getOrDefault(indexName, elasticSearchProperties.getNumberOfReplicas());
    return Settings.builder()
        .put(NUMBER_OF_SHARDS, shards)
        .put(NUMBER_OF_REPLICAS, replicas)
        .build();
  }

  private void createIndices() {
    indexDescriptors.forEach(this::createIndex);
  }

  private void createTemplates() {
    templateDescriptors.forEach(this::createTemplate);
  }

  private void createIndex(final IndexDescriptor indexDescriptor) {
    createIndex(indexDescriptor, indexDescriptor.getSchemaClasspathFilename());
  }

  private void createTemplate(final TemplateDescriptor templateDescriptor) {
    createTemplate(templateDescriptor, null);
  }

  private PutComposableIndexTemplateRequest prepareComposableTemplateRequest(
      final TemplateDescriptor templateDescriptor, final String templateClasspathResource) {
    final String templateResourceName =
        templateClasspathResource != null
            ? templateClasspathResource
            : templateDescriptor.getSchemaClasspathFilename();

    final Template template = getTemplateFrom(templateDescriptor, templateResourceName);
    final ComposableIndexTemplate composableTemplate =
        new ComposableIndexTemplate.Builder()
            .indexPatterns(List.of(templateDescriptor.getIndexPattern()))
            .template(template)
            .componentTemplates(List.of(settingsTemplateName()))
            .build();
    final PutComposableIndexTemplateRequest request =
        new PutComposableIndexTemplateRequest()
            .name(templateDescriptor.getTemplateName())
            .indexTemplate(composableTemplate);
    return request;
  }

  private void overrideTemplateSettings(
      final Map<String, Object> templateConfig, final TemplateDescriptor templateDescriptor) {
    final Settings indexSettings = getIndexSettings(templateDescriptor.getIndexName());
    final Map<String, Object> settings =
        (Map<String, Object>) templateConfig.getOrDefault("settings", new HashMap<>());
    final Map<String, Object> index =
        (Map<String, Object>) settings.getOrDefault("index", new HashMap<>());
    index.put("number_of_shards", indexSettings.get(NUMBER_OF_SHARDS));
    index.put("number_of_replicas", indexSettings.get(NUMBER_OF_REPLICAS));
    settings.put("index", index);
    templateConfig.put("settings", settings);
  }

  private Template getTemplateFrom(
      final TemplateDescriptor templateDescriptor, final String templateFilename) {
    // Easiest way to create Template from json file: create 'old' request ang retrieve needed info
    final Map<String, Object> templateConfig =
        ElasticsearchJSONUtil.readJSONFileToMap(templateFilename);
    overrideTemplateSettings(templateConfig, templateDescriptor);
    final PutIndexTemplateRequest ptr =
        new PutIndexTemplateRequest(templateDescriptor.getTemplateName()).source(templateConfig);
    try {
      final Map<String, AliasMetadata> aliases =
          Map.of(
              templateDescriptor.getAlias(),
              AliasMetadata.builder(templateDescriptor.getAlias()).build());
      return new Template(ptr.settings(), new CompressedXContent(ptr.mappings()), aliases);
    } catch (final IOException e) {
      throw new RuntimeException(
          String.format("Error in reading mappings for %s ", templateDescriptor.getTemplateName()),
          e);
    }
  }

  private void createIndex(final CreateIndexRequest createIndexRequest, final String indexName) {
    final boolean created = retryElasticsearchClient.createIndex(createIndexRequest);
    if (created) {
      LOGGER.debug("Index [{}] was successfully created", indexName);
    } else {
      LOGGER.debug("Index [{}] was NOT created", indexName);
    }
  }

  private void putIndexTemplate(final PutComposableIndexTemplateRequest request) {
    putIndexTemplate(request, false);
  }

  private void putIndexTemplate(
      final PutComposableIndexTemplateRequest request, final boolean overwrite) {
    final boolean created = retryElasticsearchClient.createTemplate(request, overwrite);
    if (created) {
      LOGGER.debug("Template [{}] was successfully created", request.name());
    } else {
      LOGGER.debug("Template [{}] was NOT created", request.name());
    }
  }
}
