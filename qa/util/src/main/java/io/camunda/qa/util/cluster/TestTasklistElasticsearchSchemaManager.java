/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.qa.util.cluster;

import io.camunda.tasklist.data.conditionals.ElasticSearchCondition;
import io.camunda.tasklist.schema.v86.manager.ElasticsearchSchemaManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component("tasklistSchemaManager")
@Conditional(ElasticSearchCondition.class)
@Profile("test")
public class TestTasklistElasticsearchSchemaManager extends ElasticsearchSchemaManager {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(TestTasklistElasticsearchSchemaManager.class);

  public void deleteSchema() {
    final String prefix = tasklistProperties.getElasticsearch().getIndexPrefix();
    LOGGER.info("Removing indices {}*", prefix);
    retryElasticsearchClient.deleteIndicesFor(prefix + "*");
    LOGGER.info("Removing templates {}*", prefix);
    retryElasticsearchClient.deleteTemplatesFor(prefix + "*");
  }

  public void setCreateSchema(final boolean createSchema) {
    tasklistProperties.getElasticsearch().setCreateSchema(createSchema);
  }

  public void setIndexPrefix(final String indexPrefix) {
    tasklistProperties.getElasticsearch().setIndexPrefix(indexPrefix);
  }

  public void setDefaultIndexPrefix() {
    tasklistProperties.getElasticsearch().setDefaultIndexPrefix();
  }
}
