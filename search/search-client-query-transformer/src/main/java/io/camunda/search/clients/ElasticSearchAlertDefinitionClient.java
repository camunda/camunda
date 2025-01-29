/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients;

import io.camunda.search.clients.core.SearchIndexRequest;
import io.camunda.search.clients.core.SearchQueryRequest;
import io.camunda.search.entities.AlertDefinitionEntity;
import io.camunda.webapps.schema.descriptors.AlertDefinitionIndex;
import java.util.List;

public class ElasticSearchAlertDefinitionClient implements AlertDefinitionClient {

  private final DocumentBasedWriteClient writeClient;
  private final DocumentBasedSearchClient readClient;
  private final AlertDefinitionIndex alertDefinitionIndex;

  public ElasticSearchAlertDefinitionClient(
      final DocumentBasedWriteClient writeClient,
      final DocumentBasedSearchClient readClient,
      final AlertDefinitionIndex alertDefinitionIndex) {
    this.writeClient = writeClient;
    this.readClient = readClient;
    this.alertDefinitionIndex = alertDefinitionIndex;
  }

  @Override
  public void store(final AlertDefinitionEntity alertDefinition) {
    final SearchIndexRequest<Object> indexRequest =
        SearchIndexRequest.of(
            b -> b.index(alertDefinitionIndex.getFullQualifiedName()).document(alertDefinition));
    writeClient.index(indexRequest);
  }

  @Override
  public List<AlertDefinitionEntity> query() {
    return readClient.findAll(
        SearchQueryRequest.of(b -> b.index(alertDefinitionIndex.getFullQualifiedName())),
        AlertDefinitionEntity.class);
  }
}
