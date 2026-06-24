/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.bootstrap;

import io.atomix.cluster.messaging.ManagedMessagingService;
import io.camunda.configuration.api.physicaltenants.PhysicalTenantIds;
import io.camunda.identity.sdk.IdentityConfiguration;
import io.camunda.search.clients.SearchClientsProxy;
import io.camunda.security.api.context.OidcClaimsProvider;
import io.camunda.security.api.model.config.AuthenticationConfiguration;
import io.camunda.security.auth.BrokerRequestAuthorizationConverter;
import io.camunda.security.configuration.EngineSecurityConfig;
import io.camunda.service.UserServices;
import io.camunda.zeebe.broker.PartitionListener;
import io.camunda.zeebe.broker.PartitionRaftListener;
import io.camunda.zeebe.broker.SpringBrokerBridge;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.clustering.ClusterServicesImpl;
import io.camunda.zeebe.broker.exporter.repo.ExporterRepository;
import io.camunda.zeebe.broker.jobstream.JobStreamService;
import io.camunda.zeebe.broker.partitioning.PartitionManager;
import io.camunda.zeebe.broker.partitioning.topology.ClusterConfigurationService;
import io.camunda.zeebe.broker.system.EmbeddedGatewayService;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.management.BrokerAdminServiceImpl;
import io.camunda.zeebe.broker.system.management.CheckpointSchedulingService;
import io.camunda.zeebe.broker.system.monitoring.BrokerHealthCheckService;
import io.camunda.zeebe.broker.system.monitoring.DiskSpaceUsageMonitor;
import io.camunda.zeebe.broker.transport.adminapi.AdminApiRequestHandler;
import io.camunda.zeebe.broker.transport.snapshotapi.SnapshotApiRequestHandler;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbResources;
import io.camunda.zeebe.dynamic.nodeid.NodeIdProvider;
import io.camunda.zeebe.protocol.impl.encoding.BrokerInfo;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.transport.impl.AtomixServerTransport;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.agrona.concurrent.SnowflakeIdGenerator;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * Context that is utilized during broker startup and shutdown process. It contains dependencies
 * that are needed during the startup/shutdown. It is a modifiable context and will be updated
 * during startup or shutdown.
 */
public interface BrokerStartupContext {

  BrokerInfo getBrokerInfo();

  BrokerCfg getBrokerConfiguration();

  IdentityConfiguration getIdentityConfiguration();

  SpringBrokerBridge getSpringBrokerBridge();

  ActorSchedulingService getActorSchedulingService();

  ConcurrencyControl getConcurrencyControl();

  BrokerHealthCheckService getHealthCheckService();

  SearchClientsProxy getSearchClientsProxy();

  void addPartitionListener(PartitionListener partitionListener);

  void removePartitionListener(PartitionListener partitionListener);

  void addPartitionRaftListener(PartitionRaftListener partitionListener);

  void removePartitionRaftListener(PartitionRaftListener partitionListener);

  List<PartitionListener> getPartitionListeners();

  List<PartitionRaftListener> getPartitionRaftListeners();

  ClusterServicesImpl getClusterServices();

  AdminApiRequestHandler getAdminApiService();

  void setAdminApiService(AdminApiRequestHandler adminApiService);

  AtomixServerTransport getGatewayBrokerTransport();

  void setGatewayBrokerTransport(AtomixServerTransport gatewayBrokerTransport);

  ManagedMessagingService getApiMessagingService();

  void setApiMessagingService(ManagedMessagingService commandApiMessagingService);

  EmbeddedGatewayService getEmbeddedGatewayService();

  void setEmbeddedGatewayService(EmbeddedGatewayService embeddedGatewayService);

  DiskSpaceUsageMonitor getDiskSpaceUsageMonitor();

  void setDiskSpaceUsageMonitor(DiskSpaceUsageMonitor diskSpaceUsageMonitor);

  ExporterRepository getExporterRepository();

  /** Returns all currently registered partition managers, keyed by physical tenant ID. */
  Map<String, PartitionManager> getPartitionManagers();

  /** Registers a partition manager for the given physical tenant ID. */
  void addPartitionManager(String physicalTenantId, PartitionManager partitionManager);

  /** Deregisters the partition manager for the given physical tenant ID. */
  void removePartitionManager(String physicalTenantId);

  /**
   * Returns the broker-wide shared RocksDB cache and write buffer manager. Allocated once per
   * broker and shared across every physical tenant's partition manager.
   */
  RocksDbResources getRocksDbResources();

  void setRocksDbResources(RocksDbResources sharedRocksDbResources);

  BrokerAdminServiceImpl getBrokerAdminService();

  void setBrokerAdminService(final BrokerAdminServiceImpl brokerAdminService);

  JobStreamService getJobStreamService();

  void setJobStreamService(final JobStreamService jobStreamService);

  ClusterConfigurationService getClusterConfigurationService();

  void setClusterConfigurationService(ClusterConfigurationService clusterConfigurationService);

  BrokerClient getBrokerClient();

  Duration getShutdownTimeout();

  SnowflakeIdGenerator getRequestIdGenerator();

  void setRequestIdGenerator(SnowflakeIdGenerator requestIdGenerator);

  MeterRegistry getMeterRegistry();

  /** Returns the security configuration for the {@code default} physical tenant. */
  EngineSecurityConfig getSecurityConfiguration();

  /**
   * Returns the security configuration for the given physical tenant.
   *
   * @throws IllegalArgumentException if the physical tenant id is unknown
   */
  EngineSecurityConfig getSecurityConfiguration(String physicalTenantId);

  Function<String, UserServices> getUserServicesForTenant();

  PasswordEncoder getPasswordEncoder();

  Function<AuthenticationConfiguration, JwtDecoder> getJwtDecoderFactory();

  Function<AuthenticationConfiguration, OidcClaimsProvider> getOidcClaimsProviderFactory();

  SnapshotApiRequestHandler getSnapshotApiRequestHandler();

  void setSnapshotApiRequestHandler(SnapshotApiRequestHandler snapshotApiRequestHandler);

  /**
   * Returns the request authorization converter for the given physical tenant.
   *
   * @throws IllegalArgumentException if the physical tenant id is unknown
   */
  BrokerRequestAuthorizationConverter getBrokerRequestAuthorizationConverter(
      String physicalTenantId);

  CheckpointSchedulingService getCheckpointSchedulingService();

  void setCheckpointSchedulingService(CheckpointSchedulingService checkpointSchedulingService);

  NodeIdProvider getNodeIdProvider();

  /** Returns the physical tenant IDs this broker should run. */
  PhysicalTenantIds getPhysicalTenantIds();
}
