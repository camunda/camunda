/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.bootstrap;

import static java.util.Collections.unmodifiableList;
import static java.util.Objects.requireNonNull;

import io.atomix.cluster.messaging.ManagedMessagingService;
import io.camunda.configuration.api.physicaltenants.PhysicalTenantIds;
import io.camunda.identity.sdk.IdentityConfiguration;
import io.camunda.search.clients.SearchClientsProxy;
import io.camunda.security.api.context.OidcClaimsProvider;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.agrona.concurrent.SnowflakeIdGenerator;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;

public final class BrokerStartupContextImpl implements BrokerStartupContext {

  private final BrokerInfo brokerInfo;
  private final BrokerCfg configuration;
  private final IdentityConfiguration identityConfiguration;
  private final SpringBrokerBridge springBrokerBridge;
  private final ActorSchedulingService actorScheduler;
  private final BrokerHealthCheckService healthCheckService;
  private final ExporterRepository exporterRepository;
  private final ClusterServicesImpl clusterServices;
  private final BrokerClient brokerClient;
  private final List<PartitionListener> partitionListeners = new ArrayList<>();
  private final List<PartitionRaftListener> partitionRaftListeners = new ArrayList<>();
  private final Duration shutdownTimeout;
  private final MeterRegistry meterRegistry;
  private final EngineSecurityConfig securityConfiguration;
  private final UserServices userServices;
  private final PasswordEncoder passwordEncoder;
  private final JwtDecoder jwtDecoder;
  private final OidcClaimsProvider oidcClaimsProvider;
  private final SearchClientsProxy searchClientsProxy;
  private final NodeIdProvider nodeIdProvider;
  private final PhysicalTenantIds physicalTenantIds;
  private final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter;

  private ConcurrencyControl concurrencyControl;
  private DiskSpaceUsageMonitor diskSpaceUsageMonitor;
  private AtomixServerTransport gatewayBrokerTransport;
  private SnowflakeIdGenerator requestIdGenerator;
  private ManagedMessagingService commandApiMessagingService;
  private AdminApiRequestHandler adminApiService;
  private EmbeddedGatewayService embeddedGatewayService;
  private final Map<String, PartitionManager> partitionManagers = new LinkedHashMap<>();
  private RocksDbResources sharedRocksDbResources;
  private BrokerAdminServiceImpl brokerAdminService;
  private JobStreamService jobStreamService;
  private ClusterConfigurationService clusterConfigurationService;
  private SnapshotApiRequestHandler snapshotApiRequestHandler;
  private CheckpointSchedulingService checkpointSchedulingService;

  public BrokerStartupContextImpl(
      final BrokerInfo brokerInfo,
      final BrokerCfg configuration,
      final IdentityConfiguration identityConfiguration,
      final SpringBrokerBridge springBrokerBridge,
      final ActorSchedulingService actorScheduler,
      final BrokerHealthCheckService healthCheckService,
      final ExporterRepository exporterRepository,
      final ClusterServicesImpl clusterServices,
      final BrokerClient brokerClient,
      final List<PartitionListener> additionalPartitionListeners,
      final Duration shutdownTimeout,
      final MeterRegistry meterRegistry,
      final EngineSecurityConfig securityConfiguration,
      final UserServices userServices,
      final PasswordEncoder passwordEncoder,
      final JwtDecoder jwtDecoder,
      final OidcClaimsProvider oidcClaimsProvider,
      final SearchClientsProxy searchClientsProxy,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter,
      final NodeIdProvider nodeIdProvider,
      final PhysicalTenantIds physicalTenantIds) {

    this.brokerInfo = requireNonNull(brokerInfo);
    this.configuration = requireNonNull(configuration);
    this.springBrokerBridge = requireNonNull(springBrokerBridge);
    this.actorScheduler = requireNonNull(actorScheduler);
    this.healthCheckService = requireNonNull(healthCheckService);
    this.exporterRepository = requireNonNull(exporterRepository);
    this.clusterServices = requireNonNull(clusterServices);
    this.identityConfiguration = identityConfiguration;
    this.brokerClient = brokerClient;
    this.shutdownTimeout = shutdownTimeout;
    this.meterRegistry = requireNonNull(meterRegistry);
    this.securityConfiguration = requireNonNull(securityConfiguration);
    this.userServices = userServices;
    this.passwordEncoder = passwordEncoder;
    this.jwtDecoder = jwtDecoder;
    this.oidcClaimsProvider = oidcClaimsProvider;
    this.searchClientsProxy = searchClientsProxy;
    this.nodeIdProvider = requireNonNull(nodeIdProvider);
    this.physicalTenantIds = requireNonNull(physicalTenantIds);
    partitionListeners.addAll(additionalPartitionListeners);
    this.brokerRequestAuthorizationConverter = brokerRequestAuthorizationConverter;
  }

  @Override
  public String toString() {
    return "BrokerStartupContextImpl{" + "broker=" + brokerInfo.brokerIdStr() + '}';
  }

  @Override
  public BrokerInfo getBrokerInfo() {
    return brokerInfo;
  }

  @Override
  public BrokerCfg getBrokerConfiguration() {
    return configuration;
  }

  @Override
  public IdentityConfiguration getIdentityConfiguration() {
    return identityConfiguration;
  }

  @Override
  public SpringBrokerBridge getSpringBrokerBridge() {
    return springBrokerBridge;
  }

  @Override
  public ActorSchedulingService getActorSchedulingService() {
    return actorScheduler;
  }

  @Override
  public ConcurrencyControl getConcurrencyControl() {
    return concurrencyControl;
  }

  public void setConcurrencyControl(final ConcurrencyControl concurrencyControl) {
    this.concurrencyControl = Objects.requireNonNull(concurrencyControl);
  }

  @Override
  public BrokerHealthCheckService getHealthCheckService() {
    return healthCheckService;
  }

  @Override
  public SearchClientsProxy getSearchClientsProxy() {
    return searchClientsProxy;
  }

  @Override
  public void addPartitionListener(final PartitionListener listener) {
    partitionListeners.add(requireNonNull(listener));
  }

  @Override
  public void removePartitionListener(final PartitionListener listener) {
    partitionListeners.remove(requireNonNull(listener));
  }

  @Override
  public void addPartitionRaftListener(final PartitionRaftListener listener) {
    partitionRaftListeners.add(requireNonNull(listener));
  }

  @Override
  public void removePartitionRaftListener(final PartitionRaftListener listener) {
    partitionRaftListeners.remove(requireNonNull(listener));
  }

  @Override
  public List<PartitionListener> getPartitionListeners() {
    return unmodifiableList(partitionListeners);
  }

  @Override
  public List<PartitionRaftListener> getPartitionRaftListeners() {
    return unmodifiableList(partitionRaftListeners);
  }

  @Override
  public ClusterServicesImpl getClusterServices() {
    return clusterServices;
  }

  @Override
  public AdminApiRequestHandler getAdminApiService() {
    return adminApiService;
  }

  @Override
  public void setAdminApiService(final AdminApiRequestHandler adminApiService) {
    this.adminApiService = adminApiService;
  }

  @Override
  public AtomixServerTransport getGatewayBrokerTransport() {
    return gatewayBrokerTransport;
  }

  @Override
  public void setGatewayBrokerTransport(final AtomixServerTransport gatewayBrokerTransport) {
    this.gatewayBrokerTransport = gatewayBrokerTransport;
  }

  @Override
  public ManagedMessagingService getApiMessagingService() {
    return commandApiMessagingService;
  }

  @Override
  public void setApiMessagingService(final ManagedMessagingService commandApiMessagingService) {
    this.commandApiMessagingService = commandApiMessagingService;
  }

  @Override
  public EmbeddedGatewayService getEmbeddedGatewayService() {
    return embeddedGatewayService;
  }

  @Override
  public void setEmbeddedGatewayService(final EmbeddedGatewayService embeddedGatewayService) {
    this.embeddedGatewayService = embeddedGatewayService;
  }

  @Override
  public DiskSpaceUsageMonitor getDiskSpaceUsageMonitor() {
    return diskSpaceUsageMonitor;
  }

  @Override
  public void setDiskSpaceUsageMonitor(final DiskSpaceUsageMonitor diskSpaceUsageMonitor) {
    this.diskSpaceUsageMonitor = diskSpaceUsageMonitor;
  }

  @Override
  public ExporterRepository getExporterRepository() {
    return exporterRepository;
  }

  @Override
  public Map<String, PartitionManager> getPartitionManagers() {
    return Collections.unmodifiableMap(partitionManagers);
  }

  @Override
  public void addPartitionManager(
      final String physicalTenantId, final PartitionManager partitionManager) {
    partitionManagers.put(physicalTenantId, partitionManager);
  }

  @Override
  public void removePartitionManager(final String physicalTenantId) {
    partitionManagers.remove(physicalTenantId);
  }

  @Override
  public RocksDbResources getRocksDbResources() {
    return sharedRocksDbResources;
  }

  @Override
  public void setRocksDbResources(final RocksDbResources sharedRocksDbResources) {
    this.sharedRocksDbResources = sharedRocksDbResources;
  }

  @Override
  public BrokerAdminServiceImpl getBrokerAdminService() {
    return brokerAdminService;
  }

  @Override
  public void setBrokerAdminService(final BrokerAdminServiceImpl brokerAdminService) {
    this.brokerAdminService = brokerAdminService;
  }

  @Override
  public JobStreamService getJobStreamService() {
    return jobStreamService;
  }

  @Override
  public void setJobStreamService(final JobStreamService jobStreamService) {
    this.jobStreamService = jobStreamService;
  }

  @Override
  public ClusterConfigurationService getClusterConfigurationService() {
    return clusterConfigurationService;
  }

  @Override
  public void setClusterConfigurationService(
      final ClusterConfigurationService clusterConfigurationService) {
    this.clusterConfigurationService = clusterConfigurationService;
  }

  @Override
  public BrokerClient getBrokerClient() {
    return brokerClient;
  }

  @Override
  public Duration getShutdownTimeout() {
    return shutdownTimeout;
  }

  @Override
  public SnowflakeIdGenerator getRequestIdGenerator() {
    return requestIdGenerator;
  }

  @Override
  public void setRequestIdGenerator(final SnowflakeIdGenerator requestIdGenerator) {
    this.requestIdGenerator = requestIdGenerator;
  }

  @Override
  public MeterRegistry getMeterRegistry() {
    return meterRegistry;
  }

  @Override
  public EngineSecurityConfig getSecurityConfiguration() {
    return securityConfiguration;
  }

  @Override
  public UserServices getUserServices() {
    return userServices;
  }

  @Override
  public PasswordEncoder getPasswordEncoder() {
    return passwordEncoder;
  }

  @Override
  public JwtDecoder getJwtDecoder() {
    return jwtDecoder;
  }

  @Override
  public OidcClaimsProvider getOidcClaimsProvider() {
    return oidcClaimsProvider;
  }

  @Override
  public SnapshotApiRequestHandler getSnapshotApiRequestHandler() {
    return snapshotApiRequestHandler;
  }

  @Override
  public void setSnapshotApiRequestHandler(
      final SnapshotApiRequestHandler snapshotApiRequestHandler) {
    this.snapshotApiRequestHandler = snapshotApiRequestHandler;
  }

  @Override
  public BrokerRequestAuthorizationConverter getBrokerRequestAuthorizationConverter() {
    return brokerRequestAuthorizationConverter;
  }

  @Override
  public CheckpointSchedulingService getCheckpointSchedulingService() {
    return checkpointSchedulingService;
  }

  @Override
  public void setCheckpointSchedulingService(
      final CheckpointSchedulingService checkpointSchedulingService) {
    this.checkpointSchedulingService = checkpointSchedulingService;
  }

  @Override
  public NodeIdProvider getNodeIdProvider() {
    return nodeIdProvider;
  }

  @Override
  public PhysicalTenantIds getPhysicalTenantIds() {
    return physicalTenantIds;
  }
}
