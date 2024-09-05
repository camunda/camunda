/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.rdbms;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.search.clients.ProcessInstanceSearchClient;
import io.camunda.search.clients.query.SearchBoolQuery;
import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.clients.query.SearchTermQuery;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.query.ProcessInstanceQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.security.auth.Authentication;

public class RdbmsSearchClient implements ProcessInstanceSearchClient {

  private final RdbmsService rdbmsService;

  public RdbmsSearchClient(final RdbmsService rdbmsService) {
    this.rdbmsService = rdbmsService;
  }

  @Override
  public SearchQueryResult<ProcessInstanceEntity> searchProcessInstances(final ProcessInstanceQuery filter, final Authentication authentication) {
    return null;
  }

  @Override
  public void close() throws Exception {

  }

  public String getBpmnProcessId(final SearchQuery searchQuery) {
    if (searchQuery.queryOption() instanceof final SearchTermQuery searchTermQuery) {
      if (searchTermQuery.field().equalsIgnoreCase("bpmnProcessId")) {
        return searchTermQuery.value().stringValue();
      } else {
        return null;
      }
    } else if (searchQuery.queryOption() instanceof final SearchBoolQuery searchBoolQuery) {
      for (final SearchQuery sq : searchBoolQuery.must()) {
        final var term = getBpmnProcessId(sq);
        if (term != null) {
          return term;
        }
      }
    }

    return null;
  }
}
