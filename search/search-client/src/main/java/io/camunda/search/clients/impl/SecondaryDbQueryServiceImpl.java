/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.impl;

import io.camunda.search.clients.ProcessInstanceSearchClient;
import io.camunda.search.clients.SecondaryDbQueryService;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.filter.ProcessInstanceFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.search.query.SearchQueryResult;

/** This services uses the existing search client to query the secondary database. */
public class SecondaryDbQueryServiceImpl implements SecondaryDbQueryService {

  private final ProcessInstanceSearchClient processInstanceSearchClient;

  public SecondaryDbQueryServiceImpl(
      final ProcessInstanceSearchClient processInstanceSearchClient) {
    this.processInstanceSearchClient = processInstanceSearchClient;
  }

  @Override
  public SearchQueryResult<Long> queryProcessInstanceKeys(
      final ProcessInstanceFilter filter, final SearchQueryPage page) {
    final var query =
        SearchQueryBuilders.processInstanceSearchQuery()
            .filter(filter)
            .page(page)
            .resultConfig(r -> r.onlyKey(true))
            .build();

    final var result = processInstanceSearchClient.searchProcessInstances(query);

    return new SearchQueryResult<>(
        result.total(),
        result.items().stream().map(ProcessInstanceEntity::processInstanceKey).toList(),
        result.firstSortValues(),
        result.lastSortValues());
  }
}
