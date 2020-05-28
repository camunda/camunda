/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.qa.util;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import io.zeebe.tasklist.es.ElasticsearchSchemaManager;
import io.zeebe.tasklist.es.schema.indices.IndexDescriptor;
import io.zeebe.tasklist.es.schema.templates.TemplateDescriptor;
import io.zeebe.tasklist.exceptions.TasklistRuntimeException;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.admin.indices.template.delete.DeleteIndexTemplateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.PutIndexTemplateRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component("schemaManager")
@Profile("test")
public class TestElasticsearchSchemaManager extends ElasticsearchSchemaManager {

  private static final Logger logger = LoggerFactory.getLogger(TestElasticsearchSchemaManager.class);

  @Override
  public boolean initializeSchema() {
    //do nothing
    logger.info("INIT: no schema will be created");
    return true;
  }

  @Override
  protected boolean createIndex(IndexDescriptor indexDescriptor) {
    final Map<String, Object> indexDescription = readJSONFileToMap(indexDescriptor.getFileName());
    // Adjust aliases in case of other configured indexNames, e.g. non-default prefix
    indexDescription.put("aliases", Collections.singletonMap(indexDescriptor.getAlias(), Collections.EMPTY_MAP));
    addSettings(indexDescription);

    return createIndex(new CreateIndexRequest(indexDescriptor.getIndexName()).source(indexDescription), indexDescriptor.getIndexName());
  }

  @Override
  protected boolean createTemplate(TemplateDescriptor templateDescriptor) {
    final Map<String, Object> template = readJSONFileToMap(templateDescriptor.getFileName());

    // Adjust prefixes and aliases in case of other configured indexNames, e.g. non-default prefix
    template.put("index_patterns", Collections.singletonList(templateDescriptor.getIndexPattern()));
    template.put("aliases", Collections.singletonMap(templateDescriptor.getAlias(), Collections.EMPTY_MAP));
    addSettings(template);

    return putIndexTemplate(new PutIndexTemplateRequest(templateDescriptor.getTemplateName()).source(template),
        templateDescriptor.getTemplateName())
        // This is necessary, otherwise Tasklist won't find indexes at startup
        && createIndex(new CreateIndexRequest(templateDescriptor.getMainIndexName()), templateDescriptor.getMainIndexName());
  }

  private void addSettings(Map<String, Object> definition) {
    final Object settings = definition.get("settings");
    if (settings != null) {
      final Map<String, Object> settingsMap = (Map<String, Object>) settings;
      definition.put("settings", populateSettings(settingsMap));
    } else {
      definition.put("settings", populateSettings(null));
    }
  }

  private Map populateSettings(Map<String, Object> settings) {
    if (settings == null) {
      settings = new HashMap<>();
    }
    settings.put("number_of_shards", 1);
    settings.put("number_of_replicas", 0);
    return settings;
  }

  public void deleteSchema() {
    try {
      String prefix = tasklistProperties.getElasticsearch().getIndexPrefix();
      logger.info("Removing indices " + prefix + "*");
      esClient.indices().delete(new DeleteIndexRequest(prefix + "*"), RequestOptions.DEFAULT);
      esClient.indices().deleteTemplate(new DeleteIndexTemplateRequest(prefix + "*"), RequestOptions.DEFAULT);
    } catch (ElasticsearchStatusException | IOException e) {
      throw new TasklistRuntimeException("Failed to delete indices ", e);
    }
  }

  public void deleteSchemaQuietly() {
    try {
      deleteSchema();
    } catch (Throwable t) {
      logger.debug(t.getMessage());
    }
  }
}
