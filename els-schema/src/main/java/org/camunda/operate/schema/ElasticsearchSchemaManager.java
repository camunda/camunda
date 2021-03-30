/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

package org.camunda.operate.schema;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import org.camunda.operate.es.RetryElasticsearchClient;
import org.camunda.operate.exceptions.OperateRuntimeException;
import org.camunda.operate.property.OperateElasticsearchProperties;
import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.schema.indices.AbstractIndexDescriptor;
import org.camunda.operate.schema.indices.IndexDescriptor;
import org.camunda.operate.schema.templates.TemplateDescriptor;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.PutIndexTemplateRequest;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentHelper;
import org.elasticsearch.common.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
  private List<AbstractIndexDescriptor> indexDescriptors;

  @Autowired
  private List<TemplateDescriptor> templateDescriptors;

  @Autowired
  protected RetryElasticsearchClient retryElasticsearchClient;

  @Autowired
  protected OperateProperties operateProperties;

  public void createSchema() {
     createDefaults();
     createTemplates();
     createIndices();
  }

  private void createDefaults() {
    final OperateElasticsearchProperties elsConfig = operateProperties.getElasticsearch();
    final String settingsTemplate = String.format("%s_template", elsConfig.getIndexPrefix());

    final List<String> patterns = List.of(String.format("%s-*", elsConfig.getIndexPrefix()));
    logger.info("Create default settings from '{}' with {} shards and {} replicas per index.", settingsTemplate,
        elsConfig.getNumberOfShards(),
        elsConfig.getNumberOfReplicas());

    final PutIndexTemplateRequest putIndexTemplateRequest = new PutIndexTemplateRequest(settingsTemplate)
        .order(TEMPLATE_ORDER) // order of template applying
        .patterns(patterns)
        .settings(Settings.builder()
            .put(NUMBER_OF_SHARDS, elsConfig.getNumberOfShards())
            .put(NUMBER_OF_REPLICAS, elsConfig.getNumberOfReplicas()).build());

    putIndexTemplate(putIndexTemplateRequest, settingsTemplate);
  }

  private void createIndices() {
    indexDescriptors.forEach(this::createIndex);
  }

  private void createTemplates() {
    templateDescriptors.forEach(this::createTemplate);
  }

  private void createIndex(final IndexDescriptor indexDescriptor) {
    final String indexFilename = String.format("/schema/create/index/operate-%s.json", indexDescriptor.getIndexName());
    final Map<String, Object> indexDescription = prepareCreateIndex(indexFilename, indexDescriptor.getAlias());
    createIndex(new CreateIndexRequest(indexDescriptor.getFullQualifiedName()).source(indexDescription), indexDescriptor.getFullQualifiedName());
  }

  private void createTemplate(final TemplateDescriptor templateDescriptor) {
    final String templateFilename = String.format("/schema/create/template/operate-%s.json", templateDescriptor.getIndexName());
    final Map<String, Object> template = readJSONFileToMap(templateFilename);
    // Adjust prefixes and aliases in case of other configured indexNames, e.g. non-default prefix
    template.put(INDEX_PATTERNS, Collections.singletonList(templateDescriptor.getIndexPattern()));
    template.put(ALIASES, Collections.singletonMap(templateDescriptor.getAlias(), Collections.emptyMap()));
    putIndexTemplate(new PutIndexTemplateRequest(templateDescriptor.getTemplateName()).source(template), templateDescriptor.getTemplateName());
    // This is necessary, otherwise operate won't find indexes at startup
    String indexName = templateDescriptor.getFullQualifiedName();
    createIndex(new CreateIndexRequest(indexName), indexName);
  }

  private Map<String, Object> prepareCreateIndex(String fileName, String alias) {
    final Map<String, Object> indexDescription = readJSONFileToMap(fileName);
    // Adjust aliases in case of other configured indexNames, e.g. non-default prefix
    indexDescription.put(ALIASES, Collections.singletonMap(alias, Collections.emptyMap()));
    return indexDescription;
  }

  private Map<String, Object> readJSONFileToMap(final String filename) {
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

  private void createIndex(final CreateIndexRequest createIndexRequest, String indexName) {
    boolean created = retryElasticsearchClient.createIndex(createIndexRequest);
    if (created) {
      logger.debug("Index [{}] was successfully created", indexName);
    } else {
      logger.debug("Index [{}] was NOT created", indexName);
    }
  }

  private void putIndexTemplate(final PutIndexTemplateRequest putIndexTemplateRequest, String templateName) {
    boolean created = retryElasticsearchClient.createTemplate(putIndexTemplateRequest);
    if (created) {
      logger.debug("Template [{}] was successfully created", templateName);
    } else {
      logger.debug("Template [{}] was NOT created", templateName);
    }
  }

}
