/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.authorization;

import io.camunda.search.entities.AuthorizationEntity;
import io.camunda.search.entities.BatchOperationEntity;
import io.camunda.search.entities.DecisionDefinitionEntity;
import io.camunda.search.entities.DecisionInstanceEntity;
import io.camunda.search.entities.DecisionRequirementsEntity;
import io.camunda.search.entities.FlowNodeInstanceEntity;
import io.camunda.search.entities.GroupEntity;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.security.auth.Authorization;

public abstract class Authorizations {

  public static final Authorization<AuthorizationEntity> AUTHORIZATION_READ_AUTHORIZATION =
      Authorization.of(a -> a.authorization().read());

  public static final Authorization<BatchOperationEntity> BATCH_OPERATION_READ_AUTHORIZATION =
      Authorization.of(a -> a.batchOperation().read());

  public static final Authorization<DecisionDefinitionEntity>
      DECISION_DEFINITION_READ_AUTHORIZATION =
          Authorization.of(a -> a.decisionDefinition().readDecisionDefinition());

  public static final Authorization<DecisionInstanceEntity> DECISION_INSTANCE_READ_AUTHORIZATION =
      Authorization.of(a -> a.decisionDefinition().readDecisionInstance());

  public static final Authorization<DecisionRequirementsEntity>
      DECISION_REQUIREMENTS_READ_AUTHORIZATION =
          Authorization.of(a -> a.decisionRequirementsDefinition().read());

  public static final Authorization<FlowNodeInstanceEntity> ELEMENT_INSTANCE_READ_AUTHORIZATION =
      Authorization.of(a -> a.processDefinition().readProcessInstance());

  public static final Authorization<GroupEntity> GROUP_READ_AUTHORIZATION =
      Authorization.of(a -> a.group().read());

  public static final Authorization<ProcessInstanceEntity> PROCESS_INSTANCE_READ_AUTHORIZATION =
      Authorization.of(a -> a.processDefinition().readProcessInstance());
}
