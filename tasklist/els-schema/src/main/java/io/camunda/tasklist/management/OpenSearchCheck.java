/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.management;

import io.camunda.tasklist.data.conditionals.OpenSearchCondition;
import io.camunda.tasklist.os.RetryOpenSearchClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class OpenSearchCheck implements SearchEngineCheck {

  @Autowired private RetryOpenSearchClient retryOpenSearchClient;

  @Override
  public boolean isHealthy() {
    return retryOpenSearchClient.isHealthy();
  }
}
