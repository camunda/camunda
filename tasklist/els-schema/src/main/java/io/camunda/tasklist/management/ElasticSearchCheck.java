/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.management;

import io.camunda.tasklist.data.conditionals.ElasticSearchCondition;
import io.camunda.tasklist.es.RetryElasticsearchClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class ElasticSearchCheck implements SearchEngineCheck {

  @Autowired private RetryElasticsearchClient retryElasticsearchClient;

  @Override
  public boolean isHealthy() {
    return retryElasticsearchClient.isHealthy();
  }
}
