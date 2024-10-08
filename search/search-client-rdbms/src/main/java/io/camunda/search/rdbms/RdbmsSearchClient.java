/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.rdbms;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.domain.Paging;
import io.camunda.db.rdbms.domain.ProcessDefinitionModel;
import io.camunda.db.rdbms.domain.ProcessInstanceFilter;
import io.camunda.db.rdbms.domain.ProcessInstanceFilter.ProcessInstanceSortField;
import io.camunda.db.rdbms.domain.ProcessInstanceModel.State;
import io.camunda.db.rdbms.domain.SortDirection;
import io.camunda.db.rdbms.domain.SortFieldEntry;
import io.camunda.search.clients.AuthorizationSearchClient;
import io.camunda.search.clients.DecisionDefinitionSearchClient;
import io.camunda.search.clients.DecisionInstanceSearchClient;
import io.camunda.search.clients.DecisionRequirementSearchClient;
import io.camunda.search.clients.FlowNodeInstanceSearchClient;
import io.camunda.search.clients.FormSearchClient;
import io.camunda.search.clients.IncidentSearchClient;
import io.camunda.search.clients.ProcessInstanceSearchClient;
import io.camunda.search.clients.UserSearchClient;
import io.camunda.search.clients.UserTaskSearchClient;
import io.camunda.search.clients.VariableSearchClient;
import io.camunda.search.entities.AuthorizationEntity;
import io.camunda.search.entities.DecisionDefinitionEntity;
import io.camunda.search.entities.DecisionInstanceEntity;
import io.camunda.search.entities.DecisionRequirementsEntity;
import io.camunda.search.entities.FlowNodeInstanceEntity;
import io.camunda.search.entities.FormEntity;
import io.camunda.search.entities.IncidentEntity;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.entities.ProcessInstanceEntity.ProcessInstanceState;
import io.camunda.search.entities.UserEntity;
import io.camunda.search.entities.UserTaskEntity;
import io.camunda.search.entities.VariableEntity;
import io.camunda.search.query.AuthorizationQuery;
import io.camunda.search.query.DecisionDefinitionQuery;
import io.camunda.search.query.DecisionInstanceQuery;
import io.camunda.search.query.DecisionRequirementsQuery;
import io.camunda.search.query.FlowNodeInstanceQuery;
import io.camunda.search.query.FormQuery;
import io.camunda.search.query.IncidentQuery;
import io.camunda.search.query.ProcessInstanceQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.UserQuery;
import io.camunda.search.query.UserTaskQuery;
import io.camunda.search.query.VariableQuery;
import io.camunda.search.security.auth.Authentication;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RdbmsSearchClient
    implements AuthorizationSearchClient,
        DecisionDefinitionSearchClient,
        DecisionInstanceSearchClient,
        DecisionRequirementSearchClient,
        FlowNodeInstanceSearchClient,
        FormSearchClient,
        IncidentSearchClient,
        ProcessInstanceSearchClient,
        UserTaskSearchClient,
        UserSearchClient,
        VariableSearchClient {

  private static final Logger LOG = LoggerFactory.getLogger(RdbmsSearchClient.class);

  private final RdbmsService rdbmsService;

  public RdbmsSearchClient(final RdbmsService rdbmsService) {
    this.rdbmsService = rdbmsService;
  }

  @Override
  public SearchQueryResult<ProcessInstanceEntity> searchProcessInstances(
      final ProcessInstanceQuery query, final Authentication authentication) {
    LOG.debug("[RDBMS Search Client] Search for processInstance: {}", query);

    final var searchResult =
        rdbmsService
            .getProcessInstanceRdbmsService()
            .search(
                new ProcessInstanceFilter(
                    (query.filter().processDefinitionIds().isEmpty())
                        ? null
                        : query.filter().processDefinitionIds().getFirst(),
                    null,
                    new Paging(query.page().size(), query.page().from()),
                    List.of(
                        new SortFieldEntry(
                            ProcessInstanceSortField.START_DATE, SortDirection.DESC))));

    return new SearchQueryResult<>(
        searchResult.total(),
        searchResult.hits().stream()
            .map(
                pi -> {
                  final var processDefinition =
                      rdbmsService
                          .getProcessDeploymentRdbmsService()
                          .findOne(pi.processDefinitionKey(), pi.version());
                  return new ProcessInstanceEntity(
                      pi.processInstanceKey(),
                      pi.bpmnProcessId(),
                      processDefinition
                          .map(ProcessDefinitionModel::name)
                          .orElse(pi.bpmnProcessId()),
                      pi.version(),
                      null,
                      pi.processDefinitionKey(),
                      null,
                      pi.parentProcessInstanceKey(),
                      pi.parentElementInstanceKey(),
                      null,
                      pi.startDate().toString(),
                      Optional.ofNullable(pi.endDate()).map(OffsetDateTime::toString).orElse(null),
                      mapState(pi.state()),
                      null,
                      pi.tenantId());
                })
            .toList(),
        null);
  }

  @Override
  public void close() throws Exception {}

  private ProcessInstanceEntity.ProcessInstanceState mapState(State state) {
    return switch (state) {
      case ACTIVE -> ProcessInstanceState.ACTIVE;
      case COMPLETED -> ProcessInstanceState.COMPLETED;
      case CANCELED -> ProcessInstanceState.CANCELED;
    };
  }

  @Override
  public SearchQueryResult<AuthorizationEntity> searchAuthorizations(
      final AuthorizationQuery filter, final Authentication authentication) {
    return null;
  }

  @Override
  public SearchQueryResult<DecisionDefinitionEntity> searchDecisionDefinitions(
      final DecisionDefinitionQuery filter, final Authentication authentication) {
    return null;
  }

  @Override
  public SearchQueryResult<DecisionInstanceEntity> searchDecisionInstances(
      final DecisionInstanceQuery filter, final Authentication authentication) {
    return null;
  }

  @Override
  public SearchQueryResult<DecisionRequirementsEntity> searchDecisionRequirements(
      final DecisionRequirementsQuery filter, final Authentication authentication) {
    return null;
  }

  @Override
  public SearchQueryResult<FlowNodeInstanceEntity> searchFlowNodeInstances(
      final FlowNodeInstanceQuery filter, final Authentication authentication) {
    return null;
  }

  @Override
  public SearchQueryResult<FormEntity> searchForms(
      final FormQuery filter, final Authentication authentication) {
    return null;
  }

  @Override
  public SearchQueryResult<IncidentEntity> searchIncidents(
      final IncidentQuery filter, final Authentication authentication) {
    return null;
  }

  @Override
  public SearchQueryResult<UserEntity> searchUsers(
      final UserQuery filter, final Authentication authentication) {
    return null;
  }

  @Override
  public SearchQueryResult<UserTaskEntity> searchUserTasks(
      final UserTaskQuery filter, final Authentication authentication) {
    return null;
  }

  @Override
  public SearchQueryResult<VariableEntity> searchVariables(
      final VariableQuery filter, final Authentication authentication) {
    return null;
  }
}
