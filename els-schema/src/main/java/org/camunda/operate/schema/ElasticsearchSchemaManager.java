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
import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.schema.indices.IndexDescriptor;
import org.camunda.operate.schema.migration.Migrator;
import org.camunda.operate.schema.templates.TemplateDescriptor;
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
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component("schemaManager")
@Profile("!test")
public class ElasticsearchSchemaManager {

  private static final Logger logger = LoggerFactory.getLogger(ElasticsearchSchemaManager.class);

  @Autowired
  Environment env;
  
  @Autowired
  private List<IndexDescriptor> indexDescriptors;

  @Autowired
  private List<TemplateDescriptor> templateDescriptors;

  @Autowired
  private ElsIndicesCheck elsIndicesCheck;

  @Autowired
  private Migrator migrator;
  
  @Autowired
  protected RestHighLevelClient esClient;

  @Autowired
  protected OperateProperties operateProperties;

  @Autowired
  private MigrationProperties migrationProperties;
    
  @PostConstruct
  public void initializeSchema() {
    if (operateProperties.getElasticsearch().isCreateSchema() && !schemaAlreadyExists()) {
      logger.info("Elasticsearch schema is empty or not complete. Indices will be created.");
      createSchema();
    } else {
      logger.info("Elasticsearch schema won't be created, it either already exist, or schema creation is disabled in configuration.");
    }
    if (migrationProperties.isMigrationEnabled()) {
      migrator.migrate();
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
  
  protected void createIndex(final IndexDescriptor indexDescriptor) {
    if (!indexExists(indexDescriptor.getIndexName())) {
      final Map<String, Object> indexDescription = readJSONFileToMap(indexDescriptor.getFileName());
      // Adjust aliases in case of other configured indexNames, e.g. non-default prefix
      indexDescription.put("aliases", Collections.singletonMap(indexDescriptor.getAlias(), Collections.emptyMap()));
    
      createIndex(new CreateIndexRequest(indexDescriptor.getIndexName()).source(indexDescription), indexDescriptor.getIndexName());
    }
  }
  
  protected void createTemplate(final TemplateDescriptor templateDescriptor) {
    if (!templateExists(templateDescriptor.getTemplateName())) {
      final Map<String, Object> template = readJSONFileToMap(templateDescriptor.getFileName());
      // Adjust prefixes and aliases in case of other configured indexNames, e.g. non-default prefix
      template.put("index_patterns", Collections.singletonList(templateDescriptor.getIndexPattern()));
      template.put("aliases", Collections.singletonMap(templateDescriptor.getAlias(), Collections.emptyMap()));

      putIndexTemplate(new PutIndexTemplateRequest(templateDescriptor.getTemplateName()).source(template),templateDescriptor.getTemplateName());
    } 
    if (!indexExists(templateDescriptor.getMainIndexName())) {    
      // This is necessary, otherwise operate won't find indexes at startup
      createIndex(new CreateIndexRequest(templateDescriptor.getMainIndexName()), templateDescriptor.getMainIndexName());
    }
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
  
  protected boolean createIndex(final CreateIndexRequest createIndexRequest, String indexName) {
    try {
        boolean acknowledged = esClient.indices().create(createIndexRequest, RequestOptions.DEFAULT).isAcknowledged();
        if (acknowledged) {
          logger.debug("Index [{}] was successfully created", indexName);
        }
        return acknowledged;
    } catch (IOException e) {
      throw new OperateRuntimeException("Failed to create index " + indexName, e);
    }
  }
  
  protected boolean putIndexTemplate(final PutIndexTemplateRequest putIndexTemplateRequest, String templateName) {
    try {
      boolean acknowledged = esClient.indices().putTemplate(putIndexTemplateRequest, RequestOptions.DEFAULT).isAcknowledged();
      if (acknowledged) {
        logger.debug("Template [{}] was successfully created", templateName);
      }
      return acknowledged;
    } catch (IOException e) {
      throw new OperateRuntimeException("Failed to put index template " + templateName , e);
    }
  }
  
  protected boolean templateExists(final String templateName) {
    try {
      return esClient.indices().existsTemplate(new IndexTemplatesExistRequest(templateName), RequestOptions.DEFAULT);
    }catch (IOException e) {
      throw new OperateRuntimeException("Failed to check existence of template " + templateName, e);
    }
  }
  
  protected boolean indexExists(final String indexName) {
    try {
      return esClient.indices().exists(new GetIndexRequest(indexName), RequestOptions.DEFAULT);
    }catch (IOException e) {
      throw new OperateRuntimeException("Failed to check existence of index " + indexName, e);
    }
  }

}

