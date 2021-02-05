/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.schema;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;
import org.camunda.operate.management.ElsIndicesCheck;
import org.camunda.operate.property.MigrationProperties;
import org.camunda.operate.exceptions.OperateRuntimeException;
import org.camunda.operate.property.OperateElasticsearchProperties;
import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.schema.indices.IndexDescriptor;
import org.camunda.operate.schema.indices.MigrationRepositoryIndex;
import org.camunda.operate.schema.migration.Migrator;
import org.camunda.operate.schema.templates.TemplateDescriptor;
import org.camunda.operate.es.RetryElasticsearchClient;
import org.elasticsearch.client.indices.*;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentHelper;
import org.elasticsearch.common.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component("schemaManager")
@Profile("!test")
public class ElasticsearchSchemaManager {

  private static final Logger logger = LoggerFactory.getLogger(ElasticsearchSchemaManager.class);

  private static final String NUMBER_OF_SHARDS = "number_of_shards";
  private static final String NUMBER_OF_REPLICAS = "number_of_replicas";
  private static final String ALIASES = "aliases";
  private static final String INDEX_PATTERNS = "index_patterns";
  public static final int TEMPLATE_ORDER = 15;

  @Autowired
  private List<IndexDescriptor> indexDescriptors;

  @Autowired
  private List<TemplateDescriptor> templateDescriptors;

  @Autowired
  private ElsIndicesCheck elsIndicesCheck;

  @Autowired
  protected RetryElasticsearchClient retryElasticsearchClient;

  @Autowired
  protected OperateProperties operateProperties;

  @Autowired
  private MigrationProperties migrationProperties;

  @Autowired
  private BeanFactory beanFactory;

  @PostConstruct
  public void initializeSchema() {
    if (operateProperties.getElasticsearch().isCreateSchema() && !schemaAlreadyExists()) {
      logger.info("Elasticsearch schema is empty or not complete. Indices will be created.");
      createSchema();
    } else {
      logger.info("Elasticsearch schema won't be created, it either already exist, or schema creation is disabled in configuration.");
    }
    if (migrationProperties.isMigrationEnabled()) {
      final Migrator migrator = beanFactory.getBean(Migrator.class);
      migrator.migrate();
    }
  }

  private boolean schemaAlreadyExists() {
    return elsIndicesCheck.indicesArePresent();
  }

  public void createSchema() {
     createDefaults();
     createTemplates();
     createIndices();
  }

  public void createDefaults() {
    final OperateElasticsearchProperties elsConfig = operateProperties.getElasticsearch();
    final String schemaVersion = operateProperties.getSchemaVersion();
    final String settingsTemplate = String.format("%s-%s_template", elsConfig.getIndexPrefix(), schemaVersion);
    final List<String> patterns = List.of(
        String.format("%s-*-%s_*", elsConfig.getIndexPrefix(), schemaVersion),
        String.format("%s-%s", elsConfig.getIndexPrefix(), MigrationRepositoryIndex.INDEX_NAME));
      logger.info(
          "Create default settings from '{}' with {} shards and {} replicas per index.",
          settingsTemplate,
          elsConfig.getNumberOfShards(),
          elsConfig.getNumberOfReplicas());
      final PutIndexTemplateRequest putIndexTemplateRequest =
          new PutIndexTemplateRequest(settingsTemplate)
              .order(TEMPLATE_ORDER) // order of template applying
              .patterns(patterns)
              .settings(
                  Settings.builder()
                      .put(NUMBER_OF_SHARDS, elsConfig.getNumberOfShards())
                      .put(NUMBER_OF_REPLICAS, elsConfig.getNumberOfReplicas())
                      .build());
      putIndexTemplate(putIndexTemplateRequest, settingsTemplate);
  }

  public void createIndices() {
    indexDescriptors.forEach(this::createIndex);
  }

  public void createTemplates() {
    templateDescriptors.forEach(this::createTemplate);
  }

  protected void createIndex(final IndexDescriptor indexDescriptor) {
    String indexName = indexDescriptor.getIndexName();
    final Map<String, Object> indexDescription = prepareCreateIndex(indexName,indexDescriptor.getFileName(),indexDescriptor.getAlias());
    createIndex(new CreateIndexRequest(indexDescriptor.getIndexName()).source(indexDescription), indexDescriptor.getIndexName());
  }

  protected void createTemplate(final TemplateDescriptor templateDescriptor) {
    final Map<String, Object> template = readJSONFileToMap(templateDescriptor.getFileName());
    // Adjust prefixes and aliases in case of other configured indexNames, e.g. non-default prefix
    template.put(INDEX_PATTERNS, Collections.singletonList(templateDescriptor.getIndexPattern()));
    template.put(ALIASES, Collections.singletonMap(templateDescriptor.getAlias(), Collections.emptyMap()));
    putIndexTemplate(new PutIndexTemplateRequest(templateDescriptor.getTemplateName()).source(template),templateDescriptor.getTemplateName());
    // This is necessary, otherwise operate won't find indexes at startup
    String indexName = templateDescriptor.getMainIndexName();
    final Map<String, Object> indexDescription = prepareCreateIndex(indexName,templateDescriptor.getFileName(),templateDescriptor.getAlias());
    createIndex(new CreateIndexRequest(indexName).source(indexDescription),indexName);
  }

  protected Map<String, Object> prepareCreateIndex(String indexName,String fileName,String alias){
    final Map<String, Object> indexDescription = readJSONFileToMap(fileName);
    // Adjust aliases in case of other configured indexNames, e.g. non-default prefix
    indexDescription.put(ALIASES, Collections.singletonMap(alias, Collections.emptyMap()));
    return indexDescription;
  }

  protected Map<String, Object> readJSONFileToMap(final String filename) {
    final Map<String, Object> result;
    try (InputStream inputStream = ElasticsearchSchemaManager.class.getResourceAsStream(filename)) {
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

  protected void createIndex(final CreateIndexRequest createIndexRequest, String indexName) {
    boolean created = retryElasticsearchClient.createIndex(createIndexRequest);
    if (created) {
      logger.debug("Index [{}] was successfully created", indexName);
    } else {
      logger.debug("Index [{}] was NOT created", indexName);
    }
  }

  protected void putIndexTemplate(final PutIndexTemplateRequest putIndexTemplateRequest, String templateName) {
    boolean created = retryElasticsearchClient.createTemplate(putIndexTemplateRequest);
    if (created) {
      logger.debug("Template [{}] was successfully created", templateName);
    } else {
      logger.debug("Template [{}] was NOT created", templateName);
    }
  }

}

