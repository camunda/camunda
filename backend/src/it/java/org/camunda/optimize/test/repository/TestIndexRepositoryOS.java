/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.test.repository;

import java.util.Set;
import lombok.AllArgsConstructor;
import org.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.opensearch.client.opensearch.indices.GetIndexRequest;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
@Conditional(OpenSearchCondition.class)
public class TestIndexRepositoryOS implements TestIndexRepository {
  private final OptimizeOpenSearchClient osClient;

  @Override
  public Set<String> getAllIndexNames() {
    GetIndexRequest.Builder requestBuilder = new GetIndexRequest.Builder().index("*");
    return osClient.getRichOpenSearchClient().index().get(requestBuilder).result().keySet();
  }
}
