/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.es;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.camunda.operate.es.schema.indices.IndexCreator;
import org.camunda.operate.es.schema.templates.TemplateCreator;
import org.camunda.operate.exceptions.OperateRuntimeException;
import org.camunda.operate.property.OperateProperties;
import org.elasticsearch.ResourceAlreadyExistsException;
import org.elasticsearch.action.admin.indices.alias.Alias;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.client.indices.PutIndexTemplateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

@Component("schemaManager")
@Profile("!test")
public class ElasticsearchSchemaManager {

  private static final Logger logger = LoggerFactory.getLogger(ElasticsearchSchemaManager.class);

  @Autowired
  private List<IndexCreator> indexCreators;

  @Autowired
  private List<TemplateCreator> templateCreators;

  @Autowired
  private RestHighLevelClient esClient;

  @Autowired
  private OperateProperties operateProperties;

  @PostConstruct
  public boolean initializeSchema() {
    if (!schemaAlreadyExists()) {
      logger.info("Elasticsearch schema is empty. Indices will be created.");
      createIndices();
      createTemplates();
      return true;
    } else {
      logger.info("Elasticsearch schema already exists");
      return false;
    }
  }

  public void createIndices() {
    for (IndexCreator mapping : indexCreators) {
      createIndex(mapping);
    }
  }

  public void createTemplates() {
    for (TemplateCreator templateCreator: templateCreators) {
      createTemplate(templateCreator);
    }
  }

  private void createTemplate(TemplateCreator templateCreator) {
    try {
      Settings templateSettings = null;
      try {
        templateSettings = buildSettings(templateCreator.needsSeveralShards());
      } catch (IOException e) {
        logger.error(String.format("Could not create settings for template [%s]", templateCreator.getTemplateName()), e);
      }
      final PutIndexTemplateRequest request = new PutIndexTemplateRequest(templateCreator.getTemplateName())
        .patterns(Arrays.asList(templateCreator.getIndexPattern()))
        .mapping(templateCreator.getSource())
        .alias(new Alias(templateCreator.getAlias()))
        .settings(templateSettings)
        .order(operateProperties.getElasticsearch().getTemplateOrder());
      esClient.indices().putTemplate(request, RequestOptions.DEFAULT);

      //create main index
      final Alias alias = new Alias(templateCreator.getAlias());
      alias.writeIndex(true);
      final CreateIndexRequest createIndexRequest = new CreateIndexRequest(templateCreator.getMainIndexName())
        .alias(alias);
      esClient.indices().create(createIndexRequest, RequestOptions.DEFAULT);
      logger.debug("Template [{}] was successfully created", templateCreator.getTemplateName());
    } catch (IOException e) {
      String message = String.format("Could not add mapping to the template [%s]", templateCreator.getTemplateName());
      logger.error(message, e);
    } catch (ResourceAlreadyExistsException e) {
      logger.warn("Template [{}] already exists", templateCreator.getTemplateName());
    }

  }

  private void createIndex(IndexCreator mapping) {
    try {
      Settings indexSettings = null;
      try {
        indexSettings = buildSettings(mapping.needsSeveralShards());
      } catch (IOException e) {
        logger.error(String.format("Could not create settings for index [%s]", mapping.getIndexName()), e);
      }
      final CreateIndexRequest createIndexRequest = new CreateIndexRequest(mapping.getIndexName())
          .settings(indexSettings)
          .alias(new Alias(mapping.getAlias()))
          .mapping(mapping.getSource());
      esClient.indices().create(createIndexRequest, RequestOptions.DEFAULT);
      esClient.indices().refresh(new RefreshRequest(mapping.getIndexName()), RequestOptions.DEFAULT);
      logger.debug("Index [{}] was successfully created", mapping.getIndexName());
    } catch (IOException e) {
      String message = String.format("Could not add mapping to the index [%s]", mapping.getIndexName());
      logger.error(message, e);
    } catch (ResourceAlreadyExistsException e) {
      logger.warn("Index for type [{}] already exists", mapping.getIndexName());
    }

  }

  /**
   * Checks in Elasticsearch, if the schema already exists. For this it searches for one of used aliases.
   * @return true is Elasticsearch schema already exists, false otherwise
   */
  protected boolean schemaAlreadyExists() {
    try {
     return esClient.indices().exists(new GetIndexRequest(indexCreators.get(0).getAlias()), RequestOptions.DEFAULT);
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while checking schema existence: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  private Settings buildSettings(boolean needsSeveralShards) throws IOException {
    XContentBuilder xContentBuilder = jsonBuilder()
      .startObject()
        .field("refresh_interval", "2s")
        .field("number_of_replicas", operateProperties.getElasticsearch().getNumberOfReplicas());
          if (needsSeveralShards) { xContentBuilder = xContentBuilder
        .field("number_of_shards", operateProperties.getElasticsearch().getNumberOfShards());
          } else {  xContentBuilder = xContentBuilder
        .field("number_of_shards", 1);
          }
        xContentBuilder = xContentBuilder
        .startObject("analysis")
          // define a lowercase normalizer: https://www.elastic.co/guide/en/elasticsearch/reference/current/keyword.html
          .startObject("normalizer")
            .startObject("case_insensitive")
              .field("filter","lowercase")
            .endObject()
          .endObject()
        .endObject()
      .endObject();
    return Settings.builder().loadFromSource(Strings.toString(xContentBuilder), XContentType.JSON).build();
  }

}
