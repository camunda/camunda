/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.bootstrap;

import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;

import io.atomix.cluster.messaging.ManagedMessagingService;
import io.camunda.identity.sdk.IdentityConfiguration;
import io.camunda.search.clients.SearchClientsProxy;
import io.camunda.security.auth.BrokerRequestAuthorizationConverter;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.service.UserServices;
import io.camunda.zeebe.broker.PartitionListener;
import io.camunda.zeebe.broker.PartitionRaftListener;
import io.camunda.zeebe.broker.SpringBrokerBridge;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.clustering.ClusterServicesImpl;
import io.camunda.zeebe.broker.exporter.repo.ExporterRepository;
import io.camunda.zeebe.broker.jobstream.JobStreamService;
import io.camunda.zeebe.broker.partitioning.PartitionManagerImpl;
import io.camunda.zeebe.broker.partitioning.topology.ClusterConfigurationService;
import io.camunda.zeebe.broker.system.EmbeddedGatewayService;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.management.BrokerAdminServiceImpl;
import io.camunda.zeebe.broker.system.management.CheckpointSchedulingService;
import io.camunda.zeebe.broker.system.monitoring.BrokerHealthCheckService;
import io.camunda.zeebe.broker.system.monitoring.DiskSpaceUsageMonitor;
import io.camunda.zeebe.broker.transport.adminapi.AdminApiRequestHandler;
import io.camunda.zeebe.broker.transport.commandapi.CommandApiServiceImpl;
import io.camunda.zeebe.broker.transport.snapshotapi.SnapshotApiRequestHandler;
import io.camunda.zeebe.dynamic.nodeid.NodeIdProvider;
import io.camunda.zeebe.protocol.impl.encoding.BrokerInfo;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.transport.impl.AtomixServerTransport;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import org.agrona.concurrent.SnowflakeIdGenerator;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;

public class MockBrokerStartupContext implements BrokerStartupContext {

  private BrokerInfo brokerInfo = mock(BrokerInfo.class);
  private BrokerCfg brokerConfiguration = mock(BrokerCfg.class);
  private IdentityConfiguration identityConfiguration = mock(IdentityConfiguration.class);
  private SpringBrokerBridge springBrokerBridge = mock(SpringBrokerBridge.class);
  private ActorSchedulingService actorSchedulingService = mock(ActorSchedulingService.class);
  private ConcurrencyControl concurrencyControl = mock(ConcurrencyControl.class);
  private BrokerHealthCheckService healthCheckService = mock(BrokerHealthCheckService.class);
  private SearchClientsProxy searchClientsProxy = mock(SearchClientsProxy.class);
  private List<PartitionListener> partitionListeners = List.of();
  private List<PartitionRaftListener> partitionRaftListeners = List.of();
  private ClusterServicesImpl clusterServices = mock(ClusterServicesImpl.class, RETURNS_DEEP_STUBS);
  private CommandApiServiceImpl commandApiService = mock(CommandApiServiceImpl.class);
  private AdminApiRequestHandler adminApiService = mock(AdminApiRequestHandler.class);
  private AtomixServerTransport gatewayBrokerTransport = mock(AtomixServerTransport.class);
  private ManagedMessagingService apiMessagingService = mock(ManagedMessagingService.class);
  private EmbeddedGatewayService embeddedGatewayService;
  private DiskSpaceUsageMonitor diskSpaceUsageMonitor = mock(DiskSpaceUsageMonitor.class);
  private ExporterRepository exporterRepository = mock(ExporterRepository.class);
  private PartitionManagerImpl partitionManager;
  private BrokerAdminServiceImpl brokerAdminService = mock(BrokerAdminServiceImpl.class);
  private JobStreamService jobStreamService = mock(JobStreamService.class);
  private ClusterConfigurationService clusterConfigurationService =
      mock(ClusterConfigurationService.class);
  private BrokerClient brokerClient = mock(BrokerClient.class);
  private Duration shutdownTimeout = Duration.ofSeconds(30);
  private SnowflakeIdGenerator requestIdGenerator = mock(SnowflakeIdGenerator.class);
  private MeterRegistry meterRegistry = new SimpleMeterRegistry();
  private SecurityConfiguration securityConfiguration = new SecurityConfiguration();
  private UserServices userServices = mock(UserServices.class);
  private PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
  private JwtDecoder jwtDecoder = mock(JwtDecoder.class);
  private SnapshotApiRequestHandler snapshotApiRequestHandler =
      mock(SnapshotApiRequestHandler.class);
  private BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter =
      mock(BrokerRequestAuthorizationConverter.class);
  private CheckpointSchedulingService checkpointSchedulingService =
      mock(CheckpointSchedulingService.class);
  private NodeIdProvider nodeIdProvider = mock(NodeIdProvider.class);

  @Override
  public BrokerInfo getBrokerInfo() {
    return brokerInfo;
  }

  public void setBrokerInfo(final BrokerInfo brokerInfo) {
    this.brokerInfo = brokerInfo;
  }

  @Override
  public BrokerCfg getBrokerConfiguration() {
    return brokerConfiguration;
  }

  public void setBrokerConfiguration(final BrokerCfg brokerConfiguration) {
    this.brokerConfiguration = brokerConfiguration;
  }

  @Override
  public IdentityConfiguration getIdentityConfiguration() {
    return identityConfiguration;
  }

  public void setIdentityConfiguration(final IdentityConfiguration identityConfiguration) {
    this.identityConfiguration = identityConfiguration;
  }

  @Override
  public SpringBrokerBridge getSpringBrokerBridge() {
    return springBrokerBridge;
  }

  public void setSpringBrokerBridge(final SpringBrokerBridge springBrokerBridge) {
    this.springBrokerBridge = springBrokerBridge;
  }

  @Override
  public ActorSchedulingService getActorSchedulingService() {
    return actorSchedulingService;
  }

  public void setActorSchedulingService(final ActorSchedulingService actorSchedulingService) {
    this.actorSchedulingService = actorSchedulingService;
  }

  @Override
  public ConcurrencyControl getConcurrencyControl() {
    return concurrencyControl;
  }

  public void setConcurrencyControl(final ConcurrencyControl concurrencyControl) {
    this.concurrencyControl = concurrencyControl;
  }

  @Override
  public BrokerHealthCheckService getHealthCheckService() {
    return healthCheckService;
  }

  public void setHealthCheckService(final BrokerHealthCheckService healthCheckService) {
    this.healthCheckService = healthCheckService;
  }

  @Override
  public SearchClientsProxy getSearchClientsProxy() {
    return searchClientsProxy;
  }

  public void setSearchClientsProxy(final SearchClientsProxy searchClientsProxy) {
    this.searchClientsProxy = searchClientsProxy;
  }

  @Override
  public void addPartitionListener(final PartitionListener partitionListener) {}

  @Override
  public void removePartitionListener(final PartitionListener partitionListener) {}

  @Override
  public void addPartitionRaftListener(final PartitionRaftListener partitionListener) {}

  @Override
  public void removePartitionRaftListener(final PartitionRaftListener partitionListener) {}

  @Override
  public List<PartitionListener> getPartitionListeners() {
    return partitionListeners;
  }

  public void setPartitionListeners(final List<PartitionListener> partitionListeners) {
    this.partitionListeners = partitionListeners;
  }

  @Override
  public List<PartitionRaftListener> getPartitionRaftListeners() {
    return partitionRaftListeners;
  }

  public void setPartitionRaftListeners(final List<PartitionRaftListener> partitionRaftListeners) {
    this.partitionRaftListeners = partitionRaftListeners;
  }

  @Override
  public ClusterServicesImpl getClusterServices() {
    return clusterServices;
  }

  public void setClusterServices(final ClusterServicesImpl clusterServices) {
    this.clusterServices = clusterServices;
  }

  @Override
  public CommandApiServiceImpl getCommandApiService() {
    return commandApiService;
  }

  @Override
  public void setCommandApiService(final CommandApiServiceImpl commandApiService) {
    this.commandApiService = commandApiService;
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
    return apiMessagingService;
  }

  @Override
  public void setApiMessagingService(final ManagedMessagingService apiMessagingService) {
    this.apiMessagingService = apiMessagingService;
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

  public void setExporterRepository(final ExporterRepository exporterRepository) {
    this.exporterRepository = exporterRepository;
  }

  @Override
  public PartitionManagerImpl getPartitionManager() {
    return partitionManager;
  }

  @Override
  public void setPartitionManager(final PartitionManagerImpl partitionManager) {
    this.partitionManager = partitionManager;
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

  public void setBrokerClient(final BrokerClient brokerClient) {
    this.brokerClient = brokerClient;
  }

  @Override
  public Duration getShutdownTimeout() {
    return shutdownTimeout;
  }

  public void setShutdownTimeout(final Duration shutdownTimeout) {
    this.shutdownTimeout = shutdownTimeout;
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

  public void setMeterRegistry(final MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  @Override
  public SecurityConfiguration getSecurityConfiguration() {
    return securityConfiguration;
  }

  public void setSecurityConfiguration(final SecurityConfiguration securityConfiguration) {
    this.securityConfiguration = securityConfiguration;
  }

  @Override
  public UserServices getUserServices() {
    return userServices;
  }

  public void setUserServices(final UserServices userServices) {
    this.userServices = userServices;
  }

  @Override
  public PasswordEncoder getPasswordEncoder() {
    return passwordEncoder;
  }

  public void setPasswordEncoder(final PasswordEncoder passwordEncoder) {
    this.passwordEncoder = passwordEncoder;
  }

  @Override
  public JwtDecoder getJwtDecoder() {
    return jwtDecoder;
  }

  public void setJwtDecoder(final JwtDecoder jwtDecoder) {
    this.jwtDecoder = jwtDecoder;
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

  public void setBrokerRequestAuthorizationConverter(
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter) {
    this.brokerRequestAuthorizationConverter = brokerRequestAuthorizationConverter;
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

  public void setNodeIdProvider(final NodeIdProvider nodeIdProvider) {
    this.nodeIdProvider = nodeIdProvider;
  }
}
