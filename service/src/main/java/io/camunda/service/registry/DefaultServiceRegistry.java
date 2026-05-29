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
public final class DefaultServiceRegistry implements ServiceRegistry {

  private final Map<String, AdHocSubProcessActivityServices> adHocSubProcessActivityByTenant;
  private final Map<String, AgentInstanceServices> agentInstanceByTenant;
  private final Map<String, AuditLogServices> auditLogByTenant;
  private final Map<String, AuthorizationServices> authorizationByTenant;
  private final Map<String, BatchOperationServices> batchOperationByTenant;
  private final Map<String, ClockServices> clockByTenant;
  private final Map<String, ClusterVariableServices> clusterVariableByTenant;
  private final Map<String, ConditionalServices> conditionalByTenant;
  private final Map<String, DecisionDefinitionServices> decisionDefinitionByTenant;
  private final Map<String, DecisionInstanceServices> decisionInstanceByTenant;
  private final Map<String, DecisionRequirementsServices> decisionRequirementsByTenant;
  private final Map<String, DocumentServices> documentByTenant;
  private final Map<String, ElementInstanceServices> elementInstanceByTenant;
  private final Map<String, ExpressionServices> expressionByTenant;
  private final Map<String, FormServices> formByTenant;
  private final Map<String, GlobalListenerServices> globalListenerByTenant;
  private final Map<String, GroupServices> groupByTenant;
  private final Map<String, IncidentServices> incidentByTenant;
  private final Map<String, JobServices<?>> jobByTenant;
  private final Map<String, MappingRuleServices> mappingRuleByTenant;
  private final Map<String, MessageServices> messageByTenant;
  private final Map<String, MessageSubscriptionServices> messageSubscriptionByTenant;
  private final Map<String, ProcessDefinitionServices> processDefinitionByTenant;
  private final Map<String, ProcessInstanceServices> processInstanceByTenant;
  private final Map<String, ResourceServices> resourceByTenant;
  private final Map<String, RoleServices> roleByTenant;
  private final Map<String, SignalServices> signalByTenant;
  private final Map<String, TenantServices> tenantByTenant;
  private final Map<String, UsageMetricsServices> usageMetricsByTenant;
  private final Map<String, UserServices> userByTenant;
  private final Map<String, UserTaskServices> userTaskByTenant;
  private final Map<String, VariableServices> variableByTenant;

  // both cluster wide and per physicalTenant
  private final ManagementServices managementServices;
  private final TopologyServices topologyServices;

  public DefaultServiceRegistry(
      final Map<String, AdHocSubProcessActivityServices> adHocSubProcessActivityByTenant,
      final Map<String, AgentInstanceServices> agentInstanceByTenant,
      final Map<String, AuditLogServices> auditLogByTenant,
      final Map<String, AuthorizationServices> authorizationByTenant,
      final Map<String, BatchOperationServices> batchOperationByTenant,
      final Map<String, ClockServices> clockByTenant,
      final Map<String, ClusterVariableServices> clusterVariableByTenant,
      final Map<String, ConditionalServices> conditionalByTenant,
      final Map<String, DecisionDefinitionServices> decisionDefinitionByTenant,
      final Map<String, DecisionInstanceServices> decisionInstanceByTenant,
      final Map<String, DecisionRequirementsServices> decisionRequirementsByTenant,
      final Map<String, DocumentServices> documentByTenant,
      final Map<String, ElementInstanceServices> elementInstanceByTenant,
      final Map<String, ExpressionServices> expressionByTenant,
      final Map<String, FormServices> formByTenant,
      final Map<String, GlobalListenerServices> globalListenerByTenant,
      final Map<String, GroupServices> groupByTenant,
      final Map<String, IncidentServices> incidentByTenant,
      final Map<String, JobServices<?>> jobByTenant,
      final Map<String, MappingRuleServices> mappingRuleByTenant,
      final Map<String, MessageServices> messageByTenant,
      final Map<String, MessageSubscriptionServices> messageSubscriptionByTenant,
      final Map<String, ProcessDefinitionServices> processDefinitionByTenant,
      final Map<String, ProcessInstanceServices> processInstanceByTenant,
      final Map<String, ResourceServices> resourceByTenant,
      final Map<String, RoleServices> roleByTenant,
      final Map<String, SignalServices> signalByTenant,
      final Map<String, TenantServices> tenantByTenant,
      final Map<String, UsageMetricsServices> usageMetricsByTenant,
      final Map<String, UserServices> userByTenant,
      final Map<String, UserTaskServices> userTaskByTenant,
      final Map<String, VariableServices> variableByTenant,
      final ManagementServices managementServices,
      final TopologyServices topologyServices) {
    this.adHocSubProcessActivityByTenant = adHocSubProcessActivityByTenant;
    this.agentInstanceByTenant = agentInstanceByTenant;
    this.auditLogByTenant = auditLogByTenant;
    this.authorizationByTenant = authorizationByTenant;
    this.batchOperationByTenant = batchOperationByTenant;
    this.clockByTenant = clockByTenant;
    this.clusterVariableByTenant = clusterVariableByTenant;
    this.conditionalByTenant = conditionalByTenant;
    this.decisionDefinitionByTenant = decisionDefinitionByTenant;
    this.decisionInstanceByTenant = decisionInstanceByTenant;
    this.decisionRequirementsByTenant = decisionRequirementsByTenant;
    this.documentByTenant = documentByTenant;
    this.elementInstanceByTenant = elementInstanceByTenant;
    this.expressionByTenant = expressionByTenant;
    this.formByTenant = formByTenant;
    this.globalListenerByTenant = globalListenerByTenant;
    this.groupByTenant = groupByTenant;
    this.incidentByTenant = incidentByTenant;
    this.jobByTenant = jobByTenant;
    this.mappingRuleByTenant = mappingRuleByTenant;
    this.messageByTenant = messageByTenant;
    this.messageSubscriptionByTenant = messageSubscriptionByTenant;
    this.processDefinitionByTenant = processDefinitionByTenant;
    this.processInstanceByTenant = processInstanceByTenant;
    this.resourceByTenant = resourceByTenant;
    this.roleByTenant = roleByTenant;
    this.signalByTenant = signalByTenant;
    this.tenantByTenant = tenantByTenant;
    this.usageMetricsByTenant = usageMetricsByTenant;
    this.userByTenant = userByTenant;
    this.userTaskByTenant = userTaskByTenant;
    this.variableByTenant = variableByTenant;
    this.managementServices = managementServices;
    this.topologyServices = topologyServices;
  }

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

  @Override
  public TopologyServices topologyServices() {
    return topologyServices;
  }
}
