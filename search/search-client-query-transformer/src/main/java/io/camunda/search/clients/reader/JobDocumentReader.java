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
import io.camunda.search.entities.JobEntity;
import io.camunda.search.query.JobQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;

public class JobDocumentReader extends DocumentBasedReader implements JobReader {

  public JobDocumentReader(
      final DocumentBasedSearchClient searchClient,
      final ServiceTransformers transformers,
      final IndexDescriptor indexDescriptor) {
    super(searchClient, transformers, indexDescriptor);
  }

  @Override
  public JobEntity getByKey(final String key, final ResourceAccessChecks resourceAccessChecks) {
    return getSearchExecutor()
        .getByQuery(
            JobQuery.of(b -> b.filter(f -> f.jobKeys(Long.valueOf(key))).singleResult()),
            io.camunda.webapps.schema.entities.JobEntity.class,
            resourceAccessChecks);
  }

  @Override
  public SearchQueryResult<JobEntity> search(
      final JobQuery query, final ResourceAccessChecks resourceAccessChecks) {
    return getSearchExecutor()
        .search(query, io.camunda.webapps.schema.entities.JobEntity.class, resourceAccessChecks);
  }
}
