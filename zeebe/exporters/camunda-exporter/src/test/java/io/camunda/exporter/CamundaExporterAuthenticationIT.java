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
import io.camunda.exporter.exceptions.ElasticsearchExporterException;
import io.camunda.zeebe.exporter.test.ExporterTestConfiguration;
import io.camunda.zeebe.exporter.test.ExporterTestContext;
import io.camunda.zeebe.exporter.test.ExporterTestController;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public class CamundaExporterAuthenticationIT {

  private static final String ELASTIC_PASSWORD = "PASSWORD";
  private static final ExporterConfiguration CONFIG = new ExporterConfiguration();

  @Container
  private static final ElasticsearchContainer CONTAINER =
      TestSearchContainers.createDefeaultElasticsearchContainer()
          .withPassword(ELASTIC_PASSWORD)
          .withEnv("xpack.security.enabled", "true");

  private final ProtocolFactory factory = new ProtocolFactory();
  private final ExporterTestController controller = new ExporterTestController();

  @BeforeEach
  void beforeEach() throws IOException {
    CONFIG.getConnect().setUsername("elastic");
    CONFIG.getConnect().setPassword(ELASTIC_PASSWORD);
    CONFIG.getConnect().setUrl(CONTAINER.getHttpHostAddress());
    createSchemas(CONFIG);
  }

  @Test
  void shouldConnectToElasticsearchWithUsernameAndPassword() {
    // given
    final var exporter = new CamundaExporter();

    final var context =
        new ExporterTestContext()
            .setConfiguration(new ExporterTestConfiguration<>("elastic", CONFIG));

    // when
    exporter.configure(context);

    // then
    assertThatNoException().isThrownBy(() -> exporter.open(controller));
  }

  @Test
  void shouldFailToAuthenticateForWrongCredentials() {
    // given
    final var exporter = new CamundaExporter();
    CONFIG.getConnect().setPassword("123");

    final var context =
        new ExporterTestContext()
            .setConfiguration(new ExporterTestConfiguration<>("elastic", CONFIG));

    // when
    exporter.configure(context);

    // then
    assertThatThrownBy(() -> exporter.open(controller))
        .isInstanceOf(ElasticsearchExporterException.class)
        .cause()
        .hasMessageContaining("unable to authenticate user");
  }
}
