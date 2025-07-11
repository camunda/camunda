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
import io.camunda.search.entities.IncidentEntity;
import io.camunda.search.entities.JobEntity;
import io.camunda.search.entities.MappingEntity;
import io.camunda.search.entities.MessageSubscriptionEntity;
import io.camunda.search.entities.ProcessDefinitionEntity;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.entities.RoleEntity;
import io.camunda.search.entities.SequenceFlowEntity;
import io.camunda.search.entities.TenantEntity;
import io.camunda.search.entities.UserEntity;
import io.camunda.search.entities.UserTaskEntity;
import io.camunda.search.entities.VariableEntity;
import io.camunda.security.auth.Authorization;
import io.camunda.security.auth.Authorization.Builder;

public class Authorizations {

  public static final Authorization<?> UNSPECIFIED_AUTHORIZATION =
      Authorization.of(Builder::unspecified);

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

  public static final Authorization<IncidentEntity> INCIDENT_READ_AUTHORIZATION =
      Authorization.of(a -> a.processDefinition().readProcessInstance());

  public static final Authorization<JobEntity> JOB_READ_AUTHORIZATION =
      Authorization.of(a -> a.processDefinition().readProcessInstance());

  public static final Authorization<MappingEntity> MAPPING_READ_AUTHORIZATION =
      Authorization.of(a -> a.mapping().read());

  public static final Authorization<MessageSubscriptionEntity>
      MESSAGE_SUBSCRIPTION_READ_AUTHORIZATION =
          Authorization.of(a -> a.processDefinition().readProcessInstance());

  public static final Authorization<ProcessDefinitionEntity> PROCESS_DEFINITION_READ_AUTHORIZATION =
      Authorization.of(a -> a.processDefinition().readProcessDefinition());

  public static final Authorization<ProcessInstanceEntity> PROCESS_INSTANCE_READ_AUTHORIZATION =
      Authorization.of(a -> a.processDefinition().readProcessInstance());

  public static final Authorization<SequenceFlowEntity> SEQUENCE_FLOW_READ_AUTHORIZATION =
      Authorization.of(a -> a.processDefinition().readProcessInstance());

  public static final Authorization<RoleEntity> ROLE_READ_AUTHORIZATION =
      Authorization.of(a -> a.role().read());

  public static final Authorization<TenantEntity> TENANT_READ_AUTHORIZATION =
      Authorization.of(a -> a.tenant().read());

  public static final Authorization<UserEntity> USER_READ_AUTHORIZATION =
      Authorization.of(a -> a.user().read());

  public static final Authorization<UserTaskEntity> USER_TASK_READ_AUTHORIZATION =
      Authorization.of(a -> a.processDefinition().readUserTask());

  public static final Authorization<VariableEntity> VARIABLE_READ_AUTHORIZATION =
      Authorization.of(a -> a.processDefinition().readProcessInstance());
}
