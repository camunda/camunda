/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.es;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.camunda.operate.es.schema.indices.IndexCreator;
import org.camunda.operate.es.schema.templates.TemplateCreator;
import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.util.ElasticsearchUtil;
import org.elasticsearch.ResourceAlreadyExistsException;
import org.elasticsearch.action.admin.indices.alias.Alias;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequestBuilder;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

@Component
public class ElasticsearchSchemaManager {

  private static final Logger logger = LoggerFactory.getLogger(ElasticsearchSchemaManager.class);

  @Autowired
  private List<IndexCreator> indexCreators;

  @Autowired
  private List<TemplateCreator> templateCreators;

  @Autowired
  private TransportClient esClient;

  @Autowired
  private OperateProperties operateProperties;

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
        templateSettings = buildSettings();
      } catch (IOException e) {
        logger.error(String.format("Could not create settings for template [%s]", templateCreator.getTemplateName()), e);
      }
      esClient.admin().indices().preparePutTemplate(templateCreator.getTemplateName())
        .setPatterns(Arrays.asList(templateCreator.getIndexPattern()))
        .addMapping(ElasticsearchUtil.ES_INDEX_TYPE, templateCreator.getSource())
        .addAlias(new Alias(templateCreator.getAlias()))
        .setSettings(templateSettings)
        .setOrder(operateProperties.getElasticsearch().getTemplateOrder())
        .get();

      //create main index
      final Alias alias = new Alias(templateCreator.getAlias());
      alias.writeIndex(true);
      esClient.admin().indices().prepareCreate(templateCreator.getMainIndexName())
        .addAlias(alias).get();

    } catch (IOException e) {
      String message = String.format("Could not add mapping to the template [%s]", templateCreator.getTemplateName());
      logger.error(message, e);
    } catch (ResourceAlreadyExistsException e) {
      logger.warn("Template [{}] already exists", templateCreator.getTemplateName());
    }
    logger.debug("Template [{}] was successfully created", templateCreator.getTemplateName());
  }

  private void createIndex(IndexCreator mapping) {
    try {
      Settings indexSettings = null;
      try {
        indexSettings = buildSettings();
      } catch (IOException e) {
        logger.error(String.format("Could not create settings for index [%s]", mapping.getIndexName()), e);
      }
      CreateIndexRequestBuilder createIndexRequestBuilder =
        esClient.admin().indices().prepareCreate(mapping.getIndexName())
          .addAlias(new Alias(mapping.getAlias()))
          .setSettings(indexSettings);

      createIndexRequestBuilder = createIndexRequestBuilder.addMapping(ElasticsearchUtil.ES_INDEX_TYPE, mapping.getSource());

      createIndexRequestBuilder.get();

    } catch (IOException e) {
      String message = String.format("Could not add mapping to the index [%s]", mapping.getIndexName());
      logger.error(message, e);
    } catch (ResourceAlreadyExistsException e) {
      logger.warn("Index for type [{}] already exists", mapping.getIndexName());
    }

    esClient.admin().indices().prepareRefresh().get();
    logger.debug("Index [{}] was successfully created", mapping.getIndexName());
  }

  /**
   * Checks in Elasticsearch, if the schema already exists. For this it searches for one of used aliases.
   * @return true is Elasticsearch schema already exists, false otherwise
   */
  private boolean schemaAlreadyExists() {
    IndicesExistsResponse response = esClient.admin().indices().prepareExists(indexCreators.get(0).getAlias()).get();
    return response.isExists();
  }

  //TODO copy-pasted from Optimize, we need to check if this settings suit our needs
  private Settings buildSettings() throws IOException {
    return Settings.builder()
      .put("index.mapper.dynamic", false)
      .loadFromSource(Strings.toString(jsonBuilder()
      .startObject()
        .field("index.mapper.dynamic", false)
        .field("refresh_interval", "2s")
        .field("number_of_replicas", "0")
        .field("number_of_shards", "1")
        .startObject("analysis")
          .startObject("analyzer")
            .startObject("case_sensitive")
              .field("type", "custom")
              .field("tokenizer", "whitespace")
              .field("filter", new String[]{"standard"})
            .endObject()
        . endObject()
        .endObject()
      .endObject()), XContentType.JSON)
    .build();
  }

}
