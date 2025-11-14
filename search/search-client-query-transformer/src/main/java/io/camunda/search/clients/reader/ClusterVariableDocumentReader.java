/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.reader;

import io.camunda.search.clients.SearchClientBasedQueryExecutor;
import io.camunda.search.entities.ClusterVariableEntity;
import io.camunda.search.query.ClusterVariableQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;

public class ClusterVariableDocumentReader extends DocumentBasedReader
    implements ClusterVariableReader {

  public ClusterVariableDocumentReader(
      final SearchClientBasedQueryExecutor executor, final IndexDescriptor indexDescriptor) {
    super(executor, indexDescriptor);
  }

  @Override
  public SearchQueryResult<ClusterVariableEntity> search(
      final ClusterVariableQuery query, final ResourceAccessChecks resourceAccessChecks) {
    return getSearchExecutor()
        .search(
            query,
            io.camunda.webapps.schema.entities.clustervariable.ClusterVariableEntity.class,
            resourceAccessChecks);
  }

  @Override
  public ClusterVariableEntity getTenantScopedClusterVariable(
      final String tenant, final String name) {
    return getSearchExecutor()
        .getByQuery(
            ClusterVariableQuery.of(
                b -> b.filter(f -> f.names(name).tenantIds(tenant)).singleResult()),
            io.camunda.webapps.schema.entities.clustervariable.ClusterVariableEntity.class);
  }

  @Override
  public ClusterVariableEntity getGloballyScopedClusterVariable(final String name) {
    return getSearchExecutor()
        .getByQuery(
            ClusterVariableQuery.of(b -> b.filter(f -> f.names(name)).singleResult()),
            io.camunda.webapps.schema.entities.clustervariable.ClusterVariableEntity.class);
  }
}
