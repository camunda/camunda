/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.reader;

import io.camunda.search.clients.SearchClientBasedQueryExecutor;
import io.camunda.search.entities.ResourceEntity;
import io.camunda.search.query.ResourceQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;

public class ResourceDocumentReader extends DocumentBasedReader implements ResourceReader {

  public ResourceDocumentReader(
      final SearchClientBasedQueryExecutor executor, final IndexDescriptor indexDescriptor) {
    super(executor, indexDescriptor);
  }

  @Override
  public ResourceEntity getByKey(final long key, final ResourceAccessChecks resourceAccessChecks) {
    return getSearchExecutor()
        .getById(
            String.valueOf(key),
            io.camunda.webapps.schema.entities.resource.ResourceEntity.class,
            indexDescriptor.getFullQualifiedName());
  }

  @Override
  public SearchQueryResult<ResourceEntity> search(
      final ResourceQuery query, final ResourceAccessChecks resourceAccessChecks) {
    return getSearchExecutor()
        .search(
            query,
            io.camunda.webapps.schema.entities.resource.ResourceEntity.class,
            resourceAccessChecks);
  }
}
