/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.qa.util.cluster;

import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.schema.elasticsearch.ElasticsearchSchemaManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component("schemaManager")
@Conditional(ElasticsearchCondition.class)
@Profile("test")
public class TestOperateElasticsearchSchemaManager extends ElasticsearchSchemaManager {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(TestOperateElasticsearchSchemaManager.class);

  public void deleteSchema() {
    final String prefix = operateProperties.getElasticsearch().getIndexPrefix();
    LOGGER.info("Removing indices {}*", prefix);
    retryElasticsearchClient.deleteIndicesFor(prefix + "*");
    LOGGER.info("Removing templates {}*", prefix);
    retryElasticsearchClient.deleteTemplatesFor(prefix + "*");
  }

  public void setCreateSchema(final boolean createSchema) {
    operateProperties.getElasticsearch().setCreateSchema(createSchema);
  }

  public void setIndexPrefix(final String indexPrefix) {
    operateProperties.getElasticsearch().setIndexPrefix(indexPrefix);
  }

  public void setDefaultIndexPrefix() {
    operateProperties.getElasticsearch().setDefaultIndexPrefix();
  }
}
