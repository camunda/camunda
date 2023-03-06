/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.bootstrap;

import static java.util.Collections.unmodifiableList;
import static java.util.Objects.requireNonNull;

import io.atomix.cluster.messaging.ManagedMessagingService;
import io.camunda.zeebe.broker.PartitionListener;
import io.camunda.zeebe.broker.SpringBrokerBridge;
import io.camunda.zeebe.broker.clustering.ClusterServicesImpl;
import io.camunda.zeebe.broker.exporter.repo.ExporterRepository;
import io.camunda.zeebe.broker.jobstream.JobStreamService;
import io.camunda.zeebe.broker.partitioning.PartitionManagerImpl;
import io.camunda.zeebe.broker.system.EmbeddedGatewayService;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.management.BrokerAdminServiceImpl;
import io.camunda.zeebe.broker.system.monitoring.BrokerHealthCheckService;
import io.camunda.zeebe.broker.system.monitoring.DiskSpaceUsageMonitor;
import io.camunda.zeebe.broker.transport.adminapi.AdminApiRequestHandler;
import io.camunda.zeebe.broker.transport.commandapi.CommandApiServiceImpl;
import io.camunda.zeebe.protocol.impl.encoding.BrokerInfo;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.transport.impl.AtomixServerTransport;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class BrokerStartupContextImpl implements BrokerStartupContext {

  private final BrokerInfo brokerInfo;
  private final BrokerCfg configuration;
  private final SpringBrokerBridge springBrokerBridge;
  private final ActorSchedulingService actorScheduler;
  private final BrokerHealthCheckService healthCheckService;
  private final ExporterRepository exporterRepository;
  private final ClusterServicesImpl clusterServices;
  private final List<PartitionListener> partitionListeners = new ArrayList<>();

  private ConcurrencyControl concurrencyControl;
  private DiskSpaceUsageMonitor diskSpaceUsageMonitor;
  private AtomixServerTransport gatewayBrokerTransport;
  private ManagedMessagingService commandApiMessagingService;
  private CommandApiServiceImpl commandApiService;
  private AdminApiRequestHandler adminApiService;
  private EmbeddedGatewayService embeddedGatewayService;
  private PartitionManagerImpl partitionManager;
  private BrokerAdminServiceImpl brokerAdminService;
  private JobStreamService jobStreamService;

  public BrokerStartupContextImpl(
      final BrokerInfo brokerInfo,
      final BrokerCfg configuration,
      final SpringBrokerBridge springBrokerBridge,
      final ActorSchedulingService actorScheduler,
      final BrokerHealthCheckService healthCheckService,
      final ExporterRepository exporterRepository,
      final ClusterServicesImpl clusterServices,
      final List<PartitionListener> additionalPartitionListeners) {

    this.brokerInfo = requireNonNull(brokerInfo);
    this.configuration = requireNonNull(configuration);
    this.springBrokerBridge = requireNonNull(springBrokerBridge);
    this.actorScheduler = requireNonNull(actorScheduler);
    this.healthCheckService = requireNonNull(healthCheckService);
    this.exporterRepository = requireNonNull(exporterRepository);
    this.clusterServices = requireNonNull(clusterServices);
    partitionListeners.addAll(additionalPartitionListeners);
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
  public void addPartitionListener(final PartitionListener listener) {
    partitionListeners.add(requireNonNull(listener));
  }

  @Override
  public void removePartitionListener(final PartitionListener listener) {
    partitionListeners.remove(requireNonNull(listener));
  }

  @Override
  public List<PartitionListener> getPartitionListeners() {
    return unmodifiableList(partitionListeners);
  }

  @Override
  public ClusterServicesImpl getClusterServices() {
    return clusterServices;
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
}
