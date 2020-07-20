/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.qa.util;

import io.zeebe.tasklist.es.ElasticsearchSchemaManager;
import io.zeebe.tasklist.exceptions.TasklistRuntimeException;
import java.io.IOException;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.admin.indices.template.delete.DeleteIndexTemplateRequest;
import org.elasticsearch.client.RequestOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component("schemaManager")
@Profile("test")
public class TestElasticsearchSchemaManager extends ElasticsearchSchemaManager {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(TestElasticsearchSchemaManager.class);

  @Override
  public void initializeSchema() {
    // do nothing
    LOGGER.info("INIT: no schema will be created");
  }

  public void deleteSchema() {
    try {
      final String prefix = tasklistProperties.getElasticsearch().getIndexPrefix();
      LOGGER.info("Removing indices " + prefix + "*");
      esClient.indices().delete(new DeleteIndexRequest(prefix + "*"), RequestOptions.DEFAULT);
      esClient
          .indices()
          .deleteTemplate(new DeleteIndexTemplateRequest(prefix + "*"), RequestOptions.DEFAULT);
    } catch (ElasticsearchStatusException | IOException e) {
      throw new TasklistRuntimeException("Failed to delete indices ", e);
    }
  }

  public void deleteSchemaQuietly() {
    try {
      deleteSchema();
    } catch (Throwable t) {
      LOGGER.debug(t.getMessage());
    }
  }
}
