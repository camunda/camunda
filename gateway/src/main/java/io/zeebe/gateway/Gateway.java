/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.gateway;

import io.atomix.cluster.AtomixCluster;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.netty.NettyServerBuilder;
import io.zeebe.gateway.impl.broker.BrokerClient;
import io.zeebe.gateway.impl.broker.BrokerClientImpl;
import io.zeebe.gateway.impl.configuration.GatewayCfg;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.function.Function;
import org.slf4j.Logger;

public class Gateway {

  public static final String VERSION;
  private static final Logger LOG = Loggers.GATEWAY_LOGGER;
  private static final Function<GatewayCfg, ServerBuilder> DEFAULT_SERVER_BUILDER_FACTORY =
      cfg ->
          NettyServerBuilder.forAddress(
              new InetSocketAddress(cfg.getNetwork().getHost(), cfg.getNetwork().getPort()));

  static {
    final String version = Gateway.class.getPackage().getImplementationVersion();
    VERSION = version != null ? version : "development";
  }

  private final Function<GatewayCfg, ServerBuilder> serverBuilderFactory;
  private final Function<GatewayCfg, BrokerClient> brokerClientFactory;
  private final GatewayCfg gatewayCfg;

  private Server server;
  private BrokerClient brokerClient;
  private EndpointManager endpointManager;

  public Gateway(GatewayCfg gatewayCfg, AtomixCluster atomixCluster) {
    this(
        gatewayCfg,
        cfg -> new BrokerClientImpl(cfg, atomixCluster),
        DEFAULT_SERVER_BUILDER_FACTORY);
  }

  public Gateway(GatewayCfg gatewayCfg, Function<GatewayCfg, BrokerClient> brokerClientFactory) {
    this(gatewayCfg, brokerClientFactory, DEFAULT_SERVER_BUILDER_FACTORY);
  }

  public Gateway(
      GatewayCfg gatewayCfg,
      Function<GatewayCfg, BrokerClient> brokerClientFactory,
      Function<GatewayCfg, ServerBuilder> serverBuilderFactory) {
    this.gatewayCfg = gatewayCfg;
    this.brokerClientFactory = brokerClientFactory;
    this.serverBuilderFactory = serverBuilderFactory;
  }

  public GatewayCfg getGatewayCfg() {
    return gatewayCfg;
  }

  public BrokerClient getBrokerClient() {
    return brokerClient;
  }

  public void start() throws IOException {
    LOG.info("Version: {}", VERSION);
    LOG.info("Starting gateway with configuration {}", gatewayCfg.toJson());

    brokerClient = buildBrokerClient();

    endpointManager = new EndpointManager(brokerClient);
    server = serverBuilderFactory.apply(gatewayCfg).addService(endpointManager).build();

    server.start();
  }

  protected BrokerClient buildBrokerClient() {
    return brokerClientFactory.apply(gatewayCfg);
  }

  public void listenAndServe() throws InterruptedException, IOException {
    start();
    server.awaitTermination();
  }

  public void stop() {
    if (server != null && !server.isShutdown()) {
      server.shutdown();
      try {
        server.awaitTermination();
      } catch (InterruptedException e) {
        LOG.error("Failed to await termination of gateway", e);
      } finally {
        server = null;
      }
    }

    if (brokerClient != null) {
      brokerClient.close();
      brokerClient = null;
    }
  }
}
