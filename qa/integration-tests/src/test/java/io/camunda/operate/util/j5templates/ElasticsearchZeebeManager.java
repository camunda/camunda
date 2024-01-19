/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.util.j5templates;

import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.qa.util.TestContainerUtil;
import io.camunda.operate.util.TestUtil;
import org.elasticsearch.client.RestHighLevelClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component
public class ElasticsearchZeebeManager extends ZeebeContainerManager {
  private static final Logger logger = LoggerFactory.getLogger(ElasticsearchZeebeManager.class);

  private final RestHighLevelClient zeebeEsClient;

  public ElasticsearchZeebeManager(OperateProperties operateProperties, TestContainerUtil testContainerUtil,
                                   @Qualifier("zeebeEsClient") RestHighLevelClient zeebeEsClient) {
    super(operateProperties, testContainerUtil);
    this.zeebeEsClient = zeebeEsClient;
  }

  protected void updatePrefix() {
    logger.info("Starting Zeebe with ELS prefix: " + prefix);
    operateProperties.getZeebeElasticsearch().setPrefix(prefix);
  }

  protected void removeIndices() {
    TestUtil.removeAllIndices(zeebeEsClient, prefix);
  }
}
