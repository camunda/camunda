/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore;

import io.camunda.application.commons.configuration.WorkingDirectoryConfiguration.WorkingDirectory;
import io.camunda.configuration.Camunda;
import io.camunda.configuration.beanoverrides.BrokerBasedPropertiesOverride;
import io.camunda.configuration.beans.BrokerBasedProperties;
import io.camunda.configuration.physicaltenants.PhysicalTenantResolver;
import io.camunda.service.TenantRestoreEnvironment;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.dynamic.nodeid.NodeIdProvider;
import io.camunda.zeebe.dynamic.nodeid.fs.DataDirectoryProvider;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.NullMarked;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@NullMarked
class PhysicalTenantRestoreConfigurations {

  /**
   * @param dataDirectoryProvider not read here, but required: creating it is what resolves the root
   *     configuration's data directory, which every other tenant's configuration copies below.
   */
  @Bean
  PhysicalTenantBrokerConfigurations physicalTenantConfigurations(
      final PhysicalTenantResolver physicalTenantResolver,
      final Camunda rootCamunda,
      final BrokerBasedProperties rootConfiguration,
      final NodeIdProvider nodeIdProvider,
      final WorkingDirectory workingDirectory,
      final DataDirectoryProvider dataDirectoryProvider) {
    final var nodeInstance = nodeIdProvider.currentNodeInstance();
    final var brokerBase = workingDirectory.path().toAbsolutePath().toString();
    final var dataDirectory = rootConfiguration.getData().getDirectory();

    final Map<String, BrokerCfg> configurations = new LinkedHashMap<>();
    physicalTenantResolver
        .getAll()
        .forEach(
            (physicalTenantId, camunda) -> {
              if (camunda == rootCamunda) {
                configurations.put(physicalTenantId, rootConfiguration);
                return;
              }
              final var configuration = BrokerBasedPropertiesOverride.convert(camunda);
              final var cluster = configuration.getCluster();
              cluster.setNodeId(nodeInstance.id());
              cluster.setNodeVersion(nodeInstance.version().version());
              // The working directory, not the data directory: init() fans this base out to
              // network/cluster/threads/data/exporters/gateway to resolve their relative paths.
              configuration.init(brokerBase);
              // Applied after init(), which would otherwise resolve this tenant's own configured
              // data directory against the base above.
              configuration.getData().setDirectory(dataDirectory);
              configurations.put(physicalTenantId, configuration);
            });
    return new PhysicalTenantBrokerConfigurations(configurations);
  }

  /**
   * Each physical tenant's database type and continuous-backup setting.
   *
   * <p>Read from {@link Camunda}, not {@link BrokerCfg}: the legacy config type these restore beans
   * otherwise deal in does not carry either field. Both are deployment-time choices that may
   * legitimately differ per tenant — {@code databaseType} is constrained to agree across tenants
   * only up to Elasticsearch/OpenSearch mixing (see {@code
   * SecondaryStorageTypeHomogeneityValidation}), and nothing constrains {@code continuousBackups}
   * at all — so validating one tenant's restore request against another tenant's (or the root's)
   * values is a real, reachable mistake, not a theoretical one.
   */
  @Bean
  PhysicalTenantRestoreEnvironments physicalTenantRestoreEnvironments(
      final PhysicalTenantResolver physicalTenantResolver) {
    return new PhysicalTenantRestoreEnvironments(
        physicalTenantResolver.mapValues(
            camunda ->
                new TenantRestoreEnvironment(
                    camunda.getData().getSecondaryStorage().getType().name(),
                    camunda.getData().getPrimaryStorage().getBackup().isContinuous())));
  }

  /** The {@link TenantRestoreEnvironment} of every configured physical tenant. */
  record PhysicalTenantRestoreEnvironments(Map<String, TenantRestoreEnvironment> byPhysicalTenant) {

    PhysicalTenantRestoreEnvironments {
      byPhysicalTenant = Map.copyOf(byPhysicalTenant);
    }

    TenantRestoreEnvironment forPhysicalTenant(final String physicalTenantId) {
      final var environment = byPhysicalTenant.get(physicalTenantId);
      if (environment == null) {
        throw new IllegalArgumentException(
            "No restore environment for physical tenant '%s'".formatted(physicalTenantId));
      }
      return environment;
    }
  }

  /**
   * The {@link BrokerCfg} of every configured physical tenant, keyed by physical tenant id.
   *
   * <p>A holder rather than a bare {@code Map<String, BrokerCfg>} bean, which Spring would not
   * deliver: a {@code Map<String, T>} injection point is resolved by collecting every bean of type
   * {@code T} under its own bean name, and {@code BrokerBasedProperties extends BrokerCfg}, so such
   * an injection point would quietly receive the single root configuration keyed by its bean name
   * instead of this map. ({@code Map<String, RdbmsMapperBundle>} gets away with being a bare map
   * only because no individual bean of that type exists for Spring to collect.)
   */
  record PhysicalTenantBrokerConfigurations(Map<String, BrokerCfg> configurations) {

    PhysicalTenantBrokerConfigurations {
      if (configurations.isEmpty()) {
        throw new IllegalArgumentException("Expected at least one physical tenant, but got none");
      }
      configurations = Map.copyOf(configurations);
    }

    Set<String> physicalTenantIds() {
      return configurations.keySet();
    }

    BrokerCfg forPhysicalTenant(final String physicalTenantId) {
      final var configuration = configurations.get(physicalTenantId);
      if (configuration == null) {
        throw new IllegalArgumentException(
            "No configuration for physical tenant '%s'".formatted(physicalTenantId));
      }
      return configuration;
    }
  }
}
