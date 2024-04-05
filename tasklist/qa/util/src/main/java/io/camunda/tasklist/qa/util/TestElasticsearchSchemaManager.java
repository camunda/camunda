/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.qa.util;

import io.camunda.tasklist.data.conditionals.ElasticSearchCondition;
import io.camunda.tasklist.schema.manager.ElasticsearchSchemaManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component("schemaManager")
@Profile({"test"})
@Conditional(ElasticSearchCondition.class)
public class TestElasticsearchSchemaManager extends ElasticsearchSchemaManager
    implements TestSchemaManager {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(TestElasticsearchSchemaManager.class);

  public void deleteSchema() {
    final String prefix = tasklistProperties.getElasticsearch().getIndexPrefix();
    LOGGER.info("Removing indices " + prefix + "*");
    retryElasticsearchClient.deleteIndicesFor(prefix + "*");
    retryElasticsearchClient.deleteTemplatesFor(prefix + "*");
  }

  public void deleteSchemaQuietly() {
    try {
      deleteSchema();
    } catch (Exception t) {
      LOGGER.debug(t.getMessage());
    }
  }
}
