/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.reader;

import io.camunda.search.clients.DocumentBasedSearchClient;
import io.camunda.search.clients.security.ResourceAccessChecks;
import io.camunda.search.clients.transformers.ServiceTransformers;
import io.camunda.search.entities.SequenceFlowEntity;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.SequenceFlowQuery;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;

public class SequenceFlowDocumentReader extends DocumentBasedReader implements SequenceFlowReader {

  public SequenceFlowDocumentReader(
      final DocumentBasedSearchClient searchClient,
      final ServiceTransformers transformers,
      final IndexDescriptor indexDescriptor) {
    super(searchClient, transformers, indexDescriptor);
  }

  @Override
  public SequenceFlowEntity getByKey(
      final String key, final ResourceAccessChecks resourceAccessChecks) {
    throw new UnsupportedOperationException("SequenceFlowReader#getByKey not supported");
  }

  @Override
  public SearchQueryResult<SequenceFlowEntity> search(
      final SequenceFlowQuery query, final ResourceAccessChecks resourceAccessChecks) {
    return getSearchExecutor()
        .search(
            query,
            io.camunda.webapps.schema.entities.SequenceFlowEntity.class,
            resourceAccessChecks);
  }
}
