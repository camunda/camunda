/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.clustering.base;

import io.atomix.cluster.AtomixCluster;
import io.zeebe.broker.system.configuration.BrokerCfg;
import io.zeebe.gateway.Gateway;
import io.zeebe.gateway.impl.broker.BrokerClient;
import io.zeebe.gateway.impl.broker.BrokerClientImpl;
import io.zeebe.gateway.impl.configuration.GatewayCfg;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import java.io.IOException;
import java.util.function.Function;

public class EmbeddedGatewayService implements Service<Gateway> {

  private final BrokerCfg configuration;
  private final Injector<AtomixCluster> atomixClusterInjector = new Injector<>();

  private Gateway gateway;

  public EmbeddedGatewayService(BrokerCfg configuration) {
    this.configuration = configuration;
  }

  @Override
  public void start(ServiceStartContext startContext) {
    final AtomixCluster atomix = atomixClusterInjector.getValue();
    final Function<GatewayCfg, BrokerClient> brokerClientFactory =
        cfg -> new BrokerClientImpl(cfg, atomix, startContext.getScheduler(), false);
    gateway = new Gateway(configuration.getGateway(), brokerClientFactory);
    startContext.run(this::startGateway);
  }

  @Override
  public void stop(ServiceStopContext stopContext) {
    if (gateway != null) {
      stopContext.run(gateway::stop);
    }
  }

  @Override
  public Gateway get() {
    return gateway;
  }

  private void startGateway() {
    try {
      gateway.start();
    } catch (final IOException e) {
      throw new RuntimeException("Gateway was not able to start", e);
    }
  }

  public Injector<AtomixCluster> getAtomixClusterInjector() {
    return atomixClusterInjector;
  }
}
