/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter;

import static io.camunda.exporter.utils.CamundaExporterSchemaUtils.createSchemas;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.search.schema.exceptions.SearchEngineException;
import io.camunda.zeebe.exporter.test.ExporterTestConfiguration;
import io.camunda.zeebe.exporter.test.ExporterTestContext;
import io.camunda.zeebe.exporter.test.ExporterTestController;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import java.io.IOException;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public class CamundaExporterAuthenticationIT {

  private static final String ELASTIC_USER = "elastic";
  private static final String ELASTIC_PASSWORD = "PASSWORD";

  /**
   * The default wait strategy only waits for the node to log {@code started}, which Elasticsearch
   * does before the {@code .security} index is allocated. Until that index is available the
   * reserved realm cannot read the {@code elastic} user's password hash and rejects valid
   * credentials with a {@code 401}, so wait for an authenticated request to succeed instead.
   */
  @Container
  private static final ElasticsearchContainer CONTAINER =
      TestSearchContainers.createDefaultElasticsearchContainer()
          .withPassword(ELASTIC_PASSWORD)
          .withEnv("xpack.security.enabled", "true")
          .waitingFor(
              new HttpWaitStrategy()
                  .forPort(9200)
                  .forPath("/_cluster/health")
                  .withBasicCredentials(ELASTIC_USER, ELASTIC_PASSWORD)
                  .forStatusCode(200)
                  .withStartupTimeout(Duration.ofMinutes(5)));

  private final ExporterConfiguration config = new ExporterConfiguration();
  private final ProtocolFactory factory = new ProtocolFactory();
  private final ExporterTestController controller = new ExporterTestController();

  @BeforeEach
  void beforeEach() throws IOException {
    config.getConnect().setUsername(ELASTIC_USER);
    config.getConnect().setPassword(ELASTIC_PASSWORD);
    config.getConnect().setUrl(CONTAINER.getHttpHostAddress());
    createSchemas(config);
  }

  @Test
  void shouldConnectToElasticsearchWithUsernameAndPassword() {
    // given
    final var exporter = new CamundaExporter();

    final var context =
        new ExporterTestContext()
            .setConfiguration(new ExporterTestConfiguration<>("elastic", config));

    // when
    exporter.configure(context);

    // then
    assertThatNoException().isThrownBy(() -> exporter.open(controller));
  }

  @Test
  void shouldFailToAuthenticateForWrongCredentials() {
    // given
    final var exporter = new CamundaExporter();
    config.getConnect().setPassword("123");

    final var context =
        new ExporterTestContext()
            .setConfiguration(new ExporterTestConfiguration<>("elastic", config));

    // when
    exporter.configure(context);

    // then
    assertThatThrownBy(() -> exporter.open(controller))
        .isInstanceOf(SearchEngineException.class)
        .cause()
        .hasMessageContaining("unable to authenticate user");
  }
}
