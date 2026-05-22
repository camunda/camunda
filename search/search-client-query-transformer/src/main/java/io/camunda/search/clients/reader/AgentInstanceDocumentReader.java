/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.reader;

import io.camunda.search.clients.SearchClientBasedQueryExecutor;
import io.camunda.search.entities.AgentInstanceEntity;
import io.camunda.search.query.AgentInstanceQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;

/**
 * Elasticsearch/OpenSearch-backed document reader for agent instances.
 *
 * <p>Full implementation will be covered in <a
 * href="https://github.com/camunda/camunda/issues/52817">#52817</a>.
 */
public class AgentInstanceDocumentReader extends DocumentBasedReader
    implements AgentInstanceReader {

  public AgentInstanceDocumentReader(
      final SearchClientBasedQueryExecutor executor, final IndexDescriptor indexDescriptor) {
    super(executor, indexDescriptor);
  }

  @Override
  public AgentInstanceEntity getByKey(
      final long key, final ResourceAccessChecks resourceAccessChecks) {
    throw new UnsupportedOperationException(
        "AgentInstanceDocumentReader is not yet implemented; see https://github.com/camunda/camunda/issues/52817");
  }

  @Override
  public SearchQueryResult<AgentInstanceEntity> search(
      final AgentInstanceQuery query, final ResourceAccessChecks resourceAccessChecks) {
    throw new UnsupportedOperationException(
        "AgentInstanceDocumentReader is not yet implemented; see https://github.com/camunda/camunda/issues/52817");
  }
}
