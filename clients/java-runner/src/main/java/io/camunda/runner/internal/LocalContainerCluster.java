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
import org.testcontainers.containers.output.Slf4jLogConsumer;
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
  private volatile URI restAddress;

  /**
   * Default image tag — a known-stable published tag. We avoid {@code latest} because its boot
   * profile may diverge unexpectedly. Override via system property {@code
   * io.camunda.process.test.camundaDockerImageVersion} or the constructor when a specific version
   * is wanted.
   */
  private static final String DEFAULT_IMAGE_VERSION = "8.9.4";

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
      // In-memory H2 secondary storage so the container is self-contained — no Elasticsearch
      // dependency. (Default secondary storage is Elasticsearch, which would hang waiting for an
      // ES instance that isn't there.)
      final String dbUrl =
          "jdbc:h2:mem:livebpmn-"
              + java.util.UUID.randomUUID()
              + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL";
      container =
          new GenericContainer<>(DockerImageName.parse(imageName + ":" + imageVersion))
              .withExposedPorts(GRPC_PORT, REST_PORT)
              .withEnv("SPRING_PROFILES_ACTIVE", "broker,consolidated-auth,security")
              .withEnv("CAMUNDA_SECURITY_AUTHENTICATION_UNPROTECTED_API", "true")
              .withEnv("CAMUNDA_SECURITY_AUTHORIZATIONS_ENABLED", "false")
              // RDBMS / H2 secondary storage
              .withEnv("CAMUNDA_DATABASE_TYPE", "rdbms")
              .withEnv("CAMUNDA_DATA_SECONDARYSTORAGE_TYPE", "rdbms")
              .withEnv("CAMUNDA_DATABASE_URL", dbUrl)
              .withEnv("CAMUNDA_DATABASE_USERNAME", "sa")
              .withEnv("CAMUNDA_DATABASE_PASSWORD", "")
              .withEnv(
                  "ZEEBE_BROKER_EXPORTERS_RDBMS_CLASSNAME",
                  "io.camunda.exporter.rdbms.RdbmsExporter")
              .withEnv("ZEEBE_BROKER_EXPORTERS_RDBMS_ARGS_FLUSH_INTERVAL", "PT0S")
              .withEnv("ZEEBE_BROKER_EXPORTERS_RDBMS_ARGS_DEFAULT_HISTORY_TTL", "PT2S")
              .withEnv("ZEEBE_BROKER_EXPORTERS_RDBMS_ARGS_MIN_HISTORY_CLEANUP_INTERVAL", "PT2S")
              .withEnv("ZEEBE_BROKER_EXPORTERS_RDBMS_ARGS_MAX_HISTORY_CLEANUP_INTERVAL", "PT5S")
              // Stream Camunda's stdout/stderr into our SLF4J output so the user can see what's
              // happening inside the container (especially during slow startup).
              .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("camunda-container")))
              .waitingFor(
                  // /v2/topology is the SDK's broker-up probe. More reliable than actuator
                  // endpoints, which are mapped differently across 8.x point releases.
                  Wait.forHttp("/v2/topology")
                      .forPort(REST_PORT)
                      .forStatusCode(200)
                      .withStartupTimeout(Duration.ofMinutes(5)));
      container.start();
      LOG.info(
          "Testcontainer ready: grpc={} rest={}",
          container.getMappedPort(GRPC_PORT),
          container.getMappedPort(REST_PORT));
    }
    final String host = container.getHost();
    restAddress = URI.create("http://" + host + ":" + container.getMappedPort(REST_PORT));
    client =
        CamundaClient.newClientBuilder()
            .grpcAddress(URI.create("http://" + host + ":" + container.getMappedPort(GRPC_PORT)))
            .restAddress(restAddress)
            .build();
    return client;
  }

  @Override
  public boolean ownsClient() {
    return true;
  }

  @Override
  public URI restAddress() {
    if (restAddress == null) {
      throw new IllegalStateException("restAddress() called before client() materialised");
    }
    return restAddress;
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
