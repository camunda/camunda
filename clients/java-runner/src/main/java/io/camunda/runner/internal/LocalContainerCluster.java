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
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * A {@link Cluster} backed by a Camunda Testcontainer ({@code camunda/camunda}). The container is
 * started lazily on first {@link #client()} call. Image name and version are read from system
 * properties; sensible defaults are baked in for the hackday flow.
 */
public final class LocalContainerCluster implements Cluster {

  private static final Logger LOG = LoggerFactory.getLogger(LocalContainerCluster.class);

  private static final int GRPC_PORT = 26500;
  private static final int REST_PORT = 8080;

  private final String imageName;
  private final String imageVersion;
  private volatile GenericContainer<?> container;
  private volatile CamundaClient client;

  /**
   * Default image tag — a stable Docker Hub-published tag so first-time users don't need to build
   * Camunda locally. Override via {@code -Dio.camunda.process.test.camundaDockerImageVersion=...}
   * (e.g. {@code latest}, {@code 8.7.0}) or via the constructor.
   */
  private static final String DEFAULT_IMAGE_VERSION = "latest";

  public LocalContainerCluster() {
    this(
        System.getProperty("io.camunda.process.test.camundaDockerImageName", "camunda/camunda"),
        System.getProperty(
            "io.camunda.process.test.camundaDockerImageVersion", DEFAULT_IMAGE_VERSION));
  }

  public LocalContainerCluster(final String imageName, final String imageVersion) {
    this.imageName = imageName;
    this.imageVersion = imageVersion;
  }

  @Override
  public synchronized CamundaClient client() {
    if (client != null) {
      return client;
    }
    if (container == null) {
      LOG.info("starting Camunda Testcontainer image={}:{}", imageName, imageVersion);
      container =
          new GenericContainer<>(DockerImageName.parse(imageName + ":" + imageVersion))
              .withExposedPorts(GRPC_PORT, REST_PORT)
              .withEnv("SPRING_PROFILES_ACTIVE", "broker,consolidated-auth,security")
              .withEnv("ZEEBE_CLOCK_CONTROLLED", "true")
              .withEnv("CAMUNDA_SECURITY_AUTHENTICATION_UNPROTECTED_API", "true")
              .withEnv("CAMUNDA_SECURITY_AUTHORIZATIONS_ENABLED", "false")
              .waitingFor(
                  Wait.forHttp("/actuator/health/status")
                      .forPort(REST_PORT)
                      .forStatusCode(200)
                      .withStartupTimeout(Duration.ofMinutes(2)));
      container.start();
      LOG.info(
          "Testcontainer ready: grpc={} rest={}",
          container.getMappedPort(GRPC_PORT),
          container.getMappedPort(REST_PORT));
    }
    final String host = container.getHost();
    client =
        CamundaClient.newClientBuilder()
            .grpcAddress(URI.create("http://" + host + ":" + container.getMappedPort(GRPC_PORT)))
            .restAddress(URI.create("http://" + host + ":" + container.getMappedPort(REST_PORT)))
            .build();
    return client;
  }

  @Override
  public boolean ownsClient() {
    return true;
  }

  @Override
  public synchronized void close() {
    if (client != null) {
      try {
        client.close();
      } catch (final Exception e) {
        LOG.warn("error closing client", e);
      }
      client = null;
    }
    if (container != null) {
      try {
        container.stop();
      } catch (final Exception e) {
        LOG.warn("error stopping container", e);
      }
      container = null;
    }
  }
}
