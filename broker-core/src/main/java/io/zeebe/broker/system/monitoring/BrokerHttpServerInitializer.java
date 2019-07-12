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

public class BrokerHttpServerInitializer extends ChannelInitializer<SocketChannel> {

  private final CollectorRegistry metricsRegistry;
  private BrokerHealthCheckService brokerHealthCheckService;

  public BrokerHttpServerInitializer(
      CollectorRegistry metricsRegistry, BrokerHealthCheckService brokerHealthCheckService) {
    this.metricsRegistry = metricsRegistry;
    this.brokerHealthCheckService = brokerHealthCheckService;
  }

  @Override
  protected void initChannel(SocketChannel ch) {
    ch.pipeline()
        .addLast("codec", new HttpServerCodec())
        .addLast("request", new BrokerHttpServerHandler(metricsRegistry, brokerHealthCheckService));
  }
}
