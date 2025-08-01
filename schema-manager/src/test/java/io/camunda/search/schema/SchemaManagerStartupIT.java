/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.schema;

import static io.camunda.zeebe.test.util.testcontainers.TestSearchContainers.createDefeaultElasticsearchContainer;
import static org.assertj.core.api.Assertions.assertThat;

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
  private static final String CAMUNDA_TEST_IMAGE_NAME =
      Optional.ofNullable(System.getenv("CAMUNDA_TEST_DOCKER_IMAGE"))
          .orElse("camunda/camunda:SNAPSHOT");
  private static final int MONITORING_PORT = 9600;

  @AutoClose
  private final GenericContainer<?> camunda =
      new GenericContainer<>(CAMUNDA_TEST_IMAGE_NAME)
          .withEnv("CAMUNDA_DATABASE_URL", "http://test-elasticsearch:9200")
          .withEnv("SPRING_PROFILES_ACTIVE", "broker,dev")
          .withEnv("LOGGING_LEVEL_IO_CAMUNDA", "DEBUG")
          .withNetwork(Network.SHARED);

  @Container
  private final ElasticsearchContainer es =
      createDefeaultElasticsearchContainer()
          // Enable security in ES for to trigger the retry logic as application does not have
          // credentials
          .withEnv("xpack.security.enabled", "true")
          .withNetwork(Network.SHARED)
          .withNetworkAliases("test-elasticsearch");

  @ParameterizedTest
  @CsvSource({"false, false", "false, true", "true, false", "true, true"})
  // waitForSchemaStartupBeforeShutdown:
  //   false is to test the case when the shutdown is before the schema startup is started
  //   true is to test the case when the shutdown is when the schema startup is retrying
  // gatewayEnabled:
  //   false is to test with embedded gateway disabled (async schema startup)
  //   true is to test with embedded gateway enabled (sync schema startup)
  void shouldGracefullyShutdownWhenSchemaStartupStillRunning(
      final boolean waitForSchemaStartupBeforeShutdown, final boolean gatewayEnabled)
      throws InterruptedException {
    // given
    final var shutdownLatch = new CountDownLatch(1);

    camunda
        .withEnv("ZEEBE_BROKER_GATEWAY_ENABLE", String.valueOf(gatewayEnabled))
        .waitingFor(
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
            ? "Retrying operation for 'init schema'"
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

    thread.join(Duration.ofSeconds(5));

    // then
    final var logs = camunda.getLogs();
    assertThat(logs)
        .contains(
            "org.springframework.boot.web.embedded.tomcat.GracefulShutdown - Graceful shutdown complete");
    assertThat(logs).contains("io.camunda.zeebe.broker.system - Broker shut down");
    assertThat(logs).doesNotContain("Failed to start application");
    assertThat(logs).doesNotContain("BeanCreationException");
  }

  @Test
  void shouldNotBlockStartupWhenCannotConnectToElasticAndEmbeddedGatewayIsDeactivated() {
    // given
    camunda
        .withEnv("ZEEBE_BROKER_GATEWAY_ENABLE", "false")
        .withExposedPorts(MONITORING_PORT)
        .waitingFor(newDefaultWaitStrategy());

    // when, should start with success, even if not able to connect to ES
    camunda.start();

    // then
    assertThat(camunda.getLogs()).contains("Started StandaloneCamunda");
  }

  private WaitAllStrategy newDefaultWaitStrategy() {
    return new WaitAllStrategy(Mode.WITH_OUTER_TIMEOUT)
        .withStrategy(new HostPortWaitStrategy())
        .withStrategy(
            new HttpWaitStrategy()
                .forPath("/ready")
                .forPort(MONITORING_PORT)
                .forStatusCodeMatching(status -> status >= 200 && status < 300)
                .withReadTimeout(Duration.ofSeconds(10)))
        .withStartupTimeout(Duration.ofMinutes(1));
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
