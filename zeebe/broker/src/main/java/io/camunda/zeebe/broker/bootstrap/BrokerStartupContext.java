/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.bootstrap;

import io.atomix.cluster.messaging.ManagedMessagingService;
import io.camunda.identity.sdk.IdentityConfiguration;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.service.UserServices;
import io.camunda.service.search.core.SearchQueryService;
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
import io.camunda.zeebe.broker.system.monitoring.BrokerHealthCheckService;
import io.camunda.zeebe.broker.system.monitoring.DiskSpaceUsageMonitor;
import io.camunda.zeebe.broker.transport.adminapi.AdminApiRequestHandler;
import io.camunda.zeebe.broker.transport.commandapi.CommandApiServiceImpl;
import io.camunda.zeebe.protocol.impl.encoding.BrokerInfo;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.transport.impl.AtomixServerTransport;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.agrona.concurrent.SnowflakeIdGenerator;
import org.springframework.security.crypto.password.PasswordEncoder;

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

  void addPartitionListener(PartitionListener partitionListener);

  void removePartitionListener(PartitionListener partitionListener);

  void addPartitionRaftListener(PartitionRaftListener partitionListener);

  void removePartitionRaftListener(PartitionRaftListener partitionListener);

  List<PartitionListener> getPartitionListeners();

  List<PartitionRaftListener> getPartitionRaftListeners();

  ClusterServicesImpl getClusterServices();

  CommandApiServiceImpl getCommandApiService();

  void setCommandApiService(CommandApiServiceImpl commandApiService);

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

  PartitionManagerImpl getPartitionManager();

  void setPartitionManager(PartitionManagerImpl partitionManager);

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

  SecurityConfiguration getSecurityConfiguration();

  UserServices getUserServices();

  PasswordEncoder getPasswordEncoder();

  Set<SearchQueryService> getSearchQueryServices();
}
