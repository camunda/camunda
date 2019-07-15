/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.system.monitoring;

import io.prometheus.client.CollectorRegistry;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;

public class BrokerHttpServerService implements Service<BrokerHttpServer> {

  private final String host;
  private final int port;
  private final CollectorRegistry metricsRegistry;
  private BrokerHealthCheckService brokerHealthCheckService;

  private BrokerHttpServer brokerHttpServer;

  public BrokerHttpServerService(
      String host,
      int port,
      CollectorRegistry metricsRegistry,
      BrokerHealthCheckService brokerHealthCheckService) {
    this.host = host;
    this.port = port;
    this.metricsRegistry = metricsRegistry;
    this.brokerHealthCheckService = brokerHealthCheckService;
  }

  @Override
  public void start(ServiceStartContext startContext) {
    startContext.run(
        () ->
            brokerHttpServer =
                new BrokerHttpServer(host, port, metricsRegistry, brokerHealthCheckService));
  }

  @Override
  public void stop(ServiceStopContext stopContext) {
    stopContext.run(brokerHttpServer::close);
  }

  @Override
  public BrokerHttpServer get() {
    return brokerHttpServer;
  }
}
