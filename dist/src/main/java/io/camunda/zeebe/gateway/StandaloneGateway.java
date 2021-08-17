/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway;

import io.atomix.cluster.AtomixCluster;
import io.atomix.cluster.discovery.BootstrapDiscoveryProvider;
import io.atomix.cluster.protocol.GroupMembershipProtocol;
import io.atomix.cluster.protocol.SwimMembershipProtocol;
import io.atomix.utils.net.Address;
import io.camunda.zeebe.gateway.impl.SpringGatewayBridge;
import io.camunda.zeebe.gateway.impl.broker.BrokerClient;
import io.camunda.zeebe.gateway.impl.broker.BrokerClientImpl;
import io.camunda.zeebe.gateway.impl.broker.cluster.BrokerTopologyManager;
import io.camunda.zeebe.gateway.impl.configuration.ClusterCfg;
import io.camunda.zeebe.gateway.impl.configuration.GatewayCfg;
import io.camunda.zeebe.gateway.impl.configuration.MembershipCfg;
import io.camunda.zeebe.util.VersionUtil;
import io.camunda.zeebe.util.sched.ActorScheduler;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchRestClientAutoConfiguration;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.event.ContextClosedEvent;

@SpringBootApplication(exclude = {ElasticsearchRestClientAutoConfiguration.class})
@ComponentScan({"io.camunda.zeebe.gateway", "io.camunda.zeebe.shared", "io.camunda.zeebe.util"})
public class StandaloneGateway
    implements CommandLineRunner, ApplicationListener<ContextClosedEvent> {
  private static final Logger LOG = Loggers.GATEWAY_LOGGER;

  @Autowired private GatewayCfg configuration;
  @Autowired private SpringGatewayBridge springGatewayBridge;

  private AtomixCluster atomixCluster;
  private Gateway gateway;
  private ActorScheduler actorScheduler;

  public static void main(final String[] args) throws Exception {
    System.setProperty("spring.banner.location", "classpath:/assets/zeebe_gateway_banner.txt");
    SpringApplication.run(StandaloneGateway.class, args);
  }

  @Override
  public void run(final String... args) throws Exception {
    configuration.init();

    if (LOG.isInfoEnabled()) {
      LOG.info("Version: {}", VersionUtil.getVersion());
      LOG.info("Starting standalone gateway with configuration {}", configuration.toJson());
    }

    atomixCluster = createAtomixCluster(configuration.getCluster());
    actorScheduler = createActorScheduler(configuration);
    final Function<GatewayCfg, BrokerClient> brokerClientFactory =
        cfg ->
            new BrokerClientImpl(
                cfg,
                atomixCluster.getMessagingService(),
                atomixCluster.getMembershipService(),
                atomixCluster.getEventService(),
                actorScheduler,
                false);
    gateway = new Gateway(configuration, brokerClientFactory, actorScheduler);
    springGatewayBridge.registerGatewayStatusSupplier(gateway::getStatus);
    springGatewayBridge.registerClusterStateSupplier(
        () ->
            Optional.ofNullable(gateway.getBrokerClient())
                .map(BrokerClient::getTopologyManager)
                .map(BrokerTopologyManager::getTopology));

    actorScheduler.start();
    atomixCluster.start();
    gateway.start();
  }

  @Override
  public void onApplicationEvent(final ContextClosedEvent event) {
    try {
      gateway.stop();
    } catch (final Exception e) {
      LOG.warn("Failed to gracefully shutdown gRPC gateway", e);
    }

    try {
      atomixCluster.stop().orTimeout(10, TimeUnit.SECONDS).join();
    } catch (final Exception e) {
      LOG.warn("Failed to gracefully shutdown cluster services", e);
    }

    try {
      actorScheduler.close();
    } catch (final Exception e) {
      LOG.warn("Failed to gracefully shutdown actor scheduler", e);
    }

    LogManager.shutdown();
  }

  private AtomixCluster createAtomixCluster(final ClusterCfg clusterCfg) {
    final MembershipCfg membershipCfg = clusterCfg.getMembership();
    final GroupMembershipProtocol membershipProtocol =
        SwimMembershipProtocol.builder()
            .withFailureTimeout(membershipCfg.getFailureTimeout())
            .withGossipInterval(membershipCfg.getGossipInterval())
            .withProbeInterval(membershipCfg.getProbeInterval())
            .withProbeTimeout(membershipCfg.getProbeTimeout())
            .withBroadcastDisputes(membershipCfg.isBroadcastDisputes())
            .withBroadcastUpdates(membershipCfg.isBroadcastUpdates())
            .withGossipFanout(membershipCfg.getGossipFanout())
            .withNotifySuspect(membershipCfg.isNotifySuspect())
            .withSuspectProbes(membershipCfg.getSuspectProbes())
            .withSyncInterval(membershipCfg.getSyncInterval())
            .build();

    return AtomixCluster.builder()
        .withMemberId(clusterCfg.getMemberId())
        .withAddress(Address.from(clusterCfg.getHost(), clusterCfg.getPort()))
        .withClusterId(clusterCfg.getClusterName())
        .withMembershipProvider(
            BootstrapDiscoveryProvider.builder()
                .withNodes(Address.from(clusterCfg.getContactPoint()))
                .build())
        .withMembershipProtocol(membershipProtocol)
        .build();
  }

  private ActorScheduler createActorScheduler(final GatewayCfg configuration) {
    return ActorScheduler.newActorScheduler()
        .setCpuBoundActorThreadCount(configuration.getThreads().getManagementThreads())
        .setIoBoundActorThreadCount(0)
        .setSchedulerName("gateway-scheduler")
        .build();
  }
}
