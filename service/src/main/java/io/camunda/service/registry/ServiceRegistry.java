/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.registry;

import io.camunda.service.AdHocSubProcessActivityServices;
import io.camunda.service.AgentHistoryServices;
import io.camunda.service.AgentInstanceServices;
import io.camunda.service.AuditLogServices;
import io.camunda.service.AuthorizationServices;
import io.camunda.service.BatchOperationServices;
import io.camunda.service.ClockServices;
import io.camunda.service.ClusterVariableServices;
import io.camunda.service.ConditionalServices;
import io.camunda.service.DecisionDefinitionServices;
import io.camunda.service.DecisionInstanceServices;
import io.camunda.service.DecisionRequirementsServices;
import io.camunda.service.DocumentServices;
import io.camunda.service.ElementInstanceServices;
import io.camunda.service.ExpressionServices;
import io.camunda.service.FormServices;
import io.camunda.service.GlobalListenerServices;
import io.camunda.service.GroupServices;
import io.camunda.service.IncidentServices;
import io.camunda.service.JobServices;
import io.camunda.service.ManagementServices;
import io.camunda.service.MappingRuleServices;
import io.camunda.service.MessageServices;
import io.camunda.service.MessageSubscriptionServices;
import io.camunda.service.ProcessDefinitionServices;
import io.camunda.service.ProcessInstanceServices;
import io.camunda.service.ResourceServices;
import io.camunda.service.RoleServices;
import io.camunda.service.SignalServices;
import io.camunda.service.TenantServices;
import io.camunda.service.TopologyServices;
import io.camunda.service.UsageMetricsServices;
import io.camunda.service.UserServices;
import io.camunda.service.UserTaskServices;
import io.camunda.service.VariableServices;

/**
 * Typed accessor for the per-physical-tenant {@code io.camunda.service.*} instances.
 *
 * <p>Tenant-scoped accessors take a {@code physicalTenantId} and return the service instance whose
 * collaborators (search-client view, broker-request tagging, ...) are already bound to that tenant.
 * Cluster-wide accessors are parameter-less because the underlying service has no per-tenant axis.
 */
public interface ServiceRegistry {

  // -- tenant-scoped --

  AdHocSubProcessActivityServices adHocSubProcessActivityServices(String physicalTenantId);

  AgentHistoryServices agentHistoryServices(String physicalTenantId);

  AgentInstanceServices agentInstanceServices(String physicalTenantId);

  AuditLogServices auditLogServices(String physicalTenantId);

  AuthorizationServices authorizationServices(String physicalTenantId);

  BatchOperationServices batchOperationServices(String physicalTenantId);

  ClockServices clockServices(String physicalTenantId);

  ClusterVariableServices clusterVariableServices(String physicalTenantId);

  ConditionalServices conditionalServices(String physicalTenantId);

  DecisionDefinitionServices decisionDefinitionServices(String physicalTenantId);

  DecisionInstanceServices decisionInstanceServices(String physicalTenantId);

  DecisionRequirementsServices decisionRequirementsServices(String physicalTenantId);

  DocumentServices documentServices(String physicalTenantId);

  ElementInstanceServices elementInstanceServices(String physicalTenantId);

  ExpressionServices expressionServices(String physicalTenantId);

  FormServices formServices(String physicalTenantId);

  GlobalListenerServices globalListenerServices(String physicalTenantId);

  GroupServices groupServices(String physicalTenantId);

  IncidentServices incidentServices(String physicalTenantId);

  <T> JobServices<T> jobServices(String physicalTenantId);

  MappingRuleServices mappingRuleServices(String physicalTenantId);

  MessageServices messageServices(String physicalTenantId);

  MessageSubscriptionServices messageSubscriptionServices(String physicalTenantId);

  ProcessDefinitionServices processDefinitionServices(String physicalTenantId);

  ProcessInstanceServices processInstanceServices(String physicalTenantId);

  ResourceServices resourceServices(String physicalTenantId);

  RoleServices roleServices(String physicalTenantId);

  SignalServices signalServices(String physicalTenantId);

  TenantServices tenantServices(String physicalTenantId);

  TopologyServices topologyServices(String physicalTenantId);

  UsageMetricsServices usageMetricsServices(String physicalTenantId);

  UserServices userServices(String physicalTenantId);

  UserTaskServices userTaskServices(String physicalTenantId);

  VariableServices variableServices(String physicalTenantId);

  // -- cluster-wide --

  ManagementServices managementServices();
}
