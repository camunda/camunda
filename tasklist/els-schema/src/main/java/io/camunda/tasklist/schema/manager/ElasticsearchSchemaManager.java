/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.schema.manager;

import static java.util.Optional.ofNullable;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.data.conditionals.ElasticSearchCondition;
import io.camunda.tasklist.es.RetryElasticsearchClient;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.property.TasklistElasticsearchProperties;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.schema.IndexMapping;
import io.camunda.tasklist.schema.IndexMapping.IndexMappingProperty;
import io.camunda.tasklist.schema.indices.AbstractIndexDescriptor;
import io.camunda.tasklist.schema.indices.IndexDescriptor;
import io.camunda.tasklist.schema.templates.TemplateDescriptor;
import io.camunda.tasklist.util.ElasticsearchJSONUtil;
import io.camunda.zeebe.util.VisibleForTesting;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.elasticsearch.action.admin.indices.alias.Alias;
import org.elasticsearch.client.indexlifecycle.DeleteAction;
import org.elasticsearch.client.indexlifecycle.LifecycleAction;
import org.elasticsearch.client.indexlifecycle.LifecyclePolicy;
import org.elasticsearch.client.indexlifecycle.Phase;
import org.elasticsearch.client.indexlifecycle.PutLifecyclePolicyRequest;
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
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

@Component("tasklistSchemaManager")
@Profile("!test")
@Conditional(ElasticSearchCondition.class)
public class ElasticsearchSchemaManager implements SchemaManager {

  public static final String TASKLIST_DELETE_ARCHIVED_INDICES = "tasklist_delete_archived_indices";
  public static final String INDEX_LIFECYCLE_NAME = "index.lifecycle.name";
  public static final String DELETE_PHASE = "delete";

  private static final Logger LOGGER = LoggerFactory.getLogger(ElasticsearchSchemaManager.class);

  private static final String NUMBER_OF_SHARDS = "index.number_of_shards";
  private static final String NUMBER_OF_REPLICAS = "index.number_of_replicas";

  @Autowired protected RetryElasticsearchClient retryElasticsearchClient;

  @Autowired protected TasklistProperties tasklistProperties;

  @Autowired
  @Qualifier("tasklistObjectMapper")
  private ObjectMapper objectMapper;

  @Autowired private List<AbstractIndexDescriptor> indexDescriptors;

  @Autowired private List<TemplateDescriptor> templateDescriptors;

  @Override
  public void createSchema() {
    if (tasklistProperties.getArchiver().isIlmEnabled()) {
      createIndexLifeCycles();
    }
    createDefaults();
    createTemplates();
    createIndices();
  }

  @Override
  public IndexMapping getExpectedIndexFields(final IndexDescriptor indexDescriptor) {
    final InputStream description =
        ElasticsearchSchemaManager.class.getResourceAsStream(
            indexDescriptor.getSchemaClasspathFilename());
    try {
      final String currentVersionSchema =
          StreamUtils.copyToString(description, StandardCharsets.UTF_8);
      final TypeReference<HashMap<String, Object>> type = new TypeReference<>() {};
      final Map<String, Object> mappings =
          (Map<String, Object>) objectMapper.readValue(currentVersionSchema, type).get("mappings");
      final Map<String, Object> properties = (Map<String, Object>) mappings.get("properties");
      final String dynamic = (String) mappings.get("dynamic");
      return new IndexMapping()
          .setIndexName(indexDescriptor.getIndexName())
          .setDynamic(dynamic)
          .setProperties(
              properties.entrySet().stream()
                  .map(
                      entry ->
                          new IndexMappingProperty()
                              .setName(entry.getKey())
                              .setTypeDefinition(entry.getValue()))
                  .collect(Collectors.toSet()));
    } catch (final IOException e) {
      throw new TasklistRuntimeException(e);
    }
  }

  @Override
  public Map<String, IndexMapping> getIndexMappings(final String indexName) {
    return retryElasticsearchClient.getIndexMappings(indexName);
  }

  @Override
  public String getIndexPrefix() {
    return tasklistProperties.getElasticsearch().getIndexPrefix();
  }

  @Override
  public void updateSchema(final Map<IndexDescriptor, Set<IndexMappingProperty>> newFields) {
    for (final Map.Entry<IndexDescriptor, Set<IndexMappingProperty>> indexNewFields :
        newFields.entrySet()) {
      if (indexNewFields.getKey() instanceof final TemplateDescriptor templateDescriptor) {
        LOGGER.info("Update template: {}", templateDescriptor.getTemplateName());
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

  @Override
  public void createIndex(final IndexDescriptor indexDescriptor) {
    createIndex(indexDescriptor, indexDescriptor.getSchemaClasspathFilename());
  }

  private String settingsTemplateName() {
    final TasklistElasticsearchProperties elsConfig = tasklistProperties.getElasticsearch();
    return String.format("%s_template", elsConfig.getIndexPrefix());
  }

  public void createDefaults() {
    final TasklistElasticsearchProperties elsConfig = tasklistProperties.getElasticsearch();
    final String settingsTemplate = settingsTemplateName();
    LOGGER.info(
        "Create default settings from '{}' with {} shards and {} replicas per index.",
        settingsTemplate,
        elsConfig.getNumberOfShards(),
        elsConfig.getNumberOfReplicas());

    final Settings settings = getDefaultIndexSettings();

    final Template template = new Template(settings, null, null);
    final ComponentTemplate componentTemplate = new ComponentTemplate(template, null, null);
    final PutComponentTemplateRequest request =
        new PutComponentTemplateRequest()
            .name(settingsTemplate)
            .componentTemplate(componentTemplate);
    retryElasticsearchClient.createComponentTemplate(request);
  }

  public void createIndexLifeCycles() {
    final TimeValue timeValue =
        TimeValue.parseTimeValue(
            tasklistProperties.getArchiver().getIlmMinAgeForDeleteArchivedIndices(),
            "IndexLifeCycle " + INDEX_LIFECYCLE_NAME);
    LOGGER.info(
        "Create Index Lifecycle {} for min age of {} ",
        TASKLIST_DELETE_ARCHIVED_INDICES,
        timeValue.getStringRep());
    final Map<String, Phase> phases = new HashMap<>();
    final Map<String, LifecycleAction> deleteActions =
        Collections.singletonMap(DeleteAction.NAME, new DeleteAction());
    phases.put(DELETE_PHASE, new Phase(DELETE_PHASE, timeValue, deleteActions));

    final LifecyclePolicy policy = new LifecyclePolicy(TASKLIST_DELETE_ARCHIVED_INDICES, phases);
    retryElasticsearchClient.putLifeCyclePolicy(new PutLifecyclePolicyRequest(policy));
  }

  private void createIndices() {
    indexDescriptors.forEach(this::createIndex);
  }

  private void createTemplates() {
    templateDescriptors.forEach(this::createTemplate);
  }

  private void createIndex(
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

  @VisibleForTesting
  void createTemplate(final TemplateDescriptor templateDescriptor) {
    createTemplate(templateDescriptor, null);
  }

  public void createTemplate(
      final TemplateDescriptor templateDescriptor, final String templateClasspathResource) {
    final PutComposableIndexTemplateRequest request =
        prepareComposableTemplateRequest(templateDescriptor, templateClasspathResource);
    putIndexTemplate(request);

    // This is necessary, otherwise tasklist won't find indexes at startup
    createIndex(templateDescriptor);
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
    final boolean created = retryElasticsearchClient.createTemplate(request);
    if (created) {
      LOGGER.debug("Template [{}] was successfully created", request.name());
    } else {
      LOGGER.debug("Template [{}] was NOT created", request.name());
    }
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
            .priority(
                ofNullable(tasklistProperties.getElasticsearch().getIndexTemplatePriority())
                    .map(Long::valueOf)
                    .orElse(null))
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

  private Settings getIndexSettings(final String indexName) {
    final TasklistElasticsearchProperties elsConfig = tasklistProperties.getElasticsearch();
    final var shards =
        elsConfig
            .getNumberOfShardsPerIndex()
            .getOrDefault(indexName, elsConfig.getNumberOfShards());
    final var replicas =
        elsConfig
            .getNumberOfReplicasPerIndices()
            .getOrDefault(indexName, elsConfig.getNumberOfReplicas());
    return Settings.builder()
        .put(NUMBER_OF_SHARDS, shards)
        .put(NUMBER_OF_REPLICAS, replicas)
        .build();
  }

  private Settings getDefaultIndexSettings() {
    final TasklistElasticsearchProperties elsConfig = tasklistProperties.getElasticsearch();
    return Settings.builder()
        .put(NUMBER_OF_SHARDS, elsConfig.getNumberOfShards())
        .put(NUMBER_OF_REPLICAS, elsConfig.getNumberOfReplicas())
        .build();
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
      throw new TasklistRuntimeException(
          String.format("Error in reading mappings for %s ", templateDescriptor.getTemplateName()),
          e);
    }
  }
}
