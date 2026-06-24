/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.authorization;

import static io.camunda.security.api.model.authz.AuthorizationResourceType.COMPONENT;
import static io.camunda.security.api.model.authz.PermissionType.ACCESS;
import static io.camunda.security.api.model.authz.PermissionType.READ_TASK_LISTENER;

import io.camunda.search.entities.AgentInstanceEntity;
import io.camunda.search.entities.AgentInstanceHistoryEntity;
import io.camunda.search.entities.AuditLogEntity;
import io.camunda.search.entities.AuthorizationEntity;
import io.camunda.search.entities.BatchOperationEntity;
import io.camunda.search.entities.ClusterVariableEntity;
import io.camunda.search.entities.DecisionDefinitionEntity;
import io.camunda.search.entities.DecisionInstanceEntity;
import io.camunda.search.entities.DecisionRequirementsEntity;
import io.camunda.search.entities.DeployedResourceEntity;
import io.camunda.search.entities.FlowNodeInstanceEntity;
import io.camunda.search.entities.FormEntity;
import io.camunda.search.entities.GlobalListenerEntity;
import io.camunda.search.entities.GroupEntity;
import io.camunda.search.entities.IncidentEntity;
import io.camunda.search.entities.JobEntity;
import io.camunda.search.entities.MappingRuleEntity;
import io.camunda.search.entities.MessageSubscriptionEntity;
import io.camunda.search.entities.ProcessDefinitionEntity;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.entities.RoleEntity;
import io.camunda.search.entities.TenantEntity;
import io.camunda.search.entities.UserEntity;
import io.camunda.search.entities.UserTaskEntity;
import io.camunda.search.entities.VariableEntity;
import io.camunda.security.core.auth.RequiredAuthorization;

public abstract class Authorizations {

  public static final RequiredAuthorization<AgentInstanceEntity> AGENT_INSTANCE_READ_AUTHORIZATION =
      RequiredAuthorization.of(a -> a.processDefinition().readProcessInstance());

  public static final RequiredAuthorization<AgentInstanceHistoryEntity>
      AGENT_HISTORY_READ_AUTHORIZATION =
          RequiredAuthorization.of(a -> a.processDefinition().readProcessInstance());

  public static final RequiredAuthorization<AuthorizationEntity> AUTHORIZATION_READ_AUTHORIZATION =
      RequiredAuthorization.of(a -> a.authorization().read());

  public static final RequiredAuthorization<BatchOperationEntity>
      BATCH_OPERATION_READ_AUTHORIZATION = RequiredAuthorization.of(a -> a.batchOperation().read());

  public static final RequiredAuthorization<Object> COMPONENT_ACCESS_AUTHORIZATION =
      RequiredAuthorization.of(a -> a.resourceType(COMPONENT).permissionType(ACCESS));

  public static final RequiredAuthorization<DecisionDefinitionEntity>
      DECISION_DEFINITION_READ_AUTHORIZATION =
          RequiredAuthorization.of(a -> a.decisionDefinition().readDecisionDefinition());

  public static final RequiredAuthorization<DecisionInstanceEntity>
      DECISION_INSTANCE_READ_AUTHORIZATION =
          RequiredAuthorization.of(a -> a.decisionDefinition().readDecisionInstance());

  public static final RequiredAuthorization<DecisionRequirementsEntity>
      DECISION_REQUIREMENTS_READ_AUTHORIZATION =
          RequiredAuthorization.of(a -> a.decisionRequirementsDefinition().read());

  public static final RequiredAuthorization<FlowNodeInstanceEntity>
      ELEMENT_INSTANCE_READ_AUTHORIZATION =
          RequiredAuthorization.of(a -> a.processDefinition().readProcessInstance());

  public static final RequiredAuthorization<FormEntity> FORM_READ_AUTHORIZATION =
      RequiredAuthorization.of(a -> a.resource().read());

  public static final RequiredAuthorization<GroupEntity> GROUP_READ_AUTHORIZATION =
      RequiredAuthorization.of(a -> a.group().read());

  public static final RequiredAuthorization<IncidentEntity> INCIDENT_READ_AUTHORIZATION =
      RequiredAuthorization.of(a -> a.processDefinition().readProcessInstance());

  public static final RequiredAuthorization<JobEntity> JOB_READ_AUTHORIZATION =
      RequiredAuthorization.of(a -> a.processDefinition().readProcessInstance());

  public static final RequiredAuthorization<MappingRuleEntity> MAPPING_RULE_READ_AUTHORIZATION =
      RequiredAuthorization.of(a -> a.mappingRule().read());

  public static final RequiredAuthorization<MessageSubscriptionEntity>
      MESSAGE_SUBSCRIPTION_READ_AUTHORIZATION =
          RequiredAuthorization.of(a -> a.processDefinition().readProcessInstance());

  public static final RequiredAuthorization<ProcessDefinitionEntity>
      PROCESS_DEFINITION_READ_AUTHORIZATION =
          RequiredAuthorization.of(a -> a.processDefinition().readProcessDefinition());

  public static final RequiredAuthorization<DeployedResourceEntity> RESOURCE_READ_AUTHORIZATION =
      RequiredAuthorization.of(a -> a.resource().read());

  public static final RequiredAuthorization<ProcessInstanceEntity>
      PROCESS_INSTANCE_READ_AUTHORIZATION =
          RequiredAuthorization.of(a -> a.processDefinition().readProcessInstance());

  public static final RequiredAuthorization<ProcessInstanceEntity>
      PROCESS_INSTANCE_UPDATE_AUTHORIZATION =
          RequiredAuthorization.of(a -> a.processDefinition().updateProcessInstance());

  public static final RequiredAuthorization<RoleEntity> ROLE_READ_AUTHORIZATION =
      RequiredAuthorization.of(a -> a.role().read());

  public static final RequiredAuthorization<TenantEntity> TENANT_READER_AUTHORIZATION =
      RequiredAuthorization.of(a -> a.tenant().read());

  public static final RequiredAuthorization<UserEntity> USER_READ_AUTHORIZATION =
      RequiredAuthorization.of(a -> a.user().read());

  public static final RequiredAuthorization<UserTaskEntity>
      PROCESS_DEFINITION_READ_USER_TASK_AUTHORIZATION =
          RequiredAuthorization.of(a -> a.processDefinition().readUserTask());

  public static final RequiredAuthorization<UserTaskEntity> USER_TASK_READ_AUTHORIZATION =
      RequiredAuthorization.of(a -> a.userTask().read());

  public static final RequiredAuthorization<UserTaskEntity>
      USER_TASK_READ_BY_PROPERTIES_AUTHORIZATION =
          RequiredAuthorization.of(
              a ->
                  a.userTask()
                      .read()
                      .authorizedByAssignee()
                      .or()
                      .authorizedByCandidateUsers()
                      .or()
                      .authorizedByCandidateGroups());

  public static final RequiredAuthorization<VariableEntity> VARIABLE_READ_AUTHORIZATION =
      RequiredAuthorization.of(a -> a.processDefinition().readProcessInstance());

  public static final RequiredAuthorization<AuditLogEntity> AUDIT_LOG_READ_AUTHORIZATION =
      RequiredAuthorization.of(a -> a.auditLog().read());

  public static final RequiredAuthorization<AuditLogEntity>
      AUDIT_LOG_READ_PROCESS_INSTANCE_AUTHORIZATION =
          RequiredAuthorization.of(a -> a.processDefinition().readProcessInstance().transitive());

  public static final RequiredAuthorization<AuditLogEntity> AUDIT_LOG_READ_USER_TASK_AUTHORIZATION =
      RequiredAuthorization.of(a -> a.processDefinition().readUserTask().transitive());

  public static final RequiredAuthorization<ClusterVariableEntity>
      CLUSTER_VARIABLE_READ_AUTHORIZATION =
          RequiredAuthorization.of(a -> a.clusterVariable().read());

  public static final RequiredAuthorization<GlobalListenerEntity>
      GLOBAL_TASK_LISTENER_READ_AUTHORIZATION =
          RequiredAuthorization.of(a -> a.globalListener().permissionType(READ_TASK_LISTENER));

  public static final RequiredAuthorization<ProcessInstanceEntity>
      PROCESS_DEFINITION_DELETE_PROCESS_INSTANCE_AUTHORIZATION =
          RequiredAuthorization.of(a -> a.processDefinition().deleteProcessInstance());

  public static final RequiredAuthorization<DecisionInstanceEntity>
      DECISION_DEFINITION_DELETE_DECISION_INSTANCE_AUTHORIZATION =
          RequiredAuthorization.of(a -> a.decisionDefinition().deleteDecisionInstance());
}
