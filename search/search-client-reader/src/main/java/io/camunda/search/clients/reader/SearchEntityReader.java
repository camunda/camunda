/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.reader;

import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.TypedSearchQuery;
import io.camunda.security.reader.ResourceAccessChecks;

public interface SearchEntityReader<T, Q extends TypedSearchQuery<?, ?>>
    extends SearchClientReader {

  /**
   * Returns an entity by id. This method must be implemented if the id of the given entity is
   * either specified by a user (for example, a user specifies a group id for a group).
   *
   * @param resourceAccessChecks to be applied when retrieving the entity, if applicable
   */
  default T getById(final String id, final ResourceAccessChecks resourceAccessChecks) {
    throw new UnsupportedOperationException("SearchClientReader#getById() not supported");
  }

  /**
   * Returns an entity by key. This method must be implemented if the key is a {@link Long} and
   * provided by the C8 Orchestration Cluster itself.
   *
   * @param resourceAccessChecks to be applied when retrieving the entity, if applicable.
   */
  default T getByKey(final long key, final ResourceAccessChecks resourceAccessChecks) {
    throw new UnsupportedOperationException("SearchClientReader#getByKey() not supported");
  }

  /**
   * Execute the given query and applies the resourceAccessChecks.
   *
   * @param resourceAccessChecks to be applied
   */
  SearchQueryResult<T> search(final Q query, final ResourceAccessChecks resourceAccessChecks);
}
