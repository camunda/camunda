/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.schema.util.elasticsearch;

import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.schema.util.SearchClientTestHelper;
import io.camunda.operate.store.elasticsearch.RetryElasticsearchClient;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;

@Conditional(ElasticsearchCondition.class)
public class ElasticsearchClientTestHelper implements SearchClientTestHelper {

  @Autowired public RetryElasticsearchClient elasticsearchClient;

  @Override
  public void setClientRetries(final int retries) {
    elasticsearchClient.setNumberOfRetries(retries);
  }

  @Override
  public void createDocument(
      final String indexName, final String id, final Map<String, Object> document) {
    elasticsearchClient.createOrUpdateDocument(indexName, id, document);
  }

  @Override
  public void createDocument(
      final String indexName,
      final String id,
      final String routing,
      final Map<String, Object> document) {
    elasticsearchClient.createOrUpdateDocument(indexName, id, routing, document);
  }
}
