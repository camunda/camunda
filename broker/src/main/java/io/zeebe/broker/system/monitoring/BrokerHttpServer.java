/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.system.monitoring;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.prometheus.client.CollectorRegistry;

public class BrokerHttpServer implements AutoCloseable {

  private final NioEventLoopGroup bossGroup;
  private final NioEventLoopGroup workerGroup;
  private final Channel channel;

  public BrokerHttpServer(
      String host,
      int port,
      CollectorRegistry metricsRegistry,
      BrokerHealthCheckService brokerHealthCheckService) {
    bossGroup = new NioEventLoopGroup(1);
    workerGroup = new NioEventLoopGroup();

    channel =
        new ServerBootstrap()
            .group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel.class)
            .childHandler(
                new BrokerHttpServerInitializer(metricsRegistry, brokerHealthCheckService))
            .bind(host, port)
            .syncUninterruptibly()
            .channel();
  }

  @Override
  public void close() {
    workerGroup.shutdownGracefully();
    bossGroup.shutdownGracefully();
    channel.close().syncUninterruptibly();
  }
}
