/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients;

import io.camunda.service.entities.AuthorizationEntity;
import io.camunda.service.entities.DecisionDefinitionEntity;
import io.camunda.service.entities.DecisionInstanceEntity;
import io.camunda.service.entities.DecisionRequirementsEntity;
import io.camunda.service.entities.FlowNodeInstanceEntity;
import io.camunda.service.entities.IncidentEntity;
import io.camunda.service.entities.UserEntity;
import io.camunda.service.entities.UserTaskEntity;
import io.camunda.service.entities.VariableEntity;
import io.camunda.service.search.query.AuthorizationQuery;
import io.camunda.service.search.query.DecisionDefinitionQuery;
import io.camunda.service.search.query.DecisionInstanceQuery;
import io.camunda.service.search.query.DecisionRequirementsQuery;
import io.camunda.service.search.query.FlowNodeInstanceQuery;
import io.camunda.service.search.query.IncidentQuery;
import io.camunda.service.search.query.SearchQueryResult;
import io.camunda.service.search.query.UserQuery;
import io.camunda.service.search.query.UserTaskQuery;
import io.camunda.service.search.query.VariableQuery;
import io.camunda.service.security.auth.Authentication;
import io.camunda.zeebe.util.Either;

public interface CamundaSearchClient extends AutoCloseable {

  Either<Exception, SearchQueryResult<AuthorizationEntity>> searchAuthorizations(
      AuthorizationQuery filter, Authentication authentication);

  Either<Exception, SearchQueryResult<DecisionDefinitionEntity>> searchDecisionDefinitions(
      DecisionDefinitionQuery filter, Authentication authentication);

  Either<Exception, SearchQueryResult<DecisionInstanceEntity>> searchDecisionInstances(
      DecisionInstanceQuery filter, Authentication authentication);

  Either<Exception, SearchQueryResult<DecisionRequirementsEntity>> searchDecisionRequirements(
      DecisionRequirementsQuery filter, Authentication authentication);

  Either<Exception, SearchQueryResult<FlowNodeInstanceEntity>> searchFlowNodeInstances(
      FlowNodeInstanceQuery filter, Authentication authentication);

  Either<Exception, SearchQueryResult<IncidentEntity>> searchIncidents(
      IncidentQuery filter, Authentication authentication);

  Either<Exception, SearchQueryResult<UserEntity>> searchUsers(
      UserQuery filter, Authentication authentication);

  Either<Exception, SearchQueryResult<UserTaskEntity>> searchUserTasks(
      UserTaskQuery filter, Authentication authentication);

  Either<Exception, SearchQueryResult<VariableEntity>> searchVariables(
      VariableQuery filter, Authentication authentication);
}
