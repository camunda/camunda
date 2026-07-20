/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.bootstrap;

import io.atomix.cluster.messaging.ManagedMessagingService;
import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.search.clients.SearchClientsProxy;
import io.camunda.security.api.context.OidcClaimsProvider;
import io.camunda.security.api.model.config.AuthenticationConfiguration;
import io.camunda.service.UserServices;
import io.camunda.zeebe.broker.PartitionListener;
import io.camunda.zeebe.broker.PartitionRaftListener;
import io.camunda.zeebe.broker.SpringBrokerBridge;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.clustering.ClusterServicesImpl;
import io.camunda.zeebe.broker.jobstream.JobStreamService;
import io.camunda.zeebe.broker.partitioning.PartitionManager;
import io.camunda.zeebe.broker.partitioning.topology.ClusterConfigurationService;
import io.camunda.zeebe.broker.system.EmbeddedGatewayService;
import io.camunda.zeebe.broker.system.PhysicalTenantContext;
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
import java.util.function.IntFunction;
import org.agrona.concurrent.SnowflakeIdGenerator;
import org.jspecify.annotations.Nullable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * Context that is utilized during broker startup and shutdown process. It contains dependencies
 * that are needed during the startup/shutdown. It is a modifiable context and will be updated
 * during startup or shutdown.
 */
public interface BrokerStartupContext {

  BrokerInfo getBrokerInfo();

  /**
   * Return the broker configuration shared across all physical tenants. This should be used only
   * for broker-wide configuration. The components that run per physical tenant must use the
   * configuration from #getPhysicalTenantContext(String physicalTenantId) instead.
   *
   * @return the broker-wide configuration shared across all physical tenants
   */
  BrokerCfg getBrokerConfiguration();

  SpringBrokerBridge getSpringBrokerBridge();

  ActorSchedulingService getActorSchedulingService();

  ConcurrencyControl getConcurrencyControl();

  BrokerHealthCheckService getHealthCheckService();

  SearchClientsProxy getSearchClientsProxy();

  @Nullable IntFunction<Long> getExportedPositionSupplier();

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

  JobStreamService getJobStreamService(String physicalTenantId);

  void addJobStreamService(String physicalTenantId, JobStreamService service);

  void removeJobStreamService(String physicalTenantId);

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

  ClusterConfigurationService getClusterConfigurationService();

  void setClusterConfigurationService(ClusterConfigurationService clusterConfigurationService);

  BrokerClient getBrokerClient();

  Duration getShutdownTimeout();

  SnowflakeIdGenerator getRequestIdGenerator();

  void setRequestIdGenerator(SnowflakeIdGenerator requestIdGenerator);

  MeterRegistry getMeterRegistry();

  /**
   * Returns the context for the given physical tenant.
   *
   * @throws IllegalArgumentException if the physical tenant id is unknown
   */
  PhysicalTenantContext getPhysicalTenantContext(String physicalTenantId);

  Function<String, UserServices> getUserServicesForTenant();

  PasswordEncoder getPasswordEncoder();

  Function<AuthenticationConfiguration, JwtDecoder> getJwtDecoderFactory();

  Function<AuthenticationConfiguration, OidcClaimsProvider> getOidcClaimsProviderFactory();

  SnapshotApiRequestHandler getSnapshotApiRequestHandler();

  void setSnapshotApiRequestHandler(SnapshotApiRequestHandler snapshotApiRequestHandler);

  CheckpointSchedulingService getCheckpointSchedulingService();

  void setCheckpointSchedulingService(CheckpointSchedulingService checkpointSchedulingService);

  NodeIdProvider getNodeIdProvider();

  /** Returns the physical tenant IDs this broker should run. */
  PhysicalTenantIds getPhysicalTenantIds();
}
