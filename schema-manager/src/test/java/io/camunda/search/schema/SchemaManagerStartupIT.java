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
import io.camunda.webapps.schema.descriptors.index.RoleIndex;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
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
  //   both values now take the same schema-startup path — startup blocks until every physical
  //   tenant has produced a first outcome either way — but they exercise different shutdown
  //   sequences, since only the enabled gateway brings up the web server that has to shut down
  //   gracefully alongside the broker
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
   * The regression test for the central promise of ADR 004: no storage failure aborts the context,
   * not for one physical tenant and not when none of them can be initialized. It is parameterised
   * over the embedded gateway because that used to select between a blocking and a non-blocking
   * schema startup — both paths now block on the settle barrier and both must come up.
   */
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void shouldStartAndServeManagementEndpointsWhenNoTenantCanInitializeItsSchema(
      final boolean gatewayEnabled) throws Exception {
    // given - an Elasticsearch that is not there, so no physical tenant can apply its schema
    camunda
        .withEnv("CAMUNDA_DATA_SECONDARYSTORAGE_ELASTICSEARCH_URL", UNREACHABLE_ELASTICSEARCH_URL)
        .withEnv("CAMUNDA_DATABASE_URL", UNREACHABLE_ELASTICSEARCH_URL)
        .withEnv("CAMUNDA_TASKLIST_ELASTICSEARCH_URL", UNREACHABLE_ELASTICSEARCH_URL)
        .withEnv("CAMUNDA_OPERATE_ELASTICSEARCH_URL", UNREACHABLE_ELASTICSEARCH_URL)
        .withEnv("ZEEBE_BROKER_GATEWAY_ENABLE", String.valueOf(gatewayEnabled))
        .withEnv("SPRING_PROFILES_ACTIVE", "broker,dev")
        .withExposedPorts(MONITORING_PORT)
        // the management endpoints answering is the startup signal here; readiness never comes,
        // and the "Started Camunda using ..." banner is printed long before the context refreshes
        .waitingFor(
            new WaitAllStrategy(Mode.WITH_OUTER_TIMEOUT)
                .withStrategy(new HostPortWaitStrategy())
                .withStrategy(
                    new HttpWaitStrategy()
                        .forPath("/actuator/prometheus")
                        .forPort(MONITORING_PORT)
                        .forStatusCode(200)
                        .withReadTimeout(Duration.ofSeconds(10)))
                .withStartupTimeout(Duration.ofMinutes(2)));

    // when
    camunda.start();

    // then - the node is up rather than crash-looping, and stays diagnosable
    assertThat(camunda.getLogs())
        .doesNotContain("Failed to start application")
        .doesNotContain("BeanCreationException");
    assertThat(bodyOf("/actuator/prometheus").body())
        .as("the per-tenant readiness gauge reports the tenant as degraded")
        .containsPattern(
            "camunda_physical_tenant_secondary_storage_ready\\{[^}]*physicalTenant=\"default\"[^}]*}"
                + "\\s+0");

    // and - it withholds traffic through readiness instead. The schema indicator only joins the
    // readiness group when an HTTP gateway is enabled, so with the gateway off there is nothing
    // in that group yet that a degraded tenant can pull down (see #51861).
    if (gatewayEnabled) {
      assertThat(bodyOf("/actuator/health/readiness").statusCode()).isEqualTo(503);
    }
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
