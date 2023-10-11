/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.qa.util;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.schema.opensearch.OpensearchSchemaManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component("schemaManager")
@Conditional(OpensearchCondition.class)
@Profile("test")
public class TestOpensearchSchemaManager extends OpensearchSchemaManager implements  TestSchemaManager {

  private static final Logger logger = LoggerFactory.getLogger(TestOpensearchSchemaManager.class);

  @Override
  public void deleteSchema() {
    String prefix = this.operateProperties.getOpensearch().getIndexPrefix();
    logger.info("Removing indices {}*", prefix);
    richOpenSearchClient.index().deleteIndicesWithRetries(prefix + "*");
    richOpenSearchClient.template().deleteTemplatesWithRetries(prefix + "*");
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
    operateProperties.getOpensearch().setCreateSchema(createSchema);
  }

  @Override
  public void setIndexPrefix(String indexPrefix) {
    operateProperties.getOpensearch().setIndexPrefix(indexPrefix);
  }

  @Override
  public void setDefaultIndexPrefix() {
    operateProperties.getOpensearch().setDefaultIndexPrefix();
  }
}
