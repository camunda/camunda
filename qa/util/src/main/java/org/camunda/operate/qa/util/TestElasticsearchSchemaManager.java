/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.qa.util;

import java.io.IOException;

import javax.annotation.PostConstruct;
import org.camunda.operate.exceptions.OperateRuntimeException;
import org.camunda.operate.schema.ElasticsearchSchemaManager;
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
public class TestElasticsearchSchemaManager extends ElasticsearchSchemaManager{

  private static final Logger logger = LoggerFactory.getLogger(TestElasticsearchSchemaManager.class);

  @PostConstruct
  @Override
  public boolean initializeSchema() {
    //do nothing
    logger.info("TestSchemaManager: no schema will be created");
    return true;
  }

  public void deleteSchema() {
    try {
      String prefix = this.operateProperties.getElasticsearch().getIndexPrefix();
      logger.info("Removing indices " + prefix + "*");
      this.esClient.indices().delete(new DeleteIndexRequest(prefix + "*"), RequestOptions.DEFAULT);
      this.esClient.indices().deleteTemplate(new DeleteIndexTemplateRequest(prefix + "*"), RequestOptions.DEFAULT);
    } catch (ElasticsearchStatusException | IOException e) {
      throw new OperateRuntimeException("Failed to delete indices ", e);
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
