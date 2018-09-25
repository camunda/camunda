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

import io.grpc.Server;
import io.grpc.netty.NettyServerBuilder;
import io.zeebe.gateway.impl.ZeebeClientBuilderImpl;
import io.zeebe.gateway.impl.broker.BrokerClient;
import java.io.IOException;
import java.net.InetSocketAddress;
import org.slf4j.Logger;

public class Gateway {

  private static final Logger LOG = Loggers.GATEWAY_LOGGER;

  private static final int GATEWAY_DEFAULT_PORT = 26500;
  private static final String GATEWAY_DEFAULT_HOST = "0.0.0.0";

  private final String host;
  private final int port;

  private Server server;
  private String brokerContactPoint = "0.0.0.0:26501";
  private BrokerClient brokerClient;

  public Gateway() {
    this(GATEWAY_DEFAULT_PORT);
  }

  public Gateway(final int port) {
    this(GATEWAY_DEFAULT_HOST, port);
  }

  public Gateway(final String host, final int port) {
    this.host = host;
    this.port = port;
  }

  public static void main(final String[] args) {
    int port = GATEWAY_DEFAULT_PORT;
    final Gateway gateway;

    if (args.length >= 1) {
      try {
        port = Integer.valueOf(args[0]);
      } catch (final NumberFormatException exp) {
        LOG.warn("Failed to parse specified port {} - using default port {}", args[0], port);
      }
    }

    gateway = new Gateway(port);

    try {
      gateway.listenAndServe();
    } catch (final Exception e) {
      LOG.error("Gateway failed ", e);
    } finally {
      gateway.stop();
    }
  }

  public void setBrokerContactPoint(final String brokerContactPoint) {
    this.brokerContactPoint = brokerContactPoint;
  }

  public void start() throws IOException {
    final ZeebeClientBuilderImpl brokerClientBuilder = new ZeebeClientBuilderImpl();

    brokerClientBuilder.brokerContactPoint(brokerContactPoint);

    brokerClient = brokerClientBuilder.buildBrokerClient();

    server =
        NettyServerBuilder.forAddress(new InetSocketAddress(host, port))
            .addService(new EndpointManager(brokerClient))
            .build();

    server.start();
    LOG.info("Gateway started at port: {}", port);
  }

  public void listenAndServe() throws InterruptedException, IOException {
    start();
    server.awaitTermination();
  }

  public void stop() {
    if (brokerClient != null) {
      brokerClient.close();
      brokerClient = null;
    }

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
  }
}
