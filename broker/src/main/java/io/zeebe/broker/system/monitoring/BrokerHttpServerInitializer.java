/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.system.monitoring;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpServerCodec;
import io.prometheus.client.CollectorRegistry;
import io.zeebe.broker.system.management.BrokerAdminService;

public final class BrokerHttpServerInitializer extends ChannelInitializer<SocketChannel> {

  private final CollectorRegistry metricsRegistry;
  private final BrokerHealthCheckService brokerHealthCheckService;
  private final BrokerAdminService brokerAdminService;

  public BrokerHttpServerInitializer(
      final CollectorRegistry metricsRegistry,
      final BrokerHealthCheckService brokerHealthCheckService,
      final BrokerAdminService brokerAdminService) {
    this.metricsRegistry = metricsRegistry;
    this.brokerHealthCheckService = brokerHealthCheckService;
    this.brokerAdminService = brokerAdminService;
  }

  @Override
  protected void initChannel(final SocketChannel ch) {
    ch.pipeline()
        .addLast("codec", new HttpServerCodec())
        .addLast(
            "request",
            new BrokerHttpServerHandler(
                metricsRegistry, brokerHealthCheckService, brokerAdminService));
  }
}
