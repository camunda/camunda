/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.es;

import static org.camunda.operate.util.CollectionUtil.map;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;

import org.camunda.operate.es.schema.indices.IndexDescriptor;
import org.camunda.operate.es.schema.templates.TemplateDescriptor;
import org.camunda.operate.exceptions.OperateRuntimeException;
import org.camunda.operate.property.OperateElasticsearchProperties;
import org.camunda.operate.property.OperateProperties;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.client.indices.CreateIndexRequest;
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

  private static final Logger logger = LoggerFactory.getLogger(ElasticsearchSchemaManager.class);

  @Autowired
  private List<IndexDescriptor> indexCreators;

  @Autowired
  private List<TemplateDescriptor> templateCreators;

  @Autowired
  private RestHighLevelClient esClient;

  @Autowired
  private OperateProperties operateProperties;
    
  @PostConstruct
  public boolean initializeSchema() {
    if (!schemaAlreadyExists()) {
      logger.info("Elasticsearch schema is empty. Indices will be created.");
      return createSchema();
    } else {
      logger.info("Elasticsearch schema already exists");
      return false;
    }
  }
  
  public boolean createSchema() {
     return createTemplates() && createIndices();
  }

  public boolean createIndices() {
    return !map(indexCreators,this::createIndex).contains(false);
  }

  public boolean createTemplates() {
    return !map(templateCreators,this::createTemplate).contains(false);
  }
  
  protected boolean createIndex(IndexDescriptor indexCreator) {
    String indexName = indexCreator.getIndexName();
    String indexNameWithoutVersion = getResourceNameFor(indexName);
    return putIndex(indexName, "/create/index/" + indexNameWithoutVersion + ".json");
  }
  
  protected boolean createTemplate(TemplateDescriptor templateCreator) {
    String templateName = templateCreator.getTemplateName();
    String templateNameWithoutVersion = getResourceNameFor(templateName);
    String indexName = templateName.replace("template", "");
    return putIndexTemplate(templateName,indexName, "/create/template/" + templateNameWithoutVersion + ".json") 
        && createIndex(new CreateIndexRequest(indexName));
  }
  
  public boolean schemaAlreadyExists() {
    try {
      String indexName = indexCreators.get(0).getAlias();
      return esClient.indices().exists(new GetIndexRequest(indexName), RequestOptions.DEFAULT);
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while checking schema existence: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }
 
  protected String getResourceNameFor(String name) {
    return name
            .replace(operateProperties.getElasticsearch().getIndexPrefix(),OperateElasticsearchProperties.DEFAULT_INDEX_PREFIX)
            .replace("-"+OperateProperties.getSchemaVersion(), "")
            .replace("template", "")
            .replace("_", "");
  }
  
  public boolean putIndexTemplate(final String templateName,String indexName,final String filename) {
    final Map<String, Object> template = readJSONFileToMap(filename);
    // update prefix in template in case it was changed in configuration
    template.put("index_patterns", Collections.singletonList(indexName + "*"));
    // update alias in template in case it was changed in configuration
    template.put("aliases", Collections.singletonMap(indexName+"alias", Collections.EMPTY_MAP));

    final PutIndexTemplateRequest request =
        new PutIndexTemplateRequest(templateName).source(template);

    return putIndexTemplate(request);
  }
  
  public boolean putIndex(final String indexName, final String filename) {
    final Map<String, Object> index = readJSONFileToMap(getResourceNameFor(filename));
    index.put("aliases", Collections.singletonMap(indexName+"alias", Collections.EMPTY_MAP));
    final CreateIndexRequest request = new CreateIndexRequest(indexName).source(index);
    return createIndex(request);
  }

  private Map<String, Object> readJSONFileToMap(final String filename) {
    final Map<String, Object> schema;
    try (InputStream inputStream = ElasticsearchSchemaManager.class.getResourceAsStream(filename)) {
      if (inputStream != null) {
        schema = XContentHelper.convertToMap(XContentType.JSON.xContent(), inputStream, true);
      } else {
        throw new OperateRuntimeException(
            "Failed to find schema in classpath " + filename);
      }
    } catch (IOException e) {
      throw new OperateRuntimeException(
          "Failed to load schema from classpath " + filename, e);
    }
    return schema;
  }
  
  private boolean createIndex(final CreateIndexRequest createIndexRequest) {
    try {
      return esClient
          .indices()
          .create(createIndexRequest, RequestOptions.DEFAULT)
          .isAcknowledged();
    } catch (IOException e) {
      throw new OperateRuntimeException("Failed to create index ", e);
    }
  }
  
  
  private boolean putIndexTemplate(final PutIndexTemplateRequest putIndexTemplateRequest) {
    try {
      return esClient
          .indices()
          .putTemplate(putIndexTemplateRequest, RequestOptions.DEFAULT)
          .isAcknowledged();
    } catch (IOException e) {
      throw new OperateRuntimeException("Failed to put index template", e);
    }
  }
}

