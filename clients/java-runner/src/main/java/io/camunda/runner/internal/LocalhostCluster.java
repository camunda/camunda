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
package io.camunda.runner.internal;

import io.camunda.client.CamundaClient;
import io.camunda.runner.Cluster;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Connects to a Camunda gateway on {@code localhost:port}, plaintext gRPC + REST 8080. */
public final class LocalhostCluster implements Cluster {

  private static final Logger LOG = LoggerFactory.getLogger(LocalhostCluster.class);

  private final int grpcPort;
  private final URI restAddress = URI.create("http://localhost:8080");
  private volatile CamundaClient client;

  public LocalhostCluster(final int grpcPort) {
    this.grpcPort = grpcPort;
  }

  @Override
  public synchronized CamundaClient client() {
    if (client == null) {
      LOG.info("connecting to localhost cluster on grpc :{} (REST :8080)", grpcPort);
      client =
          CamundaClient.newClientBuilder()
              .grpcAddress(URI.create("http://localhost:" + grpcPort))
              .restAddress(restAddress)
              .build();
    }
    return client;
  }

  @Override
  public boolean ownsClient() {
    return true;
  }

  @Override
  public URI restAddress() {
    return restAddress;
  }

  @Override
  public synchronized void close() {
    if (client != null) {
      client.close();
      client = null;
    }
  }
}
