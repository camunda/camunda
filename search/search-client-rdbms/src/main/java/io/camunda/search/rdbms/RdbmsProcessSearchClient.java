/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.rdbms;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.domain.ProcessDefinitionDbQuery;
import io.camunda.db.rdbms.read.domain.ProcessInstanceDbQuery;
import io.camunda.search.clients.ProcessDefinitionSearchClient;
import io.camunda.search.clients.ProcessInstanceSearchClient;
import io.camunda.search.entities.ProcessDefinitionEntity;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.query.ProcessDefinitionQuery;
import io.camunda.search.query.ProcessInstanceQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.security.auth.Authentication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RdbmsProcessSearchClient
    implements ProcessInstanceSearchClient, ProcessDefinitionSearchClient {

  private static final Logger LOG = LoggerFactory.getLogger(RdbmsProcessSearchClient.class);

  private final RdbmsService rdbmsService;

  public RdbmsProcessSearchClient(final RdbmsService rdbmsService) {
    this.rdbmsService = rdbmsService;
  }

  @Override
  public SearchQueryResult<ProcessInstanceEntity> searchProcessInstances(
      final ProcessInstanceQuery query, final Authentication authentication) {
    LOG.debug("[RDBMS Search Client] Search for processInstance: {}", query);

    final var searchResult =
        rdbmsService
            .getProcessInstanceReader()
            .search(ProcessInstanceDbQuery.of(b -> b
                .filter(query.filter())
                .sort(query.sort())
                .page(query.page())
            ));

    return new SearchQueryResult<>(searchResult.total(), searchResult.hits(), null);
  }

  @Override
  public SearchQueryResult<ProcessDefinitionEntity> searchProcessDefinitions(
      final ProcessDefinitionQuery query, final Authentication authentication) {
    LOG.debug("[RDBMS Search Client] Search for processDefinition: {}", query);

    final var searchResult =
        rdbmsService
            .getProcessDefinitionReader()
            .search(ProcessDefinitionDbQuery.of(b -> b
                .filter(query.filter())
                .sort(query.sort())
                .page(query.page())
            ));

    return new SearchQueryResult<>(searchResult.total(), searchResult.hits(), null);
  }
}
