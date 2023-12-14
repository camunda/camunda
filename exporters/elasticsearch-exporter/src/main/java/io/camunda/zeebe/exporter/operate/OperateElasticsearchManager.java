/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.exporter.operate;

import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.property.OperateElasticsearchProperties;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.SchemaManager;
import io.camunda.zeebe.exporter.operate.schema.indices.DecisionIndex;
import io.camunda.zeebe.exporter.operate.schema.indices.DecisionRequirementsIndex;
import io.camunda.zeebe.exporter.operate.schema.indices.ImportPositionIndex;
import io.camunda.zeebe.exporter.operate.schema.indices.IndexDescriptor;
import io.camunda.zeebe.exporter.operate.schema.indices.MetricIndex;
import io.camunda.zeebe.exporter.operate.schema.indices.MigrationRepositoryIndex;
import io.camunda.zeebe.exporter.operate.schema.indices.OperateWebSessionIndex;
import io.camunda.zeebe.exporter.operate.schema.indices.ProcessIndex;
import io.camunda.zeebe.exporter.operate.schema.indices.UserIndex;
import io.camunda.zeebe.exporter.operate.schema.templates.BatchOperationTemplate;
import io.camunda.zeebe.exporter.operate.schema.templates.DecisionInstanceTemplate;
import io.camunda.zeebe.exporter.operate.schema.templates.EventTemplate;
import io.camunda.zeebe.exporter.operate.schema.templates.FlowNodeInstanceTemplate;
import io.camunda.zeebe.exporter.operate.schema.templates.IncidentTemplate;
import io.camunda.zeebe.exporter.operate.schema.templates.ListViewTemplate;
import io.camunda.zeebe.exporter.operate.schema.templates.OperationTemplate;
import io.camunda.zeebe.exporter.operate.schema.templates.PostImporterQueueTemplate;
import io.camunda.zeebe.exporter.operate.schema.templates.SequenceFlowTemplate;
import io.camunda.zeebe.exporter.operate.schema.templates.TemplateDescriptor;
import io.camunda.zeebe.exporter.operate.schema.templates.VariableTemplate;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.elasticsearch.action.admin.indices.alias.Alias;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indexlifecycle.DeleteAction;
import org.elasticsearch.client.indexlifecycle.LifecycleAction;
import org.elasticsearch.client.indexlifecycle.LifecyclePolicy;
import org.elasticsearch.client.indexlifecycle.Phase;
import org.elasticsearch.client.indexlifecycle.PutLifecyclePolicyRequest;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.PutComponentTemplateRequest;
import org.elasticsearch.client.indices.PutComposableIndexTemplateRequest;
import org.elasticsearch.client.indices.PutIndexTemplateRequest;
import org.elasticsearch.cluster.metadata.AliasMetadata;
import org.elasticsearch.cluster.metadata.ComponentTemplate;
import org.elasticsearch.cluster.metadata.ComposableIndexTemplate;
import org.elasticsearch.cluster.metadata.Template;
import org.elasticsearch.common.compress.CompressedXContent;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentHelper;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OperateElasticsearchManager implements SchemaManager {

  private static final Logger LOGGER = LoggerFactory.getLogger(OperateElasticsearchManager.class);

  private static final String NUMBER_OF_SHARDS = "index.number_of_shards";
  private static final String NUMBER_OF_REPLICAS = "index.number_of_replicas";

  protected NoSpringRetryElasticsearchClient retryElasticsearchClient;
  protected OperateProperties operateProperties;
  private List<IndexDescriptor> indexDescriptors;
  private List<TemplateDescriptor> templateDescriptors;

  public OperateElasticsearchManager(RestHighLevelClient client) {
    this.retryElasticsearchClient = new NoSpringRetryElasticsearchClient(client);
    this.operateProperties = new OperateProperties(); // trying with the default properties for nwo

    final String indexPrefix = operateProperties.getElasticsearch().getIndexPrefix();

    indexDescriptors =
        Arrays.asList(
            new DecisionIndex(indexPrefix),
            new DecisionRequirementsIndex(indexPrefix),
            new ImportPositionIndex(indexPrefix),
            new MetricIndex(indexPrefix),
            new MigrationRepositoryIndex(indexPrefix),
            new OperateWebSessionIndex(indexPrefix),
            new ProcessIndex(indexPrefix),
            new UserIndex(indexPrefix));
    templateDescriptors =
        Arrays.asList(
            new BatchOperationTemplate(indexPrefix),
            new DecisionInstanceTemplate(indexPrefix),
            new EventTemplate(indexPrefix),
            new FlowNodeInstanceTemplate(indexPrefix),
            new IncidentTemplate(indexPrefix),
            new ListViewTemplate(indexPrefix),
            new OperationTemplate(indexPrefix),
            new PostImporterQueueTemplate(indexPrefix),
            new SequenceFlowTemplate(indexPrefix),
            new VariableTemplate(indexPrefix));
  }

  public <T extends IndexDescriptor> T getIndexDescriptor(Class<T> clazz) {
    return (T)
        indexDescriptors.stream()
            .filter(i -> i.getClass() == clazz)
            .findFirst()
            .orElseThrow(
                () -> new RuntimeException("Could not find template descriptor of class " + clazz));
  }

  public <T extends TemplateDescriptor> T getTemplateDescriptor(Class<T> clazz) {
    return (T)
        templateDescriptors.stream()
            .filter(i -> i.getClass() == clazz)
            .findFirst()
            .orElseThrow(
                () -> new RuntimeException("Could not find template descriptor of class " + clazz));
  }

  @Override
  public void createSchema() {
    LOGGER.info("Creating schema");
    if (operateProperties.getArchiver().isIlmEnabled()) {
      createIndexLifeCycles();
    }
    createDefaults();
    createTemplates();
    createIndices();
    LOGGER.info("Schema created");
  }

  @Override
  public boolean setIndexSettingsFor(Map<String, ?> settings, String indexPattern) {
    return retryElasticsearchClient.setIndexSettingsFor(
        Settings.builder().loadFromMap(settings).build(), indexPattern);
  }

  @Override
  public String getOrDefaultRefreshInterval(String indexName, String defaultValue) {
    return retryElasticsearchClient.getOrDefaultRefreshInterval(indexName, defaultValue);
  }

  @Override
  public String getOrDefaultNumbersOfReplica(String indexName, String defaultValue) {
    return retryElasticsearchClient.getOrDefaultNumbersOfReplica(indexName, defaultValue);
  }

  @Override
  public void refresh(String indexPattern) {
    retryElasticsearchClient.refresh(indexPattern);
  }

  @Override
  public boolean isHealthy() {
    return retryElasticsearchClient.isHealthy();
  }

  @Override
  public Set<String> getIndexNames(String indexPattern) {
    return retryElasticsearchClient.getIndexNames(indexPattern);
  }

  @Override
  public Set<String> getAliasesNames(final String indexPattern) {
    return retryElasticsearchClient.getAliasesNames(indexPattern);
  }

  @Override
  public long getNumberOfDocumentsFor(String... indexPatterns) {
    return retryElasticsearchClient.getNumberOfDocumentsFor(indexPatterns);
  }

  @Override
  public boolean deleteIndicesFor(String indexPattern) {
    return retryElasticsearchClient.deleteIndicesFor(indexPattern);
  }

  @Override
  public boolean deleteTemplatesFor(String deleteTemplatePattern) {
    return retryElasticsearchClient.deleteTemplatesFor(deleteTemplatePattern);
  }

  @Override
  public void removePipeline(String pipelineName) {
    retryElasticsearchClient.removePipeline(pipelineName);
  }

  @Override
  public boolean addPipeline(String name, String pipelineDefinition) {
    return retryElasticsearchClient.addPipeline(name, pipelineDefinition);
  }

  @Override
  public Map<String, String> getIndexSettingsFor(String indexName, String... fields) {
    return retryElasticsearchClient.getIndexSettingsFor(indexName, fields);
  }

  private String settingsTemplateName() {
    final OperateElasticsearchProperties elsConfig = operateProperties.getElasticsearch();
    return String.format("%s_template", elsConfig.getIndexPrefix());
  }

  private Settings getIndexSettings() {
    final OperateElasticsearchProperties elsConfig = operateProperties.getElasticsearch();
    return Settings.builder()
        .put(NUMBER_OF_SHARDS, elsConfig.getNumberOfShards())
        .put(NUMBER_OF_REPLICAS, elsConfig.getNumberOfReplicas())
        .build();
  }

  private void createDefaults() {
    final OperateElasticsearchProperties elsConfig = operateProperties.getElasticsearch();
    final String settingsTemplate = settingsTemplateName();
    LOGGER.info(
        "Create default settings from '{}' with {} shards and {} replicas per index.",
        settingsTemplate,
        elsConfig.getNumberOfShards(),
        elsConfig.getNumberOfReplicas());

    final Settings settings = getIndexSettings();

    final Template template = new Template(settings, null, null);
    final ComponentTemplate componentTemplate = new ComponentTemplate(template, null, null);
    final PutComponentTemplateRequest request =
        new PutComponentTemplateRequest()
            .name(settingsTemplate)
            .componentTemplate(componentTemplate);
    retryElasticsearchClient.createComponentTemplate(request);
  }

  private void createIndexLifeCycles() {
    final TimeValue timeValue =
        TimeValue.parseTimeValue(
            operateProperties.getArchiver().getIlmMinAgeForDeleteArchivedIndices(),
            "IndexLifeCycle " + INDEX_LIFECYCLE_NAME);
    LOGGER.info(
        "Create Index Lifecycle {} for min age of {} ",
        OPERATE_DELETE_ARCHIVED_INDICES,
        timeValue.getStringRep());
    final Map<String, Phase> phases = new HashMap<>();
    final Map<String, LifecycleAction> deleteActions =
        Collections.singletonMap(DeleteAction.NAME, new DeleteAction());
    phases.put(DELETE_PHASE, new Phase(DELETE_PHASE, timeValue, deleteActions));

    final LifecyclePolicy policy = new LifecyclePolicy(OPERATE_DELETE_ARCHIVED_INDICES, phases);
    final PutLifecyclePolicyRequest request = new PutLifecyclePolicyRequest(policy);
    retryElasticsearchClient.putLifeCyclePolicy(request);
  }

  private void createIndices() {
    indexDescriptors.forEach(this::createIndex);
  }

  private void createTemplates() {
    templateDescriptors.forEach(this::createTemplate);
  }

  private void createIndex(final IndexDescriptor indexDescriptor) {
    final String indexFilename =
        String.format("/schema/create/index/operate-%s.json", indexDescriptor.getIndexName());
    final Map<String, Object> indexDescription = readJSONFileToMap(indexFilename);
    createIndex(
        new CreateIndexRequest(indexDescriptor.getFullQualifiedName())
            .source(indexDescription)
            .aliases(Set.of(new Alias(indexDescriptor.getAlias()).writeIndex(false)))
            .settings(getIndexSettings()),
        indexDescriptor.getFullQualifiedName());
  }

  private void createTemplate(final TemplateDescriptor templateDescriptor) {
    final Template template = getTemplateFrom(templateDescriptor);
    final ComposableIndexTemplate composableTemplate =
        new ComposableIndexTemplate.Builder()
            .indexPatterns(List.of(templateDescriptor.getIndexPattern()))
            .template(template)
            .componentTemplates(List.of(settingsTemplateName()))
            .build();
    putIndexTemplate(
        new PutComposableIndexTemplateRequest()
            .name(templateDescriptor.getTemplateName())
            .indexTemplate(composableTemplate));
    // This is necessary, otherwise operate won't find indexes at startup
    final String indexName = templateDescriptor.getFullQualifiedName();
    createIndex(new CreateIndexRequest(indexName), indexName);
  }

  private Template getTemplateFrom(final TemplateDescriptor templateDescriptor) {
    final String templateFilename =
        String.format("/schema/create/template/operate-%s.json", templateDescriptor.getIndexName());
    // Easiest way to create Template from json file: create 'old' request ang retrieve needed info
    final Map<String, Object> templateConfig = readJSONFileToMap(templateFilename);
    final PutIndexTemplateRequest ptr =
        new PutIndexTemplateRequest(templateDescriptor.getTemplateName()).source(templateConfig);
    try {
      final Map<String, AliasMetadata> aliases =
          Map.of(
              templateDescriptor.getAlias(),
              AliasMetadata.builder(templateDescriptor.getAlias()).build());
      return new Template(ptr.settings(), new CompressedXContent(ptr.mappings()), aliases);
    } catch (IOException e) {
      throw new OperateRuntimeException(
          String.format("Error in reading mappings for %s ", templateDescriptor.getTemplateName()),
          e);
    }
  }

  private Map<String, Object> readJSONFileToMap(final String filename) {
    final Map<String, Object> result;
    try (InputStream inputStream =
        OperateElasticsearchManager.class.getResourceAsStream(filename)) {
      if (inputStream != null) {
        result = XContentHelper.convertToMap(XContentType.JSON.xContent(), inputStream, true);
      } else {
        throw new OperateRuntimeException("Failed to find " + filename + " in classpath ");
      }
    } catch (IOException e) {
      throw new OperateRuntimeException("Failed to load file " + filename + " from classpath ", e);
    }
    return result;
  }

  private void createIndex(final CreateIndexRequest createIndexRequest, String indexName) {
    final boolean created = retryElasticsearchClient.createIndex(createIndexRequest);
    if (created) {
      LOGGER.info("Index [{}] was successfully created", indexName);
    } else {
      LOGGER.info("Index [{}] was NOT created", indexName);
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

  @Override
  public String getIndexPrefix() {
    return operateProperties.getElasticsearch().getIndexPrefix();
  }
}
