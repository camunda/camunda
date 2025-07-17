/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.reader;

import io.camunda.search.clients.SearchClientBasedQueryExecutor;
import io.camunda.search.entities.UserTaskEntity;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.UserTaskQuery;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.entities.usertask.TaskEntity;

public class UserTaskDocumentReader extends DocumentBasedReader implements UserTaskReader {

  public UserTaskDocumentReader(
      final SearchClientBasedQueryExecutor executor, final IndexDescriptor indexDescriptor) {
    super(executor, indexDescriptor);
  }

  @Override
  public UserTaskEntity getByKey(final long key, final ResourceAccessChecks resourceAccessChecks) {
    return getSearchExecutor()
        .getByQuery(
            UserTaskQuery.of(b -> b.filter(f -> f.userTaskKeys(key)).singleResult()),
            TaskEntity.class);
  }

  @Override
  public SearchQueryResult<UserTaskEntity> search(
      final UserTaskQuery query, final ResourceAccessChecks resourceAccessChecks) {
    return getSearchExecutor().search(query, TaskEntity.class, resourceAccessChecks);
  }
}
