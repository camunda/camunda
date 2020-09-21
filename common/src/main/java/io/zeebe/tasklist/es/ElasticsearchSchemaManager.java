/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.es;

import io.zeebe.tasklist.es.schema.indices.IndexDescriptor;
import io.zeebe.tasklist.es.schema.templates.TemplateDescriptor;
import io.zeebe.tasklist.exceptions.TasklistRuntimeException;
import io.zeebe.tasklist.management.ElsIndicesCheck;
import io.zeebe.tasklist.property.TasklistProperties;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.annotation.PostConstruct;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.client.indices.IndexTemplatesExistRequest;
import org.elasticsearch.client.indices.PutIndexTemplateRequest;
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

  private static final Logger LOGGER = LoggerFactory.getLogger(ElasticsearchSchemaManager.class);
  @Autowired protected RestHighLevelClient esClient;
  @Autowired protected TasklistProperties tasklistProperties;
  @Autowired private List<IndexDescriptor> indexDescriptors;
  @Autowired private List<TemplateDescriptor> templateDescriptors;

  @Autowired private ElsIndicesCheck elsIndicesCheck;

  @PostConstruct
  public void initializeSchema() {
    if (tasklistProperties.getElasticsearch().isCreateSchema() && !schemaAlreadyExists()) {
      LOGGER.info(
          "Elasticsearch schema is empty or not complete. Templates and indices will be created.");
      createSchema();
    } else {
      LOGGER.info(
          "Elasticsearch schema won't be created, it either already exist, or schema creation is disabled in configuration.");
    }
  }

  private boolean schemaAlreadyExists() {
    return elsIndicesCheck.indicesArePresent();
  }

  public void createSchema() {
    createTemplates();
    createIndices();
  }

  public void createIndices() {
    indexDescriptors.forEach(this::createIndex);
  }

  public void createTemplates() {
    templateDescriptors.forEach(this::createTemplate);
  }

  protected void createIndex(IndexDescriptor indexDescriptor) {
    if (!indexExists(indexDescriptor.getIndexName())) {
      final Map<String, Object> indexDescription = readJSONFileToMap(indexDescriptor.getFileName());
      // Adjust aliases in case of other configured indexNames, e.g. non-default prefix
      indexDescription.put(
          "aliases", Collections.singletonMap(indexDescriptor.getAlias(), Collections.EMPTY_MAP));

      createIndex(
          new CreateIndexRequest(indexDescriptor.getIndexName()).source(indexDescription),
          indexDescriptor.getIndexName());
    }
  }

  protected void createTemplate(TemplateDescriptor templateDescriptor) {
    if (!templateExists(templateDescriptor.getTemplateName())) {
      final Map<String, Object> template = readJSONFileToMap(templateDescriptor.getFileName());

      // Adjust prefixes and aliases in case of other configured indexNames, e.g. non-default prefix
      template.put(
          "index_patterns", Collections.singletonList(templateDescriptor.getIndexPattern()));
      template.put(
          "aliases",
          Collections.singletonMap(templateDescriptor.getAlias(), Collections.EMPTY_MAP));

      putIndexTemplate(
          new PutIndexTemplateRequest(templateDescriptor.getTemplateName()).source(template),
          templateDescriptor.getTemplateName());
    }
    if (!indexExists(templateDescriptor.getMainIndexName())) {
      // This is necessary, otherwise we won't find indexes at startup
      createIndex(
          new CreateIndexRequest(templateDescriptor.getMainIndexName()),
          templateDescriptor.getMainIndexName());
    }
  }

  protected Map<String, Object> readJSONFileToMap(final String filename) {
    final Map<String, Object> result;
    try (InputStream inputStream = ElasticsearchSchemaManager.class.getResourceAsStream(filename)) {
      if (inputStream != null) {
        result = XContentHelper.convertToMap(XContentType.JSON.xContent(), inputStream, true);
      } else {
        throw new TasklistRuntimeException("Failed to find " + filename + " in classpath ");
      }
    } catch (IOException e) {
      throw new TasklistRuntimeException("Failed to load file " + filename + " from classpath ", e);
    }
    return result;
  }

  protected void createIndex(final CreateIndexRequest createIndexRequest, String indexName) {
    try {
      final boolean acknowledged =
          esClient.indices().create(createIndexRequest, RequestOptions.DEFAULT).isAcknowledged();
      if (acknowledged) {
        LOGGER.info("Index [{}] was successfully created", indexName);
      } else {
        LOGGER.info("Index [{}] wasn't created", indexName);
      }
    } catch (IOException e) {
      throw new TasklistRuntimeException("Failed to create index " + indexName, e);
    }
  }

  protected void putIndexTemplate(
      final PutIndexTemplateRequest putIndexTemplateRequest, String templateName) {
    try {
      final boolean acknowledged =
          esClient
              .indices()
              .putTemplate(putIndexTemplateRequest, RequestOptions.DEFAULT)
              .isAcknowledged();
      if (acknowledged) {
        LOGGER.info("Template [{}] was successfully created", templateName);
      } else {
        LOGGER.info("Template [{}] wasn't created", templateName);
      }
    } catch (IOException e) {
      throw new TasklistRuntimeException("Failed to put index template " + templateName, e);
    }
  }

  protected boolean templateExists(final String templateName) {
    try {
      return esClient
          .indices()
          .existsTemplate(new IndexTemplatesExistRequest(templateName), RequestOptions.DEFAULT);
    } catch (IOException e) {
      throw new TasklistRuntimeException(
          "Failed to check existence of template " + templateName, e);
    }
  }

  protected boolean indexExists(final String indexName) {
    try {
      return esClient.indices().exists(new GetIndexRequest(indexName), RequestOptions.DEFAULT);
    } catch (IOException e) {
      throw new TasklistRuntimeException("Failed to check existence of index " + indexName, e);
    }
  }
}
