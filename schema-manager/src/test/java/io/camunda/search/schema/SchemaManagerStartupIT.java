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

import io.zeebe.containers.ZeebeContainer;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.WaitStrategy;
import org.testcontainers.containers.wait.strategy.WaitStrategyTarget;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class SchemaManagerStartupIT {

  private static final Logger LOG = LoggerFactory.getLogger(SchemaManagerStartupIT.class);

  private static final String CAMUNDA_TEST_IMAGE_NAME =
      Optional.ofNullable(System.getenv("CAMUNDA_TEST_DOCKER_IMAGE"))
          .orElse("camunda/camunda:SNAPSHOT");

  private final AtomicBoolean isShutdown = new AtomicBoolean(false);

  @AutoClose
  private final ZeebeContainer camunda =
      new ZeebeContainer(DockerImageName.parse(CAMUNDA_TEST_IMAGE_NAME))
          .withEnv("CAMUNDA_DATABASE_URL", "http://test-elasticsearch:9200")
          .withEnv("SPRING_PROFILES_ACTIVE", "broker")
          .withNetwork(Network.SHARED)
          .waitingFor(
              new WaitStrategy() {
                @Override
                public void waitUntilReady(final WaitStrategyTarget waitStrategyTarget) {
                  // Wait until the application is shutdown
                  Awaitility.await()
                      .timeout(Duration.ofSeconds(60))
                      .pollDelay(Duration.ofSeconds(1))
                      .untilTrue(isShutdown);
                }

                @Override
                public WaitStrategy withStartupTimeout(final Duration startupTimeout) {
                  return this;
                }
              });

  @Container
  private final ElasticsearchContainer es =
      createDefeaultElasticsearchContainer()
          // Enable security in ES for to trigger the retry logic as application does not have
          // credentials
          .withEnv("xpack.security.enabled", "true")
          .withNetwork(Network.SHARED)
          .withNetworkAliases("test-elasticsearch");

  @AutoClose private final ExecutorService executor = Executors.newSingleThreadExecutor();

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  // false is to test the case when the shutdown is before the schema startup is started
  // true is to test the case when the shutdown is when the schema startup is retrying
  void shouldGracefullyShutdownWhenSchemaStartupStillRunning(final boolean waitForSchemaStartup)
      throws InterruptedException {
    // given
    executor.submit(() -> camunda.start());

    final var logToWaitFor =
        waitForSchemaStartup
            ? "Initializing search engine schema"
            : "Root WebApplicationContext: initialization completed";

    // just wait until the container is running (but not ready)
    Awaitility.await()
        .atMost(Duration.ofSeconds(60))
        .until(() -> camunda.isRunning() && camunda.getLogs().contains(logToWaitFor));
    // Let the startup proceed a little
    Thread.sleep(2_000);

    // when
    // Simulate external shutdown signal
    LOG.info("Start graceful shutdown of the container");
    camunda.shutdownGracefully(Duration.ofSeconds(30)); // as default Kubernetes shutdown timeout
    isShutdown.set(true);

    executor.awaitTermination(5, TimeUnit.SECONDS);

    // then
    final var logs = camunda.getLogs();
    assertThat(logs)
        .contains(
            "org.springframework.boot.web.embedded.tomcat.GracefulShutdown - Graceful shutdown complete");
    assertThat(logs).contains("io.camunda.zeebe.broker.system - Broker shut down");
    assertThat(logs).doesNotContain("Failed to start application");
    assertThat(logs).doesNotContain("BeanCreationException");
  }
}
