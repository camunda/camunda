/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.rdbms;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.domain.ProcessDefinitionModel;
import io.camunda.db.rdbms.domain.ProcessInstanceFilter;
import io.camunda.db.rdbms.domain.ProcessInstanceModel.State;
import io.camunda.search.clients.ProcessInstanceSearchClient;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.entities.ProcessInstanceEntity.ProcessInstanceState;
import io.camunda.search.query.ProcessInstanceQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.security.auth.Authentication;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RdbmsSearchClient implements ProcessInstanceSearchClient {

  private static final Logger LOG = LoggerFactory.getLogger(RdbmsSearchClient.class);

  private final RdbmsService rdbmsService;

  public RdbmsSearchClient(final RdbmsService rdbmsService) {
    this.rdbmsService = rdbmsService;
  }

  @Override
  public SearchQueryResult<ProcessInstanceEntity> searchProcessInstances(final ProcessInstanceQuery query, final Authentication authentication) {
    LOG.debug("[RDBMS Search Client] Search for processInstance: {}", query);

    final var searchResult = rdbmsService.getProcessInstanceRdbmsService().search(new ProcessInstanceFilter(
        (query.filter().processDefinitionIds().isEmpty()) ? null : query.filter().processDefinitionIds().getFirst(),
        null,
        query.page().size(),
        query.page().from()
    ));

    return new SearchQueryResult<>(searchResult.total(),
        searchResult.hits().stream().map(pi -> {
              var processDefinition = rdbmsService.getProcessDeploymentRdbmsService().findOne(pi.processDefinitionKey(), pi.version());
              return new ProcessInstanceEntity(
                  pi.processInstanceKey(),
                  pi.bpmnProcessId(),
                  processDefinition.map(ProcessDefinitionModel::name).orElse(pi.bpmnProcessId()),
                  pi.version(), null,
                  pi.processDefinitionKey(),
                  null,
                  pi.parentProcessInstanceKey(),
                  pi.parentElementInstanceKey(),
                  null,
                  pi.startDate().toString(),
                  Optional.ofNullable(pi.endDate()).map(OffsetDateTime::toString).orElse(null),
                  mapState(pi.state()),
                  null,
                  pi.tenantId()
              );
            }
        ).toList(),
        null
    );
  }

  @Override
  public void close() throws Exception {
  }

  private ProcessInstanceEntity.ProcessInstanceState mapState(State state) {
    return switch (state) {
      case ACTIVE -> ProcessInstanceState.ACTIVE;
      case COMPLETED -> ProcessInstanceState.COMPLETED;
      case CANCELED -> ProcessInstanceState.CANCELED;
    };
  }
}
