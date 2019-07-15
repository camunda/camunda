/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.system;

import static io.zeebe.broker.clustering.base.ClusterBaseLayerServiceNames.ATOMIX_JOIN_SERVICE;
import static io.zeebe.broker.clustering.base.ClusterBaseLayerServiceNames.ATOMIX_SERVICE;
import static io.zeebe.broker.clustering.base.ClusterBaseLayerServiceNames.FOLLOWER_PARTITION_GROUP_NAME;
import static io.zeebe.broker.clustering.base.ClusterBaseLayerServiceNames.LEADER_PARTITION_GROUP_NAME;
import static io.zeebe.broker.system.SystemServiceNames.BROKER_HEALTH_CHECK_SERVICE;
import static io.zeebe.broker.system.SystemServiceNames.BROKER_HTTP_SERVER;
import static io.zeebe.broker.system.SystemServiceNames.LEADER_MANAGEMENT_REQUEST_HANDLER;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.hotspot.DefaultExports;
import io.zeebe.broker.system.configuration.SocketBindingCfg;
import io.zeebe.broker.system.management.LeaderManagementRequestHandler;
import io.zeebe.broker.system.monitoring.BrokerHealthCheckService;
import io.zeebe.broker.system.monitoring.BrokerHttpServerService;
import io.zeebe.servicecontainer.ServiceContainer;

public class SystemComponent implements Component {

  private static final CollectorRegistry METRICS_REGISTRY = CollectorRegistry.defaultRegistry;

  static {
    // enable hotspot prometheus metric collection
    DefaultExports.initialize();
  }

  @Override
  public void init(final SystemContext context) {
    final ServiceContainer serviceContainer = context.getServiceContainer();

    final BrokerHealthCheckService healthCheckService = new BrokerHealthCheckService();
    serviceContainer
        .createService(BROKER_HEALTH_CHECK_SERVICE, healthCheckService)
        .dependency(ATOMIX_SERVICE, healthCheckService.getAtomixInjector())
        .dependency(ATOMIX_JOIN_SERVICE)
        .groupReference(LEADER_PARTITION_GROUP_NAME, healthCheckService.getLeaderInstallReference())
        .groupReference(
            FOLLOWER_PARTITION_GROUP_NAME, healthCheckService.getFollowerInstallReference())
        .install();

    final SocketBindingCfg monitoringApi =
        context.getBrokerConfiguration().getNetwork().getMonitoringApi();

    serviceContainer
        .createService(
            BROKER_HTTP_SERVER,
            new BrokerHttpServerService(
                monitoringApi.getHost(),
                monitoringApi.getPort(),
                METRICS_REGISTRY,
                healthCheckService))
        .install();

    final LeaderManagementRequestHandler requestHandlerService =
        new LeaderManagementRequestHandler();
    serviceContainer
        .createService(LEADER_MANAGEMENT_REQUEST_HANDLER, requestHandlerService)
        .dependency(ATOMIX_SERVICE, requestHandlerService.getAtomixInjector())
        .groupReference(
            LEADER_PARTITION_GROUP_NAME, requestHandlerService.getLeaderPartitionsGroupReference())
        .install();
  }
}
