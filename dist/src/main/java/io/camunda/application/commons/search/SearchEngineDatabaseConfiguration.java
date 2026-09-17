/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.search;

import static io.camunda.application.commons.condition.ConditionalOnAnyHttpGatewayEnabled.AnyHttpGatewayEnabledCondition.isAnyHttpGatewayEnabled;

import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.configuration.conditions.ConditionalOnSecondaryStorageType;
import io.camunda.search.connect.tenant.SearchClients;
import io.camunda.search.schema.config.SearchEngineConfiguration;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import io.camunda.zeebe.broker.Broker;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.health.contributor.HealthContributor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
@ConditionalOnSecondaryStorageType({
  SecondaryStorageType.elasticsearch,
  SecondaryStorageType.opensearch
})
public class SearchEngineDatabaseConfiguration {

  /**
   * The initializer exists on every node, but only a node with an HTTP gateway holds startup until
   * a physical tenant is serviceable, hence a flag rather than a bean condition. The flag comes
   * from the same predicate that decides whether the schema readiness indicator joins the readiness
   * group, so the socket and the probe cannot disagree about what an HTTP node is.
   *
   * <p>The recovery check goes through {@link BrokerTopologyManager}, which every node has - broker
   * or gateway-only - so a gateway cannot create the indices a broker was careful not to. It is
   * required rather than optional: the same component scan brings this class and the topology
   * manager in together, so a context holding one without the other is a wiring defect, and
   * defaulting it to "nothing is recovering" would answer that defect by recreating the indices of
   * a tenant mid-restore.
   *
   * <p>The check itself is {@link SchemaInitializationRecoveryCheck}, which asks whether a tenant
   * is recovering or its mode is pending, rather than whether it is recovering only: schema
   * initialization runs once before the node can serve, so a tenant whose mode may never be known
   * has to be initialized rather than waited on forever.
   */
  @Bean
  public SearchEngineSchemaInitializer searchEngineSchemaInitializer(
      @Qualifier("searchEngineConfigurationsByTenant")
          final Map<String, SearchEngineConfiguration> searchEngineConfigurationsByTenant,
      @Qualifier("physicalTenantScopedIndexDescriptors")
          final Map<String, IndexDescriptors> physicalTenantScopedIndexDescriptors,
      final MeterRegistry meterRegistry,
      final Environment environment,
      final BrokerTopologyManager brokerTopologyManager,
      // if present, then it will ensure that the broker is started first
      @Autowired(required = false) final Broker broker) {
    return new SearchEngineSchemaInitializer(
        searchEngineConfigurationsByTenant,
        physicalTenantScopedIndexDescriptors,
        meterRegistry,
        isAnyHttpGatewayEnabled(environment),
        new SchemaInitializationRecoveryCheck(brokerTopologyManager));
  }

  /**
   * Reports the search engine's cluster health for operators, deliberately outside every probe
   * group: a cluster that turns red after startup is worth seeing on {@code /actuator/health}, but
   * not worth taking the node out of rotation or restarting it for, which is what the removed
   * Operate and Tasklist indicators used to do.
   */
  @Bean
  public HealthContributor searchEngineStatusHealthIndicator(
      final SearchClients searchClients,
      @Qualifier("searchEngineConfigurationsByTenant")
          final Map<String, SearchEngineConfiguration> searchEngineConfigurationsByTenant) {
    return SearchEngineStatusHealthIndicator.forPhysicalTenants(
        searchClients, searchEngineConfigurationsByTenant);
  }
}
