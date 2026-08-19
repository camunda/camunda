/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.schema;

import static io.camunda.zeebe.test.util.testcontainers.TestSearchContainers.createDefaultElasticsearchContainer;
import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.elasticsearch._types.mapping.DynamicMapping;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.webapps.schema.descriptors.index.MetadataIndex;
import io.camunda.webapps.schema.descriptors.index.RoleIndex;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.containers.wait.strategy.WaitAllStrategy;
import org.testcontainers.containers.wait.strategy.WaitAllStrategy.Mode;
import org.testcontainers.containers.wait.strategy.WaitStrategy;
import org.testcontainers.containers.wait.strategy.WaitStrategyTarget;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class SchemaManagerStartupIT {

  private static final Logger LOG = LoggerFactory.getLogger(SchemaManagerStartupIT.class);
  private static final String ELASTICSEARCH_URL = "http://test-elasticsearch:9200";

  /** No container answers to this alias, so every schema-initialization attempt fails. */
  private static final String UNREACHABLE_ELASTICSEARCH_URL = "http://absent-elasticsearch:9200";

  private static final String DB_TYPE_ELASTICSEARCH = "elasticsearch";

  private static final String CAMUNDA_TEST_IMAGE_NAME =
      Optional.ofNullable(System.getenv("CAMUNDA_TEST_DOCKER_IMAGE"))
          .orElse("camunda/camunda:SNAPSHOT");

  private static final int MONITORING_PORT = 9600;

  @AutoClose
  private final GenericContainer<?> camunda =
      new GenericContainer<>(CAMUNDA_TEST_IMAGE_NAME)
          .withLogConsumer(new Slf4jLogConsumer(LOG))
          // Unified Configuration: DB type
          .withEnv("CAMUNDA_DATA_SECONDARYSTORAGE_TYPE", DB_TYPE_ELASTICSEARCH)
          .withEnv("CAMUNDA_DATABASE_TYPE", DB_TYPE_ELASTICSEARCH)
          .withEnv("CAMUNDA_TASKLIST_DATABASE", DB_TYPE_ELASTICSEARCH)
          .withEnv("CAMUNDA_OPERATE_DATABASE", DB_TYPE_ELASTICSEARCH)
          // Unified Configuration: DB URL
          .withEnv("CAMUNDA_DATA_SECONDARYSTORAGE_ELASTICSEARCH_URL", ELASTICSEARCH_URL)
          .withEnv("CAMUNDA_DATABASE_URL", ELASTICSEARCH_URL)
          .withEnv("CAMUNDA_TASKLIST_ELASTICSEARCH_URL", ELASTICSEARCH_URL)
          .withEnv("CAMUNDA_OPERATE_ELASTICSEARCH_URL", ELASTICSEARCH_URL)
          // ---
          .withEnv("LOGGING_LEVEL_IO_CAMUNDA", "DEBUG")
          .withNetwork(Network.SHARED);

  @Container
  private final ElasticsearchContainer es =
      createDefaultElasticsearchContainer()
          .withNetwork(Network.SHARED)
          .withNetworkAliases("test-elasticsearch");

  @ParameterizedTest
  @CsvSource({"false, false", "false, true", "true, false", "true, true"})
  // waitForSchemaStartupBeforeShutdown:
  //   false is to test the case when the shutdown is before the schema startup is started
  //   true is to test the case when the shutdown is when the schema startup is retrying
  // gatewayEnabled:
  //   false = async schema startup, true = sync schema startup. Only a node with an HTTP gateway
  //   holds startup until a physical tenant is serviceable, so with the gateway enabled the
  //   shutdown signal arrives while the context refresh is still blocked at the gate.
  void shouldGracefullyShutdownWhenSchemaStartupStillRunning(
      final boolean waitForSchemaStartupBeforeShutdown, final boolean gatewayEnabled)
      throws InterruptedException, IOException {
    // given
    final var shutdownLatch = new CountDownLatch(1);

    // create the role index with incorrect mapping to force the schema manager to retry
    final ConnectConfiguration cfg = new ConnectConfiguration();
    cfg.setUrl(es.getHttpHostAddress());
    try (final var esClient = new ElasticsearchConnector(cfg).createClient()) {
      esClient
          .indices()
          .create(
              r ->
                  r.index(new RoleIndex("", true).getFullQualifiedName())
                      .mappings(
                          m ->
                              m.dynamic(DynamicMapping.Strict)
                                  .properties("roleId", p -> p.long_(l -> l))));
    }

    if (gatewayEnabled) {
      // enable embedded gateway and operate webapp
      camunda
          .withEnv("ZEEBE_BROKER_GATEWAY_ENABLE", "true")
          .withEnv("SPRING_PROFILES_ACTIVE", "broker,operate,dev")
          .withEnv("CAMUNDA_OPERATE_ELASTICSEARCH_HEALTHCHECKENABLED", "false");
    } else {
      camunda
          .withEnv("ZEEBE_BROKER_GATEWAY_ENABLE", "false")
          .withEnv("SPRING_PROFILES_ACTIVE", "broker,dev");
    }
    camunda.waitingFor(
        new WaitStrategy() {
          @Override
          public void waitUntilReady(final WaitStrategyTarget waitStrategyTarget) {
            // Wait until the application is shutdown
            try {
              shutdownLatch.await(60, TimeUnit.SECONDS);
            } catch (final InterruptedException e) {
              Thread.currentThread().interrupt();
              throw new RuntimeException("Interrupted while waiting for shutdown", e);
            }
          }

          @Override
          public WaitStrategy withStartupTimeout(final Duration startupTimeout) {
            return this;
          }
        });
    final var thread = Thread.ofVirtual().start(() -> camunda.start());

    final var logToWaitFor =
        waitForSchemaStartupBeforeShutdown
            ? "Schema initialization for physical tenant 'default' failed on attempt"
            : "io.camunda.zeebe.broker.system - Starting broker";

    // just wait until the container is running (but not ready)
    Awaitility.await()
        .atMost(Duration.ofSeconds(60))
        .until(() -> camunda.isRunning() && camunda.getLogs().contains(logToWaitFor));

    // when
    // Simulate external shutdown signal
    LOG.info("Start graceful shutdown of the container");
    shutDownContainerGracefully(Duration.ofSeconds(30)); // as default Kubernetes shutdown timeout
    shutdownLatch.countDown();

    assertThat(thread.join(Duration.ofSeconds(10))).isTrue();

    // then
    Awaitility.await()
        .untilAsserted(
            () ->
                assertThat(camunda.getLogs())
                    .contains(
                        "org.springframework.boot.tomcat.GracefulShutdown - Graceful shutdown complete")
                    .contains("io.camunda.zeebe.broker.system - Broker shut down"));
    assertThat(camunda.getLogs())
        .doesNotContain("Failed to start application")
        .doesNotContain("BeanCreationException")
        .doesNotContain("Start operation executor");
  }

  /**
   * A node without an HTTP gateway does not wait for its schema at all, so an unreachable
   * Elasticsearch delays nothing: it starts, keeps retrying in the background, and reports the
   * tenant as degraded through the gauge. Its counterpart — the same node <em>with</em> a gateway,
   * which is expected to stay at the gate while a tenant can still make progress — is covered by
   * {@link #shouldGracefullyShutdownWhenSchemaStartupStillRunning} above.
   */
  @Test
  void shouldNotBlockStartupWhenCannotConnectToElasticAndNoHttpGatewayIsEnabled() throws Exception {
    // given - an Elasticsearch that is not there, so no physical tenant can apply its schema
    camunda
        .withEnv("CAMUNDA_DATA_SECONDARYSTORAGE_ELASTICSEARCH_URL", UNREACHABLE_ELASTICSEARCH_URL)
        .withEnv("CAMUNDA_DATABASE_URL", UNREACHABLE_ELASTICSEARCH_URL)
        .withEnv("CAMUNDA_TASKLIST_ELASTICSEARCH_URL", UNREACHABLE_ELASTICSEARCH_URL)
        .withEnv("CAMUNDA_OPERATE_ELASTICSEARCH_URL", UNREACHABLE_ELASTICSEARCH_URL)
        .withEnv("ZEEBE_BROKER_GATEWAY_ENABLE", "false")
        .withEnv("SPRING_PROFILES_ACTIVE", "broker,dev")
        .withExposedPorts(MONITORING_PORT)
        .waitingFor(managementEndpointsAnswering());

    // when
    camunda.start();

    // then - the node is up rather than crash-looping, and stays diagnosable
    assertThat(camunda.getLogs())
        .doesNotContain("Failed to start application")
        .doesNotContain("BeanCreationException");
    assertThatDefaultTenantGaugeReportsDegraded();
  }

  /**
   * The anti-hang regression test, and the one case where the gate refuses to open. When every
   * tenant has failed in a way retrying cannot repair, nothing is left that could ever produce a
   * serviceable tenant: a gate that only counted serviceable tenants would block in the context
   * refresh forever with no management endpoint to say why, and releasing would admit a node that
   * can serve nobody. It aborts instead, which is what it does today.
   *
   * <p>The failure is made terminal by recording a schema version in the tenant's metadata index
   * that the running version refuses to migrate from, which is an {@code
   * IncompatibleVersionException} — the one failure both the schema manager and this distribution
   * agree no retry can repair.
   */
  @Test
  void shouldAbortStartupWhenEveryTenantFailsTerminally() throws Exception {
    // given
    storeIncompatibleSchemaVersion();
    camunda
        .withEnv("ZEEBE_BROKER_GATEWAY_ENABLE", "true")
        .withEnv("SPRING_PROFILES_ACTIVE", "broker,dev")
        .waitingFor(runningContainer());

    // when
    camunda.start();

    // then - the classification reaches the gate, and the gate takes the node down rather than
    // hanging at it or admitting a node that can serve nobody
    Awaitility.await("startup aborts")
        .atMost(Duration.ofMinutes(2))
        .untilAsserted(
            () ->
                assertThat(camunda.getLogs())
                    .contains("failed with a cause that retrying cannot repair")
                    .contains("EveryTenantTerminallyFailedException"));

    // and - it exits non-zero, so an orchestrator restarts it and a rollout stops here rather
    // than replacing healthy nodes with ones that will never serve
    Awaitility.await("the node exits")
        .atMost(Duration.ofMinutes(1))
        .until(() -> !camunda.isRunning());
    assertThat(camunda.getCurrentContainerInfo().getState().getExitCodeLong()).isNotZero();
  }

  /**
   * Returns as soon as the container is running, without waiting for readiness — the only way to
   * observe a node that is expected to take itself down.
   */
  private static WaitStrategy runningContainer() {
    return new WaitStrategy() {
      @Override
      public void waitUntilReady(final WaitStrategyTarget waitStrategyTarget) {}

      @Override
      public WaitStrategy withStartupTimeout(final Duration startupTimeout) {
        return this;
      }
    };
  }

  /**
   * The management endpoints answering is the startup signal for a node that never becomes ready.
   * The "Started Camunda using ..." banner is printed long before the context finishes refreshing,
   * so it would pass while the gate still held.
   */
  private static WaitStrategy managementEndpointsAnswering() {
    return new WaitAllStrategy(Mode.WITH_OUTER_TIMEOUT)
        .withStrategy(new HostPortWaitStrategy())
        .withStrategy(
            new HttpWaitStrategy()
                .forPath("/actuator/prometheus")
                .forPort(MONITORING_PORT)
                .forStatusCode(200)
                .withReadTimeout(Duration.ofSeconds(10)))
        .withStartupTimeout(Duration.ofMinutes(2));
  }

  private void assertThatDefaultTenantGaugeReportsDegraded() throws Exception {
    assertThat(bodyOf("/actuator/prometheus").body())
        .as("the per-tenant readiness gauge reports the tenant as degraded")
        .containsPattern(
            "camunda_physical_tenant_secondary_storage_ready\\{[^}]*physicalTenant=\"default\"[^}]*}"
                + "\\s+0");
  }

  /**
   * Writes a schema version far enough behind the running one that {@code
   * VersionCompatibilityCheck} refuses it, whichever version this image was built from. Creating
   * the document also creates the metadata index, which is what makes the schema manager read it.
   */
  private void storeIncompatibleSchemaVersion() throws IOException, InterruptedException {
    final var metadataIndex = new MetadataIndex("", true).getFullQualifiedName();
    final var response =
        HttpClient.newHttpClient()
            .send(
                HttpRequest.newBuilder(
                        URI.create(
                            "http://"
                                + es.getHttpHostAddress()
                                + "/"
                                + metadataIndex
                                + "/_doc/schema-version?refresh=true"))
                    .header("Content-Type", "application/json")
                    .PUT(BodyPublishers.ofString("{\"id\":\"schema-version\",\"value\":\"1.0.0\"}"))
                    .build(),
                BodyHandlers.ofString());
    assertThat(response.statusCode()).as("%s", response.body()).isEqualTo(201);
  }

  private HttpResponse<String> bodyOf(final String actuatorPath)
      throws IOException, InterruptedException {
    final var uri =
        URI.create(
            "http://"
                + camunda.getHost()
                + ":"
                + camunda.getMappedPort(MONITORING_PORT)
                + actuatorPath);
    return HttpClient.newHttpClient()
        .send(HttpRequest.newBuilder(uri).GET().build(), BodyHandlers.ofString());
  }

  private void shutDownContainerGracefully(final Duration timeout) {
    final String containerId = camunda.getContainerId();
    if (containerId == null) {
      return;
    }
    camunda
        .getDockerClient()
        .stopContainerCmd(containerId)
        .withTimeout((int) timeout.getSeconds())
        .exec();
  }
}
