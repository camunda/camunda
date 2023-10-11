/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.qa.util;

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
public class TestElasticsearchSchemaManager extends ElasticsearchSchemaManager implements TestSchemaManager {

  private static final Logger logger = LoggerFactory.getLogger(TestElasticsearchSchemaManager.class);

  @Override
  public void deleteSchema() {
    String prefix = this.operateProperties.getElasticsearch().getIndexPrefix();
    logger.info("Removing indices {}*", prefix);
    retryElasticsearchClient.deleteIndicesFor(prefix + "*");
    retryElasticsearchClient.deleteTemplatesFor(prefix + "*");
  }

  @Override
  public void deleteSchemaQuietly() {
    try {
      deleteSchema();
    } catch (Exception t) {
      logger.debug(t.getMessage());
    }
  }

  @Override
  public void setCreateSchema(boolean createSchema) {
    operateProperties.getElasticsearch().setCreateSchema(createSchema);
  }

  @Override
  public void setIndexPrefix(String indexPrefix) {
    operateProperties.getElasticsearch().setIndexPrefix(indexPrefix);
  }

  @Override
  public void setDefaultIndexPrefix() {
    operateProperties.getElasticsearch().setDefaultIndexPrefix();
  }
}
