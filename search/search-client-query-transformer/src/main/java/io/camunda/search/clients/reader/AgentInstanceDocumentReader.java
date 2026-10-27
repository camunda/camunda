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
import io.camunda.security.core.authz.ResourceAccessChecks;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;

public class AgentInstanceDocumentReader extends DocumentBasedReader
    implements AgentInstanceReader {

  public AgentInstanceDocumentReader(
      final SearchClientBasedQueryExecutor executor, final IndexDescriptor indexDescriptor) {
    super(executor, indexDescriptor);
  }

  @Override
  public AgentInstanceEntity getByKey(
      final long key, final ResourceAccessChecks resourceAccessChecks) {
    return getSearchExecutor()
        .getByQuery(
            AgentInstanceQuery.of(b -> b.filter(f -> f.agentInstanceKeys(key)).singleResult()),
            io.camunda.webapps.schema.entities.agentinstance.AgentInstanceEntity.class);
  }

  @Override
  public SearchQueryResult<AgentInstanceEntity> search(
      final AgentInstanceQuery query, final ResourceAccessChecks resourceAccessChecks) {
    return getSearchExecutor()
        .search(
            query,
            io.camunda.webapps.schema.entities.agentinstance.AgentInstanceEntity.class,
            resourceAccessChecks);
  }
}
