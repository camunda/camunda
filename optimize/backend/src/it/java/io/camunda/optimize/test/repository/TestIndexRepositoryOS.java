/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.test.repository;

import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.util.Set;
import org.opensearch.client.opensearch.indices.GetIndexRequest;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class TestIndexRepositoryOS implements TestIndexRepository {

  private final OptimizeOpenSearchClient osClient;

  public TestIndexRepositoryOS(final OptimizeOpenSearchClient osClient) {
    this.osClient = osClient;
  }

  @Override
  public Set<String> getAllIndexNames() {
    final GetIndexRequest.Builder requestBuilder = new GetIndexRequest.Builder().index("*");
    return osClient.getRichOpenSearchClient().index().get(requestBuilder).result().keySet();
  }
}
