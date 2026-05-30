/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.registry;

import io.camunda.service.AdHocSubProcessActivityServices;
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
import java.util.Map;

/**
 * Default {@link ServiceRegistry} backed by one {@code Map<physicalTenantId, service>} per
 * tenant-scoped service type plus direct references for the cluster-wide services. All maps are
 * populated once at startup (see {@code CamundaServicesConfiguration#serviceRegistry}).
 */
public record DefaultServiceRegistry(
    Map<String, AdHocSubProcessActivityServices> adHocSubProcessActivityByTenant,
    Map<String, AgentInstanceServices> agentInstanceByTenant,
    Map<String, AuditLogServices> auditLogByTenant,
    Map<String, AuthorizationServices> authorizationByTenant,
    Map<String, BatchOperationServices> batchOperationByTenant,
    Map<String, ClockServices> clockByTenant,
    Map<String, ClusterVariableServices> clusterVariableByTenant,
    Map<String, ConditionalServices> conditionalByTenant,
    Map<String, DecisionDefinitionServices> decisionDefinitionByTenant,
    Map<String, DecisionInstanceServices> decisionInstanceByTenant,
    Map<String, DecisionRequirementsServices> decisionRequirementsByTenant,
    Map<String, DocumentServices> documentByTenant,
    Map<String, ElementInstanceServices> elementInstanceByTenant,
    Map<String, ExpressionServices> expressionByTenant,
    Map<String, FormServices> formByTenant,
    Map<String, GlobalListenerServices> globalListenerByTenant,
    Map<String, GroupServices> groupByTenant,
    Map<String, IncidentServices> incidentByTenant,
    Map<String, JobServices<?>> jobByTenant,
    Map<String, MappingRuleServices> mappingRuleByTenant,
    Map<String, MessageServices> messageByTenant,
    Map<String, MessageSubscriptionServices> messageSubscriptionByTenant,
    Map<String, ProcessDefinitionServices> processDefinitionByTenant,
    Map<String, ProcessInstanceServices> processInstanceByTenant,
    Map<String, ResourceServices> resourceByTenant,
    Map<String, RoleServices> roleByTenant,
    Map<String, SignalServices> signalByTenant,
    Map<String, TenantServices> tenantByTenant,
    Map<String, TopologyServices> topologyByTenant,
    Map<String, UsageMetricsServices> usageMetricsByTenant,
    Map<String, UserServices> userByTenant,
    Map<String, UserTaskServices> userTaskByTenant,
    Map<String, VariableServices> variableByTenant,
    ManagementServices managementServices)
    implements ServiceRegistry {

  private static <S> S byTenant(final Map<String, S> byTenant, final String physicalTenantId) {
    final S service = byTenant.get(physicalTenantId);
    if (service == null) {
      throw new IllegalArgumentException("Unknown physical tenant id '" + physicalTenantId + "'");
    }
    return service;
  }

  @Override
  public AdHocSubProcessActivityServices adHocSubProcessActivityServices(
      final String physicalTenantId) {
    return byTenant(adHocSubProcessActivityByTenant, physicalTenantId);
  }

  @Override
  public AgentInstanceServices agentInstanceServices(final String physicalTenantId) {
    return byTenant(agentInstanceByTenant, physicalTenantId);
  }

  @Override
  public AuditLogServices auditLogServices(final String physicalTenantId) {
    return byTenant(auditLogByTenant, physicalTenantId);
  }

  @Override
  public AuthorizationServices authorizationServices(final String physicalTenantId) {
    return byTenant(authorizationByTenant, physicalTenantId);
  }

  @Override
  public BatchOperationServices batchOperationServices(final String physicalTenantId) {
    return byTenant(batchOperationByTenant, physicalTenantId);
  }

  @Override
  public ClockServices clockServices(final String physicalTenantId) {
    return byTenant(clockByTenant, physicalTenantId);
  }

  @Override
  public ClusterVariableServices clusterVariableServices(final String physicalTenantId) {
    return byTenant(clusterVariableByTenant, physicalTenantId);
  }

  @Override
  public ConditionalServices conditionalServices(final String physicalTenantId) {
    return byTenant(conditionalByTenant, physicalTenantId);
  }

  @Override
  public DecisionDefinitionServices decisionDefinitionServices(final String physicalTenantId) {
    return byTenant(decisionDefinitionByTenant, physicalTenantId);
  }

  @Override
  public DecisionInstanceServices decisionInstanceServices(final String physicalTenantId) {
    return byTenant(decisionInstanceByTenant, physicalTenantId);
  }

  @Override
  public DecisionRequirementsServices decisionRequirementsServices(final String physicalTenantId) {
    return byTenant(decisionRequirementsByTenant, physicalTenantId);
  }

  @Override
  public DocumentServices documentServices(final String physicalTenantId) {
    return byTenant(documentByTenant, physicalTenantId);
  }

  @Override
  public ElementInstanceServices elementInstanceServices(final String physicalTenantId) {
    return byTenant(elementInstanceByTenant, physicalTenantId);
  }

  @Override
  public ExpressionServices expressionServices(final String physicalTenantId) {
    return byTenant(expressionByTenant, physicalTenantId);
  }

  @Override
  public FormServices formServices(final String physicalTenantId) {
    return byTenant(formByTenant, physicalTenantId);
  }

  @Override
  public GlobalListenerServices globalListenerServices(final String physicalTenantId) {
    return byTenant(globalListenerByTenant, physicalTenantId);
  }

  @Override
  public GroupServices groupServices(final String physicalTenantId) {
    return byTenant(groupByTenant, physicalTenantId);
  }

  @Override
  public IncidentServices incidentServices(final String physicalTenantId) {
    return byTenant(incidentByTenant, physicalTenantId);
  }

  @Override
  public JobServices<?> jobServices(final String physicalTenantId) {
    return byTenant(jobByTenant, physicalTenantId);
  }

  @Override
  public MappingRuleServices mappingRuleServices(final String physicalTenantId) {
    return byTenant(mappingRuleByTenant, physicalTenantId);
  }

  @Override
  public MessageServices messageServices(final String physicalTenantId) {
    return byTenant(messageByTenant, physicalTenantId);
  }

  @Override
  public MessageSubscriptionServices messageSubscriptionServices(final String physicalTenantId) {
    return byTenant(messageSubscriptionByTenant, physicalTenantId);
  }

  @Override
  public ProcessDefinitionServices processDefinitionServices(final String physicalTenantId) {
    return byTenant(processDefinitionByTenant, physicalTenantId);
  }

  @Override
  public ProcessInstanceServices processInstanceServices(final String physicalTenantId) {
    return byTenant(processInstanceByTenant, physicalTenantId);
  }

  @Override
  public ResourceServices resourceServices(final String physicalTenantId) {
    return byTenant(resourceByTenant, physicalTenantId);
  }

  @Override
  public RoleServices roleServices(final String physicalTenantId) {
    return byTenant(roleByTenant, physicalTenantId);
  }

  @Override
  public SignalServices signalServices(final String physicalTenantId) {
    return byTenant(signalByTenant, physicalTenantId);
  }

  @Override
  public TenantServices tenantServices(final String physicalTenantId) {
    return byTenant(tenantByTenant, physicalTenantId);
  }

  @Override
  public TopologyServices topologyServices(final String physicalTenantId) {
    return byTenant(topologyByTenant, physicalTenantId);
  }

  @Override
  public UsageMetricsServices usageMetricsServices(final String physicalTenantId) {
    return byTenant(usageMetricsByTenant, physicalTenantId);
  }

  @Override
  public UserServices userServices(final String physicalTenantId) {
    return byTenant(userByTenant, physicalTenantId);
  }

  @Override
  public UserTaskServices userTaskServices(final String physicalTenantId) {
    return byTenant(userTaskByTenant, physicalTenantId);
  }

  @Override
  public VariableServices variableServices(final String physicalTenantId) {
    return byTenant(variableByTenant, physicalTenantId);
  }

  @Override
  public ManagementServices managementServices() {
    return managementServices;
  }
}
