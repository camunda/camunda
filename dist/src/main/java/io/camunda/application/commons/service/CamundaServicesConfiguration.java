/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.service;

import io.camunda.application.commons.condition.ConditionalOnAnyHttpGatewayEnabled;
import io.camunda.application.commons.document.CamundaDocumentStoreConfigurationLoader;
import io.camunda.configuration.physicaltenants.PhysicalTenantResolver;
import io.camunda.document.store.SimpleDocumentStoreRegistry;
import io.camunda.gateway.protocol.model.JobActivationResult;
import io.camunda.search.clients.SearchClientsProxy;
import io.camunda.security.auth.BrokerRequestAuthorizationConverter;
import io.camunda.security.configuration.EngineSecurityConfig;
import io.camunda.security.core.authz.AuthorizationChecker;
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
import io.camunda.zeebe.gateway.rest.config.PhysicalTenantRestConfigProvider;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration(proxyBeanMethods = false)
@ConditionalOnAnyHttpGatewayEnabled
public class CamundaServicesConfiguration {

  // -- cluster-wide beans --

  @Bean
  public SecurityContextProvider securityContextProvider() {
    return new SecurityContextProvider();
  }

  // Cluster-wide executor, uses the node's availableProcessors
  @Bean
  public ApiServicesExecutorProvider apiServicesExecutor(
      final GatewayRestConfiguration configuration) {
    return new ApiServicesExecutorProvider(
        configuration.getApiExecutor().getCorePoolSizeMultiplier(),
        configuration.getApiExecutor().getMaxPoolSizeMultiplier(),
        configuration.getApiExecutor().getKeepAliveSeconds(),
        configuration.getApiExecutor().getQueueCapacity());
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
   * 0001-physical-tenant-service-serviceRegistry}.
   */
  @Bean
  public ServiceRegistry serviceRegistry(
      final PhysicalTenantResolver physicalTenantResolver,
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final PasswordEncoder passwordEncoder,
      final ActivateJobsHandler<JobActivationResult> activateJobsHandler,
      final SearchClientsProxy searchClients,
      final AuthorizationChecker authorizationChecker,
      final CamundaSecurityLibraryProperties cslProperties,
      final PhysicalTenantRestConfigProvider physicalTenantRestConfigProvider,
      final BrokerTopologyManager brokerTopologyManager,
      final MeterRegistry meterRegistry,
      final Environment environment,
      final ManagementServices managementServices,
      final ApiServicesExecutorProvider executor) {

    final boolean secondaryStorageEnabled =
        DatabaseTypeUtils.isSecondaryStorageEnabled(environment);

    final var builder = new DefaultServiceRegistry.Builder();
    builder.managementServices(managementServices);

    physicalTenantResolver
        .getAll()
        .forEach(
            (tenantId, tenantConfig) -> {
              final int maxNameFieldLength =
                  physicalTenantRestConfigProvider.forTenant(tenantId).maxNameFieldLength();
              final var search = searchClients.withPhysicalTenant(tenantId);

              // -- per-tenant BrokerRequestAuthorizationConverter --
              // TODO: derive one converter per tenant from per-tenant
              // CamundaSecurityLibraryProperties once available.
              final var converter =
                  new BrokerRequestAuthorizationConverter(
                      new EngineSecurityConfig(
                          cslProperties.getAuthentication(),
                          cslProperties.getAuthorizations().isEnabled(),
                          cslProperties.getMultiTenancy().isChecksEnabled(),
                          cslProperties.getInitialization(),
                          cslProperties.getCompiledIdValidationPattern(),
                          cslProperties.getCompiledGroupIdValidationPattern()));

              // -- per-tenant process cache --
              final var processCacheConfig = tenantConfig.getApi().getRest().getProcessCache();
              final var cacheConfiguration =
                  new ProcessCache.Configuration(
                      processCacheConfig.getMaxSize(),
                      processCacheConfig.getExpirationIdle().toMillis());
              final var processCache =
                  new ProcessCache(
                      cacheConfiguration, search, brokerTopologyManager, meterRegistry);

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

              builder
                  .adHocSubProcessActivityServices(
                      tenantId,
                      new AdHocSubProcessActivityServices(
                          brokerClient, securityContextProvider, executor, converter))
                  .agentInstanceServices(
                      tenantId,
                      new AgentInstanceServices(
                          brokerClient, securityContextProvider, search, executor, converter))
                  .auditLogServices(tenantId, auditLog)
                  .authorizationServices(
                      tenantId,
                      new AuthorizationServices(
                          brokerClient, securityContextProvider, search, executor, converter))
                  .batchOperationServices(
                      tenantId,
                      new BatchOperationServices(
                          brokerClient, securityContextProvider, search, executor, converter))
                  .clockServices(
                      tenantId,
                      new ClockServices(brokerClient, securityContextProvider, executor, converter))
                  .clusterVariableServices(
                      tenantId,
                      new ClusterVariableServices(
                          brokerClient, securityContextProvider, search, executor, converter))
                  .conditionalServices(
                      tenantId,
                      new ConditionalServices(
                          brokerClient, securityContextProvider, executor, converter))
                  .decisionDefinitionServices(
                      tenantId,
                      new DecisionDefinitionServices(
                          brokerClient,
                          securityContextProvider,
                          search,
                          decisionRequirements,
                          executor,
                          converter))
                  .decisionInstanceServices(
                      tenantId,
                      new DecisionInstanceServices(
                          brokerClient, securityContextProvider, search, executor, converter))
                  .decisionRequirementsServices(tenantId, decisionRequirements)
                  .documentServices(
                      tenantId,
                      new DocumentServices(
                          brokerClient,
                          securityContextProvider,
                          new SimpleDocumentStoreRegistry(
                              new CamundaDocumentStoreConfigurationLoader(tenantConfig)),
                          authorizationChecker,
                          cslProperties.getAuthorizations(),
                          executor,
                          converter))
                  .elementInstanceServices(tenantId, elementInstance)
                  .expressionServices(
                      tenantId,
                      new ExpressionServices(
                          brokerClient, securityContextProvider, executor, converter))
                  .formServices(tenantId, form)
                  .globalListenerServices(
                      tenantId,
                      new GlobalListenerServices(
                          brokerClient, securityContextProvider, search, executor, converter))
                  .groupServices(
                      tenantId,
                      new GroupServices(
                          brokerClient, securityContextProvider, search, executor, converter))
                  .incidentServices(tenantId, incident)
                  .jobServices(
                      tenantId,
                      new JobServices<>(
                          brokerClient,
                          securityContextProvider,
                          activateJobsHandler,
                          search,
                          executor,
                          converter,
                          maxNameFieldLength))
                  .mappingRuleServices(
                      tenantId,
                      new MappingRuleServices(
                          brokerClient, securityContextProvider, search, executor, converter))
                  .messageServices(
                      tenantId,
                      new MessageServices(
                          brokerClient,
                          securityContextProvider,
                          executor,
                          converter,
                          maxNameFieldLength))
                  .messageSubscriptionServices(
                      tenantId,
                      new MessageSubscriptionServices(
                          brokerClient, securityContextProvider, search, executor, converter))
                  .processDefinitionServices(tenantId, processDefinition)
                  .processInstanceServices(tenantId, processInstance)
                  .resourceServices(
                      tenantId,
                      new ResourceServices(
                          brokerClient,
                          securityContextProvider,
                          executor,
                          converter,
                          search,
                          search,
                          search,
                          secondaryStorageEnabled))
                  .roleServices(
                      tenantId,
                      new RoleServices(
                          brokerClient, securityContextProvider, search, executor, converter))
                  .signalServices(
                      tenantId,
                      new SignalServices(
                          brokerClient, securityContextProvider, executor, converter))
                  .tenantServices(
                      tenantId,
                      new TenantServices(
                          brokerClient, securityContextProvider, search, executor, converter))
                  .topologyServices(
                      tenantId,
                      new TopologyServices(
                          brokerClient, securityContextProvider, executor, converter))
                  .usageMetricsServices(
                      tenantId,
                      new UsageMetricsServices(
                          brokerClient, securityContextProvider, search, executor, converter))
                  .userServices(
                      tenantId,
                      new UserServices(
                          brokerClient,
                          securityContextProvider,
                          search,
                          passwordEncoder,
                          executor,
                          converter))
                  .userTaskServices(tenantId, userTask)
                  .variableServices(tenantId, variable);
            });

    return builder.build();
  }

  // -- default-tenant service beans --
  //
  // Consumers that are not yet physical-tenant aware (currently the MCP gateway tools) still inject
  // individual {@code *Services} singletons. We expose the default tenant's instances from the
  // serviceRegistry so those consumers keep working until they are migrated to resolve the tenant
  // per
  // request. Making the MCP gateway physical-tenant aware is tracked by
  // https://github.com/camunda/camunda/issues/52573.

  @Bean
  public TopologyServices topologyServices(final ServiceRegistry serviceRegistry) {
    return serviceRegistry.topologyServices(PhysicalTenantResolver.DEFAULT_PHYSICAL_TENANT_ID);
  }

  @Bean
  public ProcessInstanceServices processInstanceServices(final ServiceRegistry serviceRegistry) {
    return serviceRegistry.processInstanceServices(
        PhysicalTenantResolver.DEFAULT_PHYSICAL_TENANT_ID);
  }

  @Bean
  public ProcessDefinitionServices processDefinitionServices(
      final ServiceRegistry serviceRegistry) {
    return serviceRegistry.processDefinitionServices(
        PhysicalTenantResolver.DEFAULT_PHYSICAL_TENANT_ID);
  }

  @Bean
  public UserTaskServices userTaskServices(final ServiceRegistry serviceRegistry) {
    return serviceRegistry.userTaskServices(PhysicalTenantResolver.DEFAULT_PHYSICAL_TENANT_ID);
  }

  @Bean
  public IncidentServices incidentServices(final ServiceRegistry serviceRegistry) {
    return serviceRegistry.incidentServices(PhysicalTenantResolver.DEFAULT_PHYSICAL_TENANT_ID);
  }

  @Bean
  public JobServices<JobActivationResult> jobServices(final ServiceRegistry serviceRegistry) {
    return serviceRegistry.jobServices(PhysicalTenantResolver.DEFAULT_PHYSICAL_TENANT_ID);
  }

  @Bean
  public VariableServices variableServices(final ServiceRegistry serviceRegistry) {
    return serviceRegistry.variableServices(PhysicalTenantResolver.DEFAULT_PHYSICAL_TENANT_ID);
  }

  @Bean
  public MessageServices messageServices(final ServiceRegistry serviceRegistry) {
    return serviceRegistry.messageServices(PhysicalTenantResolver.DEFAULT_PHYSICAL_TENANT_ID);
  }

  @Bean
  public MessageSubscriptionServices messageSubscriptionServices(
      final ServiceRegistry serviceRegistry) {
    return serviceRegistry.messageSubscriptionServices(
        PhysicalTenantResolver.DEFAULT_PHYSICAL_TENANT_ID);
  }

  // This is required by BrokerModuleConfiguration that requires UserServices for Basic auth
  // TODO we need to make it physical tenant aware
  @Bean
  public UserServices userServices(final ServiceRegistry serviceRegistry) {
    return serviceRegistry.userServices(PhysicalTenantResolver.DEFAULT_PHYSICAL_TENANT_ID);
  }
}
