/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.reader;

import io.camunda.search.clients.security.ResourceAccessChecks;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.TypedSearchQuery;

public interface SearchEntityReader<T, Q extends TypedSearchQuery<?, ?>>
    extends SearchClientReader {

  T getByKey(String key, final ResourceAccessChecks resourceAccessChecks);

  SearchQueryResult<T> search(final Q query, final ResourceAccessChecks resourceAccessChecks);
}
