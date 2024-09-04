/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter;

import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.GetResponse;
import io.camunda.exporter.clients.elasticsearch.ElasticsearchClientFactory;
import io.camunda.exporter.config.ElasticsearchExporterConfiguration;
import io.camunda.exporter.entities.UserEntity;
import io.camunda.exporter.utils.TestSupport;
import io.camunda.zeebe.exporter.test.ExporterTestConfiguration;
import io.camunda.zeebe.exporter.test.ExporterTestContext;
import io.camunda.zeebe.exporter.test.ExporterTestController;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.value.UserRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.io.IOException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * This is a smoke test to verify that the exporter can connect to an Elasticsearch instance and
 * export records using the configured handlers.
 */
@Testcontainers
@TestInstance(Lifecycle.PER_CLASS)
final class CamundaExporterIT {
  @Container
  private static final ElasticsearchContainer CONTAINER =
      TestSupport.createDefaultContainer().withEnv("action.destructive_requires_name", "false");

  private final ElasticsearchExporterConfiguration config =
      new ElasticsearchExporterConfiguration();
  private final ExporterTestController controller = new ExporterTestController();
  private final CamundaExporter exporter = new CamundaExporter();
  private final ProtocolFactory factory = new ProtocolFactory();

  private ExporterTestContext exporterTestContext;

  private ElasticsearchClient testClient;

  @BeforeAll
  public void beforeAll() {
    config.elasticsearch.setUrl(CONTAINER.getHttpHostAddress());
    config.bulk.setSize(1); // force flushing on the first record

    exporterTestContext =
        new ExporterTestContext()
            .setConfiguration(new ExporterTestConfiguration<>("elastic", config));
    exporter.configure(exporterTestContext);
    exporter.open(controller);

    testClient = ElasticsearchClientFactory.INSTANCE.create(config.elasticsearch);
  }

  @AfterAll
  void afterAll() throws IOException {
    testClient._transport().close();
  }

  @Test
  void shouldExportUserRecord() throws IOException {
    // given
    final Record<UserRecordValue> record = factory.generateRecord(ValueType.USER);

    // when
    exporter.export(record);

    // then
    final String id = String.valueOf(record.getKey());
    final var response = testClient.get(b -> b.id(id).index("users"), UserEntity.class);
    assertThat(response)
        .extracting(GetResponse::index, GetResponse::id)
        .containsExactly("users", id);

    assertThat(response.source())
        .describedAs("User entity is updated correctly from the user record")
        .extracting(UserEntity::getEmail, UserEntity::getName, UserEntity::getUsername)
        .containsExactly(
            record.getValue().getEmail(),
            record.getValue().getName(),
            record.getValue().getUsername());
  }
}
