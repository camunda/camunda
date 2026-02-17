/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.filter;

import static io.camunda.search.clients.query.SearchQueryBuilders.and;
import static io.camunda.search.clients.query.SearchQueryBuilders.intOperations;
import static io.camunda.search.clients.query.SearchQueryBuilders.matchAll;
import static io.camunda.search.clients.query.SearchQueryBuilders.stringOperations;
import static io.camunda.search.clients.query.SearchQueryBuilders.term;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.filter.GlobalListenerFilter;
import io.camunda.security.auth.Authorization;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.index.GlobalListenerIndex;
import java.util.ArrayList;

public final class GlobalListenerFilterTransformer
    extends IndexFilterTransformer<GlobalListenerFilter> {

  public GlobalListenerFilterTransformer(final IndexDescriptor indexDescriptor) {
    super(indexDescriptor);
  }

  @Override
  public SearchQuery toSearchQuery(final GlobalListenerFilter filter) {
    final var queries = new ArrayList<SearchQuery>();
    queries.addAll(
        stringOperations(GlobalListenerIndex.LISTENER_ID, filter.listenerIdOperations()));
    queries.addAll(stringOperations(GlobalListenerIndex.TYPE, filter.typeOperations()));
    queries.addAll(intOperations(GlobalListenerIndex.RETRIES, filter.retriesOperations()));
    queries.addAll(stringOperations(GlobalListenerIndex.EVENT_TYPES, filter.eventTypeOperations()));
    if (filter.afterNonGlobal() != null) {
      queries.add(term(GlobalListenerIndex.AFTER_NON_GLOBAL, filter.afterNonGlobal()));
    }
    queries.addAll(intOperations(GlobalListenerIndex.PRIORITY, filter.priorityOperations()));
    queries.addAll(stringOperations(GlobalListenerIndex.SOURCE, filter.sourceOperations()));
    queries.addAll(
        stringOperations(GlobalListenerIndex.LISTENER_TYPE, filter.listenerTypeOperations()));
    return and(queries);
  }

  @Override
  protected SearchQuery toAuthorizationCheckSearchQuery(final Authorization<?> authorization) {
    return matchAll();
  }
}
