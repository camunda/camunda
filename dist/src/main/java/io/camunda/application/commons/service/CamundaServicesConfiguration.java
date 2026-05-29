/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.service;

import io.camunda.application.commons.condition.ConditionalOnAnyHttpGatewayEnabled;
import io.camunda.configuration.physicaltenants.PhysicalTenantResolver;
import io.camunda.document.store.EnvironmentConfigurationLoader;
import io.camunda.document.store.SimpleDocumentStoreRegistry;
import io.camunda.gateway.protocol.model.JobActivationResult;
import io.camunda.search.clients.AgentInstanceSearchClient;
import io.camunda.search.clients.AuditLogSearchClient;
import io.camunda.search.clients.AuthorizationSearchClient;
import io.camunda.search.clients.BatchOperationSearchClient;
import io.camunda.search.clients.ClusterVariableSearchClient;
import io.camunda.search.clients.DecisionDefinitionSearchClient;
import io.camunda.search.clients.DecisionInstanceSearchClient;
import io.camunda.search.clients.DecisionRequirementSearchClient;
import io.camunda.search.clients.DeployedResourceSearchClient;
import io.camunda.search.clients.FlowNodeInstanceSearchClient;
import io.camunda.search.clients.FormSearchClient;
import io.camunda.search.clients.GroupSearchClient;
import io.camunda.search.clients.IncidentSearchClient;
import io.camunda.search.clients.JobSearchClient;
import io.camunda.search.clients.MappingRuleSearchClient;
import io.camunda.search.clients.MessageSubscriptionSearchClient;
import io.camunda.search.clients.ProcessDefinitionSearchClient;
import io.camunda.search.clients.ProcessInstanceSearchClient;
import io.camunda.search.clients.RoleSearchClient;
import io.camunda.search.clients.SearchClientsProxy;
import io.camunda.search.clients.SequenceFlowSearchClient;
import io.camunda.search.clients.TenantSearchClient;
import io.camunda.search.clients.UsageMetricsSearchClient;
import io.camunda.search.clients.UserSearchClient;
import io.camunda.search.clients.UserTaskSearchClient;
import io.camunda.search.clients.VariableSearchClient;
import io.camunda.security.auth.BrokerRequestAuthorizationConverter;
import io.camunda.security.configuration.EngineSecurityConfig;
import io.camunda.security.impl.AuthorizationChecker;
import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import io.camunda.service.AdHocSubProcessActivityServices;
import io.camunda.service.AgentInstanceServices;
import io.camunda.service.ApiServicesExecutorProvider;
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
import io.camunda.service.cache.ProcessCache;
import io.camunda.service.registry.DefaultServiceRegistry;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.spring.utils.DatabaseTypeUtils;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.gateway.impl.job.ActivateJobsHandler;
import io.camunda.zeebe.gateway.rest.config.GatewayRestConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration(proxyBeanMethods = false)
@ConditionalOnAnyHttpGatewayEnabled
public class CamundaServicesConfiguration {

  // -- per-tenant beans --

  // TODO: derive one converter per tenant from per-tenant SecurityConfiguration once available.
  @Bean
  public Map<String, BrokerRequestAuthorizationConverter> converterByTenant(
      final PhysicalTenantResolver resolver, final CamundaSecurityLibraryProperties cslProperties) {
    final var out = new LinkedHashMap<String, BrokerRequestAuthorizationConverter>();
    resolver
        .getAll()
        .keySet()
        .forEach(
            id ->
                out.put(
                    id,
                    new BrokerRequestAuthorizationConverter(
                        new EngineSecurityConfig(
                            cslProperties.getAuthentication(),
                            cslProperties.getAuthorizations().isEnabled(),
                            cslProperties.getMultiTenancy().isChecksEnabled(),
                            cslProperties.getInitialization(),
                            cslProperties.getCompiledIdValidationPattern(),
                            cslProperties.getCompiledGroupIdValidationPattern()))));
    return Map.copyOf(out);
  }

  @Bean
  public Map<String, ApiServicesExecutorProvider> executorByTenant(
      final PhysicalTenantResolver resolver) {
    final var out = new LinkedHashMap<String, ApiServicesExecutorProvider>();
    resolver
        .getAll()
        .forEach(
            (id, cfg) -> {
              final var api = cfg.getApi().getRest().getExecutor();
              out.put(
                  id,
                  new ApiServicesExecutorProvider(
                      api.getCorePoolSizeMultiplier(), api.getMaxPoolSizeMultiplier(),
                      api.getKeepAlive().toMillis(), api.getQueueCapacity()));
            });
    return Map.copyOf(out);
  }

  @Bean
  public Map<String, ProcessCache> processCacheByTenant(
      final PhysicalTenantResolver resolver,
      final GatewayRestConfiguration configuration,
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final Map<String, ApiServicesExecutorProvider> executorByTenant,
      final Map<String, BrokerRequestAuthorizationConverter> converterByTenant,
      final SearchClientsProxy searchClients,
      final BrokerTopologyManager brokerTopologyManager,
      final MeterRegistry meterRegistry) {
    final var out = new LinkedHashMap<String, ProcessCache>();
    resolver
        .getAll()
        .keySet()
        .forEach(
            id -> {
              final var search = searchClients.withPhysicalTenant(id);
              final var executor = executorByTenant.get(id);
              final var converter = converterByTenant.get(id);
              final var formSvc =
                  new FormServices(
                      brokerClient, securityContextProvider, search, executor, converter);
              final var processDefSvc =
                  new ProcessDefinitionServices(
                      brokerClient, securityContextProvider, search, formSvc, executor, converter);
              final var cacheConfiguration =
                  new ProcessCache.Configuration(
                      configuration.getProcessCache().getMaxSize(),
                      configuration.getProcessCache().getExpirationIdleMillis());
              out.put(
                  id,
                  new ProcessCache(
                      cacheConfiguration, processDefSvc, brokerTopologyManager, meterRegistry));
            });
    return Map.copyOf(out);
  }

  // -- cluster-wide beans --

  @Bean
  public SecurityContextProvider securityContextProvider() {
    return new SecurityContextProvider();
  }

  // Cluster-wide executor for JobServices and TopologyServices.
  @Bean
  public ApiServicesExecutorProvider apiServicesExecutor(
      final GatewayRestConfiguration configuration) {
    return new ApiServicesExecutorProvider(
        configuration.getApiExecutor().getCorePoolSizeMultiplier(),
        configuration.getApiExecutor().getMaxPoolSizeMultiplier(),
        configuration.getApiExecutor().getKeepAliveSeconds(),
        configuration.getApiExecutor().getQueueCapacity());
  }

  @Bean
  public UsageMetricsServices usageMetricsServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final UsageMetricsSearchClient usageMetricsSearchClient,
      final ApiServicesExecutorProvider executorProvider,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter) {
    return new UsageMetricsServices(
        brokerClient,
        securityContextProvider,
        usageMetricsSearchClient,
        executorProvider,
        brokerRequestAuthorizationConverter);
  }

  @Bean
  public JobServices<JobActivationResult> jobServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final ActivateJobsHandler<JobActivationResult> activateJobsHandler,
      final JobSearchClient jobSearchClient,
      final ApiServicesExecutorProvider executorProvider,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter,
      final GatewayRestConfiguration gatewayRestConfiguration) {
    return new JobServices<>(
        brokerClient,
        securityContextProvider,
        activateJobsHandler,
        jobSearchClient,
        executorProvider,
        brokerRequestAuthorizationConverter,
        gatewayRestConfiguration.getMaxNameFieldLength());
  }

  @Bean
  public DecisionDefinitionServices decisionDefinitionServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final DecisionDefinitionSearchClient decisionDefinitionSearchClient,
      final DecisionRequirementsServices decisionRequirementsServices,
      final ApiServicesExecutorProvider executorProvider,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter) {
    return new DecisionDefinitionServices(
        brokerClient,
        securityContextProvider,
        decisionDefinitionSearchClient,
        decisionRequirementsServices,
        executorProvider,
        brokerRequestAuthorizationConverter);
  }

  @Bean
  public DecisionInstanceServices decisionInstanceServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final DecisionInstanceSearchClient decisionInstanceSearchClient,
      final ApiServicesExecutorProvider executorProvider,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter) {
    return new DecisionInstanceServices(
        brokerClient,
        securityContextProvider,
        decisionInstanceSearchClient,
        executorProvider,
        brokerRequestAuthorizationConverter);
  }

  @Bean
  public ProcessDefinitionServices processDefinitionServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final ProcessDefinitionSearchClient processDefinitionSearchClient,
      final FormServices formServices,
      final ApiServicesExecutorProvider executorProvider,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter) {
    return new ProcessDefinitionServices(
        brokerClient,
        securityContextProvider,
        processDefinitionSearchClient,
        formServices,
        executorProvider,
        brokerRequestAuthorizationConverter);
  }

  @Bean
  public ProcessInstanceServices processInstanceServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final ProcessInstanceSearchClient processInstanceSearchClient,
      final SequenceFlowSearchClient sequenceFlowSearchClient,
      final IncidentServices incidentServices,
      final ApiServicesExecutorProvider executorProvider,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter,
      final GatewayRestConfiguration gatewayRestConfiguration) {
    return new ProcessInstanceServices(
        brokerClient,
        securityContextProvider,
        processInstanceSearchClient,
        sequenceFlowSearchClient,
        incidentServices,
        executorProvider,
        brokerRequestAuthorizationConverter,
        gatewayRestConfiguration.getMaxNameFieldLength());
  }

  @Bean
  public DecisionRequirementsServices decisionRequirementsServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final DecisionRequirementSearchClient decisionRequirementSearchClient,
      final ApiServicesExecutorProvider executorProvider,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter) {
    return new DecisionRequirementsServices(
        brokerClient,
        securityContextProvider,
        decisionRequirementSearchClient,
        executorProvider,
        brokerRequestAuthorizationConverter);
  }

  @Bean
  public ElementInstanceServices elementInstanceServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final FlowNodeInstanceSearchClient flowNodeInstanceSearchClient,
      final ProcessCache processCache,
      final IncidentServices incidentServices,
      final ApiServicesExecutorProvider executorProvider,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter) {
    return new ElementInstanceServices(
        brokerClient,
        securityContextProvider,
        flowNodeInstanceSearchClient,
        processCache,
        incidentServices,
        executorProvider,
        brokerRequestAuthorizationConverter);
  }

  @Bean
  public AdHocSubProcessActivityServices adHocSubProcessActivityServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final ApiServicesExecutorProvider executorProvider,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter) {
    return new AdHocSubProcessActivityServices(
        brokerClient,
        securityContextProvider,
        executorProvider,
        brokerRequestAuthorizationConverter);
  }

  @Bean
  public AgentInstanceServices agentInstanceServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final AgentInstanceSearchClient agentInstanceSearchClient,
      final ApiServicesExecutorProvider executorProvider,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter) {
    return new AgentInstanceServices(
        brokerClient,
        securityContextProvider,
        agentInstanceSearchClient,
        executorProvider,
        brokerRequestAuthorizationConverter);
  }

  @Bean
  public AuditLogServices auditLogServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final AuditLogSearchClient auditLogSearchClient,
      final ApiServicesExecutorProvider executorProvider,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter) {
    return new AuditLogServices(
        brokerClient,
        securityContextProvider,
        auditLogSearchClient,
        executorProvider,
        brokerRequestAuthorizationConverter);
  }

  @Bean
  public IncidentServices incidentServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final IncidentSearchClient incidentSearchClient,
      final ApiServicesExecutorProvider executorProvider,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter) {
    return new IncidentServices(
        brokerClient,
        securityContextProvider,
        incidentSearchClient,
        executorProvider,
        brokerRequestAuthorizationConverter);
  }

  @Bean
  public RoleServices roleServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final RoleSearchClient roleSearchClient,
      final ApiServicesExecutorProvider executorProvider,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter) {
    return new RoleServices(
        brokerClient,
        securityContextProvider,
        roleSearchClient,
        executorProvider,
        brokerRequestAuthorizationConverter);
  }

  @Bean
  public TenantServices tenantServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final TenantSearchClient tenantSearchClient,
      final ApiServicesExecutorProvider executorProvider,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter) {
    return new TenantServices(
        brokerClient,
        securityContextProvider,
        tenantSearchClient,
        executorProvider,
        brokerRequestAuthorizationConverter);
  }

  @Bean
  public GroupServices groupServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final GroupSearchClient groupSearchClient,
      final ApiServicesExecutorProvider executorProvider,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter) {
    return new GroupServices(
        brokerClient,
        securityContextProvider,
        groupSearchClient,
        executorProvider,
        brokerRequestAuthorizationConverter);
  }

  @Bean
  public UserServices userServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final UserSearchClient userSearchClient,
      final PasswordEncoder passwordEncoder,
      final ApiServicesExecutorProvider executorProvider,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter) {
    return new UserServices(
        brokerClient,
        securityContextProvider,
        userSearchClient,
        passwordEncoder,
        executorProvider,
        brokerRequestAuthorizationConverter);
  }

  @Bean
  public UserTaskServices userTaskServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final UserTaskSearchClient userTaskSearchClient,
      final FormServices formServices,
      final ElementInstanceServices elementInstanceServices,
      final VariableServices variableServices,
      final AuditLogServices auditLogServices,
      final ProcessCache processCache,
      final ApiServicesExecutorProvider executorProvider,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter,
      final GatewayRestConfiguration gatewayRestConfiguration) {
    return new UserTaskServices(
        brokerClient,
        securityContextProvider,
        userTaskSearchClient,
        formServices,
        elementInstanceServices,
        variableServices,
        auditLogServices,
        processCache,
        executorProvider,
        brokerRequestAuthorizationConverter,
        gatewayRestConfiguration.getMaxNameFieldLength());
  }

  @Bean
  public VariableServices variableServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final VariableSearchClient variableSearchClient,
      final ApiServicesExecutorProvider executorProvider,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter) {
    return new VariableServices(
        brokerClient,
        securityContextProvider,
        variableSearchClient,
        executorProvider,
        brokerRequestAuthorizationConverter);
  }

  @Bean
  public ClusterVariableServices clusterVariableServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final ClusterVariableSearchClient clusterVariableSearchClient,
      final ApiServicesExecutorProvider executorProvider,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter) {
    return new ClusterVariableServices(
        brokerClient,
        securityContextProvider,
        clusterVariableSearchClient,
        executorProvider,
        brokerRequestAuthorizationConverter);
  }

  @Bean
  public ExpressionServices expressionServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final ApiServicesExecutorProvider executorProvider,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter) {
    return new ExpressionServices(
        brokerClient,
        securityContextProvider,
        executorProvider,
        brokerRequestAuthorizationConverter);
  }

  @Bean
  public MessageServices messageServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final ApiServicesExecutorProvider executorProvider,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter,
      final GatewayRestConfiguration gatewayRestConfiguration) {
    return new MessageServices(
        brokerClient,
        securityContextProvider,
        executorProvider,
        brokerRequestAuthorizationConverter,
        gatewayRestConfiguration.getMaxNameFieldLength());
  }

  @Bean
  public DocumentServices documentServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final AuthorizationChecker authorizationChecker,
      final CamundaSecurityLibraryProperties cslProperties,
      final ApiServicesExecutorProvider executorProvider,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter) {
    return new DocumentServices(
        brokerClient,
        securityContextProvider,
        new SimpleDocumentStoreRegistry(new EnvironmentConfigurationLoader()),
        authorizationChecker,
        cslProperties.getAuthorizations(),
        executorProvider,
        brokerRequestAuthorizationConverter);
  }

  @Bean
  public AuthorizationServices authorizationServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final AuthorizationSearchClient authorizationSearchClient,
      final ApiServicesExecutorProvider executorProvider,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter) {
    return new AuthorizationServices(
        brokerClient,
        securityContextProvider,
        authorizationSearchClient,
        executorProvider,
        brokerRequestAuthorizationConverter);
  }

  @Bean
  public ClockServices clockServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final ApiServicesExecutorProvider executorProvider,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter) {
    return new ClockServices(
        brokerClient,
        securityContextProvider,
        executorProvider,
        brokerRequestAuthorizationConverter);
  }

  @Bean
  public ResourceServices resourceServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final ApiServicesExecutorProvider executorProvider,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter,
      final ProcessDefinitionSearchClient processDefinitionSearchClient,
      final DecisionRequirementSearchClient decisionRequirementSearchClient,
      final DeployedResourceSearchClient deployedResourceSearchClient,
      final Environment environment) {
    return new ResourceServices(
        brokerClient,
        securityContextProvider,
        executorProvider,
        brokerRequestAuthorizationConverter,
        processDefinitionSearchClient,
        decisionRequirementSearchClient,
        deployedResourceSearchClient,
        DatabaseTypeUtils.isSecondaryStorageEnabled(environment));
  }

  @Bean
  public SignalServices signalServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final ApiServicesExecutorProvider executorProvider,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter) {
    return new SignalServices(
        brokerClient,
        securityContextProvider,
        executorProvider,
        brokerRequestAuthorizationConverter);
  }

  @Bean
  public BatchOperationServices batchOperationServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final BatchOperationSearchClient batchOperationSearchClient,
      final ApiServicesExecutorProvider executorProvider,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter) {
    return new BatchOperationServices(
        brokerClient,
        securityContextProvider,
        batchOperationSearchClient,
        executorProvider,
        brokerRequestAuthorizationConverter);
  }

  @Bean
  public FormServices formServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final FormSearchClient formSearchClient,
      final ApiServicesExecutorProvider executorProvider,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter) {
    return new FormServices(
        brokerClient,
        securityContextProvider,
        formSearchClient,
        executorProvider,
        brokerRequestAuthorizationConverter);
  }

  @Bean
  public MappingRuleServices mappingRuleServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final MappingRuleSearchClient mappingRuleSearchClient,
      final ApiServicesExecutorProvider executorProvider,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter) {
    return new MappingRuleServices(
        brokerClient,
        securityContextProvider,
        mappingRuleSearchClient,
        executorProvider,
        brokerRequestAuthorizationConverter);
  }

  @Bean
  public MessageSubscriptionServices messageSubscriptionServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final MessageSubscriptionSearchClient messageSubscriptionSearchClient,
      final ApiServicesExecutorProvider executorProvider,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter) {
    return new MessageSubscriptionServices(
        brokerClient,
        securityContextProvider,
        messageSubscriptionSearchClient,
        executorProvider,
        brokerRequestAuthorizationConverter);
  }

  @Bean
  public ConditionalServices conditionalEventServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final ApiServicesExecutorProvider executorProvider,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter) {
    return new ConditionalServices(
        brokerClient,
        securityContextProvider,
        executorProvider,
        brokerRequestAuthorizationConverter);
  }

  @Bean
  public TopologyServices topologyServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final ApiServicesExecutorProvider executorProvider,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter) {
    return new TopologyServices(
        brokerClient,
        securityContextProvider,
        executorProvider,
        brokerRequestAuthorizationConverter);
  }

  /**
   * Builds one instance of each tenant-scoped {@code io.camunda.service.*} class per physical
   * tenant and exposes them through a typed {@link ServiceRegistry}. Each per-tenant instance is
   * bound to its tenant's search-client view (via {@link
   * SearchClientsProxy#withPhysicalTenant(String)}) so read queries are automatically scoped to
   * that tenant's secondary storage.
   *
   * <p>Cluster-wide services ({@code TopologyServices}, {@code ManagementServices}) are shared
   * singletons injected from their own configuration classes.
   *
   * <p>For v1 the authorization converter and executor are cloned per-tenant from the global config
   * (no isolation yet); the process cache uses a tenant-scoped search view. See ADR {@code
   * 0001-physical-tenant-service-registry}.
   */
  @Bean
  public ServiceRegistry serviceRegistry(
      final PhysicalTenantResolver physicalTenantResolver,
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final PasswordEncoder passwordEncoder,
      final ActivateJobsHandler<JobActivationResult> activateJobsHandler,
      final Map<String, ApiServicesExecutorProvider> executorByTenant,
      final Map<String, BrokerRequestAuthorizationConverter> converterByTenant,
      final Map<String, ProcessCache> processCacheByTenant,
      final SearchClientsProxy searchClients,
      final AuthorizationChecker authorizationChecker,
      final CamundaSecurityLibraryProperties cslProperties,
      final GatewayRestConfiguration gatewayRestConfiguration,
      final Environment environment,
      final TopologyServices topologyServices,
      final ManagementServices managementServices) {

    final int maxNameFieldLength = gatewayRestConfiguration.getMaxNameFieldLength();
    final boolean secondaryStorageEnabled =
        DatabaseTypeUtils.isSecondaryStorageEnabled(environment);

    final Map<String, AdHocSubProcessActivityServices> adHocByTenant = new LinkedHashMap<>();
    final Map<String, AgentInstanceServices> agentInstanceByTenant = new LinkedHashMap<>();
    final Map<String, AuditLogServices> auditLogByTenant = new LinkedHashMap<>();
    final Map<String, AuthorizationServices> authorizationByTenant = new LinkedHashMap<>();
    final Map<String, BatchOperationServices> batchOperationByTenant = new LinkedHashMap<>();
    final Map<String, ClockServices> clockByTenant = new LinkedHashMap<>();
    final Map<String, ClusterVariableServices> clusterVariableByTenant = new LinkedHashMap<>();
    final Map<String, ConditionalServices> conditionalByTenant = new LinkedHashMap<>();
    final Map<String, DecisionDefinitionServices> decisionDefinitionByTenant =
        new LinkedHashMap<>();
    final Map<String, DecisionInstanceServices> decisionInstanceByTenant = new LinkedHashMap<>();
    final Map<String, DecisionRequirementsServices> decisionRequirementsByTenant =
        new LinkedHashMap<>();
    final Map<String, DocumentServices> documentByTenant = new LinkedHashMap<>();
    final Map<String, ElementInstanceServices> elementInstanceByTenant = new LinkedHashMap<>();
    final Map<String, ExpressionServices> expressionByTenant = new LinkedHashMap<>();
    final Map<String, FormServices> formByTenant = new LinkedHashMap<>();
    final Map<String, GlobalListenerServices> globalListenerByTenant = new LinkedHashMap<>();
    final Map<String, GroupServices> groupByTenant = new LinkedHashMap<>();
    final Map<String, IncidentServices> incidentByTenant = new LinkedHashMap<>();
    final Map<String, JobServices<?>> jobByTenant = new LinkedHashMap<>();
    final Map<String, MappingRuleServices> mappingRuleByTenant = new LinkedHashMap<>();
    final Map<String, MessageServices> messageByTenant = new LinkedHashMap<>();
    final Map<String, MessageSubscriptionServices> messageSubscriptionByTenant =
        new LinkedHashMap<>();
    final Map<String, ProcessDefinitionServices> processDefinitionByTenant = new LinkedHashMap<>();
    final Map<String, ProcessInstanceServices> processInstanceByTenant = new LinkedHashMap<>();
    final Map<String, ResourceServices> resourceByTenant = new LinkedHashMap<>();
    final Map<String, RoleServices> roleByTenant = new LinkedHashMap<>();
    final Map<String, SignalServices> signalByTenant = new LinkedHashMap<>();
    final Map<String, TenantServices> tenantByTenant = new LinkedHashMap<>();
    final Map<String, UsageMetricsServices> usageMetricsByTenant = new LinkedHashMap<>();
    final Map<String, UserServices> userByTenant = new LinkedHashMap<>();
    final Map<String, UserTaskServices> userTaskByTenant = new LinkedHashMap<>();
    final Map<String, VariableServices> variableByTenant = new LinkedHashMap<>();

    physicalTenantResolver
        .getAll()
        .keySet()
        .forEach(
            tenantId -> {
              final var search = searchClients.withPhysicalTenant(tenantId);
              final var executor = executorByTenant.get(tenantId);
              final var converter = converterByTenant.get(tenantId);
              final var processCache = processCacheByTenant.get(tenantId);

              // -- leaf services (no service-to-service dependencies) --
              final var form =
                  new FormServices(
                      brokerClient, securityContextProvider, search, executor, converter);
              final var incident =
                  new IncidentServices(
                      brokerClient, securityContextProvider, search, executor, converter);
              final var variable =
                  new VariableServices(
                      brokerClient, securityContextProvider, search, executor, converter);
              final var auditLog =
                  new AuditLogServices(
                      brokerClient, securityContextProvider, search, executor, converter);
              final var decisionRequirements =
                  new DecisionRequirementsServices(
                      brokerClient, securityContextProvider, search, executor, converter);

              // -- mid-tier services (depend on leaf services) --
              final var elementInstance =
                  new ElementInstanceServices(
                      brokerClient,
                      securityContextProvider,
                      search,
                      processCache,
                      incident,
                      executor,
                      converter);
              final var processDefinition =
                  new ProcessDefinitionServices(
                      brokerClient, securityContextProvider, search, form, executor, converter);

              // -- top-level services --
              final var processInstance =
                  new ProcessInstanceServices(
                      brokerClient,
                      securityContextProvider,
                      search,
                      search,
                      incident,
                      executor,
                      converter,
                      maxNameFieldLength);
              final var userTask =
                  new UserTaskServices(
                      brokerClient,
                      securityContextProvider,
                      search,
                      form,
                      elementInstance,
                      variable,
                      auditLog,
                      processCache,
                      executor,
                      converter,
                      maxNameFieldLength);

              adHocByTenant.put(
                  tenantId,
                  new AdHocSubProcessActivityServices(
                      brokerClient, securityContextProvider, executor, converter));
              agentInstanceByTenant.put(
                  tenantId,
                  new AgentInstanceServices(
                      brokerClient, securityContextProvider, search, executor, converter));
              auditLogByTenant.put(tenantId, auditLog);
              authorizationByTenant.put(
                  tenantId,
                  new AuthorizationServices(
                      brokerClient, securityContextProvider, search, executor, converter));
              batchOperationByTenant.put(
                  tenantId,
                  new BatchOperationServices(
                      brokerClient, securityContextProvider, search, executor, converter));
              clockByTenant.put(
                  tenantId,
                  new ClockServices(brokerClient, securityContextProvider, executor, converter));
              clusterVariableByTenant.put(
                  tenantId,
                  new ClusterVariableServices(
                      brokerClient, securityContextProvider, search, executor, converter));
              conditionalByTenant.put(
                  tenantId,
                  new ConditionalServices(
                      brokerClient, securityContextProvider, executor, converter));
              decisionDefinitionByTenant.put(
                  tenantId,
                  new DecisionDefinitionServices(
                      brokerClient,
                      securityContextProvider,
                      search,
                      decisionRequirements,
                      executor,
                      converter));
              decisionInstanceByTenant.put(
                  tenantId,
                  new DecisionInstanceServices(
                      brokerClient, securityContextProvider, search, executor, converter));
              decisionRequirementsByTenant.put(tenantId, decisionRequirements);
              documentByTenant.put(
                  tenantId,
                  new DocumentServices(
                      brokerClient,
                      securityContextProvider,
                      new SimpleDocumentStoreRegistry(new EnvironmentConfigurationLoader()),
                      authorizationChecker,
                      cslProperties.getAuthorizations(),
                      executor,
                      converter));
              elementInstanceByTenant.put(tenantId, elementInstance);
              expressionByTenant.put(
                  tenantId,
                  new ExpressionServices(
                      brokerClient, securityContextProvider, executor, converter));
              formByTenant.put(tenantId, form);
              globalListenerByTenant.put(
                  tenantId,
                  new GlobalListenerServices(
                      brokerClient, securityContextProvider, search, executor, converter));
              groupByTenant.put(
                  tenantId,
                  new GroupServices(
                      brokerClient, securityContextProvider, search, executor, converter));
              incidentByTenant.put(tenantId, incident);
              jobByTenant.put(
                  tenantId,
                  new JobServices<>(
                      brokerClient,
                      securityContextProvider,
                      activateJobsHandler,
                      search,
                      executor,
                      converter,
                      maxNameFieldLength));
              mappingRuleByTenant.put(
                  tenantId,
                  new MappingRuleServices(
                      brokerClient, securityContextProvider, search, executor, converter));
              messageByTenant.put(
                  tenantId,
                  new MessageServices(
                      brokerClient,
                      securityContextProvider,
                      executor,
                      converter,
                      maxNameFieldLength));
              messageSubscriptionByTenant.put(
                  tenantId,
                  new MessageSubscriptionServices(
                      brokerClient, securityContextProvider, search, executor, converter));
              processDefinitionByTenant.put(tenantId, processDefinition);
              processInstanceByTenant.put(tenantId, processInstance);
              resourceByTenant.put(
                  tenantId,
                  new ResourceServices(
                      brokerClient,
                      securityContextProvider,
                      executor,
                      converter,
                      search,
                      search,
                      search,
                      secondaryStorageEnabled));
              roleByTenant.put(
                  tenantId,
                  new RoleServices(
                      brokerClient, securityContextProvider, search, executor, converter));
              signalByTenant.put(
                  tenantId,
                  new SignalServices(brokerClient, securityContextProvider, executor, converter));
              tenantByTenant.put(
                  tenantId,
                  new TenantServices(
                      brokerClient, securityContextProvider, search, executor, converter));
              usageMetricsByTenant.put(
                  tenantId,
                  new UsageMetricsServices(
                      brokerClient, securityContextProvider, search, executor, converter));
              userByTenant.put(
                  tenantId,
                  new UserServices(
                      brokerClient,
                      securityContextProvider,
                      search,
                      passwordEncoder,
                      executor,
                      converter));
              userTaskByTenant.put(tenantId, userTask);
              variableByTenant.put(tenantId, variable);
            });

    return new DefaultServiceRegistry(
        adHocByTenant,
        agentInstanceByTenant,
        auditLogByTenant,
        authorizationByTenant,
        batchOperationByTenant,
        clockByTenant,
        clusterVariableByTenant,
        conditionalByTenant,
        decisionDefinitionByTenant,
        decisionInstanceByTenant,
        decisionRequirementsByTenant,
        documentByTenant,
        elementInstanceByTenant,
        expressionByTenant,
        formByTenant,
        globalListenerByTenant,
        groupByTenant,
        incidentByTenant,
        jobByTenant,
        mappingRuleByTenant,
        messageByTenant,
        messageSubscriptionByTenant,
        processDefinitionByTenant,
        processInstanceByTenant,
        resourceByTenant,
        roleByTenant,
        signalByTenant,
        tenantByTenant,
        usageMetricsByTenant,
        userByTenant,
        userTaskByTenant,
        variableByTenant,
        managementServices,
        topologyServices);
  }
}
