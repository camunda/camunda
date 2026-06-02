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
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

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
  @SuppressWarnings("unchecked")
  public <T> JobServices<T> jobServices(final String physicalTenantId) {
    return (JobServices<T>) byTenant(jobByTenant, physicalTenantId);
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

  /** Creates a {@link DefaultServiceRegistry} using the fluent {@link Builder} API. */
  public static DefaultServiceRegistry of(final Consumer<Builder> spec) {
    final var builder = new Builder();
    spec.accept(builder);
    return builder.build();
  }

  /**
   * Fluent builder for {@link DefaultServiceRegistry}. Each service type has a registration method
   * that takes an explicit {@code physicalTenantId} and the service instance. Multiple tenants can
   * be registered by calling the same method repeatedly with different tenant ids.
   *
   * <p>Usage in tests:
   *
   * <pre>{@code
   * var registry = DefaultServiceRegistry.of(b -> b
   *     .processInstanceServices("default", processInstanceServices)
   *     .userServices("default", userServices));
   * }</pre>
   *
   * <p>Usage in production (CamundaServicesConfiguration):
   *
   * <pre>{@code
   * var registry = DefaultServiceRegistry.of(b -> {
   *   for (var tenantId : tenantIds) {
   *     b.processInstanceServices(tenantId, buildProcessInstanceServices(tenantId));
   *     // ... other services
   *   }
   *   b.managementServices(mgmt);
   * });
   * }</pre>
   */
  public static final class Builder {

    private final Map<String, AdHocSubProcessActivityServices> adHocSubProcessActivityByTenant =
        new HashMap<>();
    private final Map<String, AgentInstanceServices> agentInstanceByTenant = new HashMap<>();
    private final Map<String, AuditLogServices> auditLogByTenant = new HashMap<>();
    private final Map<String, AuthorizationServices> authorizationByTenant = new HashMap<>();
    private final Map<String, BatchOperationServices> batchOperationByTenant = new HashMap<>();
    private final Map<String, ClockServices> clockByTenant = new HashMap<>();
    private final Map<String, ClusterVariableServices> clusterVariableByTenant = new HashMap<>();
    private final Map<String, ConditionalServices> conditionalByTenant = new HashMap<>();
    private final Map<String, DecisionDefinitionServices> decisionDefinitionByTenant =
        new HashMap<>();
    private final Map<String, DecisionInstanceServices> decisionInstanceByTenant = new HashMap<>();
    private final Map<String, DecisionRequirementsServices> decisionRequirementsByTenant =
        new HashMap<>();
    private final Map<String, DocumentServices> documentByTenant = new HashMap<>();
    private final Map<String, ElementInstanceServices> elementInstanceByTenant = new HashMap<>();
    private final Map<String, ExpressionServices> expressionByTenant = new HashMap<>();
    private final Map<String, FormServices> formByTenant = new HashMap<>();
    private final Map<String, GlobalListenerServices> globalListenerByTenant = new HashMap<>();
    private final Map<String, GroupServices> groupByTenant = new HashMap<>();
    private final Map<String, IncidentServices> incidentByTenant = new HashMap<>();
    private final Map<String, JobServices<?>> jobByTenant = new HashMap<>();
    private final Map<String, MappingRuleServices> mappingRuleByTenant = new HashMap<>();
    private final Map<String, MessageServices> messageByTenant = new HashMap<>();
    private final Map<String, MessageSubscriptionServices> messageSubscriptionByTenant =
        new HashMap<>();
    private final Map<String, ProcessDefinitionServices> processDefinitionByTenant =
        new HashMap<>();
    private final Map<String, ProcessInstanceServices> processInstanceByTenant = new HashMap<>();
    private final Map<String, ResourceServices> resourceByTenant = new HashMap<>();
    private final Map<String, RoleServices> roleByTenant = new HashMap<>();
    private final Map<String, SignalServices> signalByTenant = new HashMap<>();
    private final Map<String, TenantServices> tenantByTenant = new HashMap<>();
    private final Map<String, TopologyServices> topologyByTenant = new HashMap<>();
    private final Map<String, UsageMetricsServices> usageMetricsByTenant = new HashMap<>();
    private final Map<String, UserServices> userByTenant = new HashMap<>();
    private final Map<String, UserTaskServices> userTaskByTenant = new HashMap<>();
    private final Map<String, VariableServices> variableByTenant = new HashMap<>();
    private ManagementServices managementServices;

    public Builder adHocSubProcessActivityServices(
        final String tenantId, final AdHocSubProcessActivityServices service) {
      adHocSubProcessActivityByTenant.put(tenantId, service);
      return this;
    }

    public Builder agentInstanceServices(
        final String tenantId, final AgentInstanceServices service) {
      agentInstanceByTenant.put(tenantId, service);
      return this;
    }

    public Builder auditLogServices(final String tenantId, final AuditLogServices service) {
      auditLogByTenant.put(tenantId, service);
      return this;
    }

    public Builder authorizationServices(
        final String tenantId, final AuthorizationServices service) {
      authorizationByTenant.put(tenantId, service);
      return this;
    }

    public Builder batchOperationServices(
        final String tenantId, final BatchOperationServices service) {
      batchOperationByTenant.put(tenantId, service);
      return this;
    }

    public Builder clockServices(final String tenantId, final ClockServices service) {
      clockByTenant.put(tenantId, service);
      return this;
    }

    public Builder clusterVariableServices(
        final String tenantId, final ClusterVariableServices service) {
      clusterVariableByTenant.put(tenantId, service);
      return this;
    }

    public Builder conditionalServices(final String tenantId, final ConditionalServices service) {
      conditionalByTenant.put(tenantId, service);
      return this;
    }

    public Builder decisionDefinitionServices(
        final String tenantId, final DecisionDefinitionServices service) {
      decisionDefinitionByTenant.put(tenantId, service);
      return this;
    }

    public Builder decisionInstanceServices(
        final String tenantId, final DecisionInstanceServices service) {
      decisionInstanceByTenant.put(tenantId, service);
      return this;
    }

    public Builder decisionRequirementsServices(
        final String tenantId, final DecisionRequirementsServices service) {
      decisionRequirementsByTenant.put(tenantId, service);
      return this;
    }

    public Builder documentServices(final String tenantId, final DocumentServices service) {
      documentByTenant.put(tenantId, service);
      return this;
    }

    public Builder elementInstanceServices(
        final String tenantId, final ElementInstanceServices service) {
      elementInstanceByTenant.put(tenantId, service);
      return this;
    }

    public Builder expressionServices(final String tenantId, final ExpressionServices service) {
      expressionByTenant.put(tenantId, service);
      return this;
    }

    public Builder formServices(final String tenantId, final FormServices service) {
      formByTenant.put(tenantId, service);
      return this;
    }

    public Builder globalListenerServices(
        final String tenantId, final GlobalListenerServices service) {
      globalListenerByTenant.put(tenantId, service);
      return this;
    }

    public Builder groupServices(final String tenantId, final GroupServices service) {
      groupByTenant.put(tenantId, service);
      return this;
    }

    public Builder incidentServices(final String tenantId, final IncidentServices service) {
      incidentByTenant.put(tenantId, service);
      return this;
    }

    public Builder jobServices(final String tenantId, final JobServices<?> service) {
      jobByTenant.put(tenantId, service);
      return this;
    }

    public Builder mappingRuleServices(final String tenantId, final MappingRuleServices service) {
      mappingRuleByTenant.put(tenantId, service);
      return this;
    }

    public Builder messageServices(final String tenantId, final MessageServices service) {
      messageByTenant.put(tenantId, service);
      return this;
    }

    public Builder messageSubscriptionServices(
        final String tenantId, final MessageSubscriptionServices service) {
      messageSubscriptionByTenant.put(tenantId, service);
      return this;
    }

    public Builder processDefinitionServices(
        final String tenantId, final ProcessDefinitionServices service) {
      processDefinitionByTenant.put(tenantId, service);
      return this;
    }

    public Builder processInstanceServices(
        final String tenantId, final ProcessInstanceServices service) {
      processInstanceByTenant.put(tenantId, service);
      return this;
    }

    public Builder resourceServices(final String tenantId, final ResourceServices service) {
      resourceByTenant.put(tenantId, service);
      return this;
    }

    public Builder roleServices(final String tenantId, final RoleServices service) {
      roleByTenant.put(tenantId, service);
      return this;
    }

    public Builder signalServices(final String tenantId, final SignalServices service) {
      signalByTenant.put(tenantId, service);
      return this;
    }

    public Builder tenantServices(final String tenantId, final TenantServices service) {
      tenantByTenant.put(tenantId, service);
      return this;
    }

    public Builder topologyServices(final String tenantId, final TopologyServices service) {
      topologyByTenant.put(tenantId, service);
      return this;
    }

    public Builder usageMetricsServices(final String tenantId, final UsageMetricsServices service) {
      usageMetricsByTenant.put(tenantId, service);
      return this;
    }

    public Builder userServices(final String tenantId, final UserServices service) {
      userByTenant.put(tenantId, service);
      return this;
    }

    public Builder userTaskServices(final String tenantId, final UserTaskServices service) {
      userTaskByTenant.put(tenantId, service);
      return this;
    }

    public Builder variableServices(final String tenantId, final VariableServices service) {
      variableByTenant.put(tenantId, service);
      return this;
    }

    public Builder managementServices(final ManagementServices service) {
      managementServices = service;
      return this;
    }

    public DefaultServiceRegistry build() {
      return new DefaultServiceRegistry(
          Map.copyOf(adHocSubProcessActivityByTenant),
          Map.copyOf(agentInstanceByTenant),
          Map.copyOf(auditLogByTenant),
          Map.copyOf(authorizationByTenant),
          Map.copyOf(batchOperationByTenant),
          Map.copyOf(clockByTenant),
          Map.copyOf(clusterVariableByTenant),
          Map.copyOf(conditionalByTenant),
          Map.copyOf(decisionDefinitionByTenant),
          Map.copyOf(decisionInstanceByTenant),
          Map.copyOf(decisionRequirementsByTenant),
          Map.copyOf(documentByTenant),
          Map.copyOf(elementInstanceByTenant),
          Map.copyOf(expressionByTenant),
          Map.copyOf(formByTenant),
          Map.copyOf(globalListenerByTenant),
          Map.copyOf(groupByTenant),
          Map.copyOf(incidentByTenant),
          Map.copyOf(jobByTenant),
          Map.copyOf(mappingRuleByTenant),
          Map.copyOf(messageByTenant),
          Map.copyOf(messageSubscriptionByTenant),
          Map.copyOf(processDefinitionByTenant),
          Map.copyOf(processInstanceByTenant),
          Map.copyOf(resourceByTenant),
          Map.copyOf(roleByTenant),
          Map.copyOf(signalByTenant),
          Map.copyOf(tenantByTenant),
          Map.copyOf(topologyByTenant),
          Map.copyOf(usageMetricsByTenant),
          Map.copyOf(userByTenant),
          Map.copyOf(userTaskByTenant),
          Map.copyOf(variableByTenant),
          managementServices);
    }
  }
}
