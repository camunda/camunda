/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.exporter;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.exporter.test.ExporterTestConfiguration;
import io.camunda.zeebe.exporter.test.ExporterTestContext;
import io.camunda.zeebe.exporter.test.ExporterTestController;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import org.junit.jupiter.api.Test;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

/**
 * A separate suite to run tests when Elasticsearch is down, and that exporting works again once it
 * is back up.
 */
final class FaultToleranceIT {
  private final ElasticsearchExporterConfiguration config =
      new ElasticsearchExporterConfiguration();
  private final ProtocolFactory factory = new ProtocolFactory();
  private final ExporterTestController controller = new ExporterTestController();
  private final ElasticsearchExporter exporter = new ElasticsearchExporter();
  private final IndexRouter indexRouter = new IndexRouter(config.index);

  @Test
  void shouldExportEvenIfElasticNotInitiallyReachable() {
    // given
    final var elasticPort = SocketUtil.getNextAddress().getPort();
    final var record = factory.generateRecord(ValueType.VARIABLE);
    config.bulk.size = 1; // force flushing after a single record
    config.index.variable = true;
    config.index.createTemplate = true;

    try (final ElasticsearchContainer container = TestSupport.createDefaultContainer()) {
      // fix the ports beforehand - since we don't know the container port until it starts, and we
      // want to start it after the exporter is running, we need to fix it to whatever we set in the
      // exporter's configuration
      // we cannot use the `getHttpHost` method here either since that one will not use the fixed
      // port we added
      config.url = container.getHost() + ":" + elasticPort;
      container.getPortBindings().add(String.format("%d:9200/tcp", elasticPort));

      exporter.configure(
          new ExporterTestContext()
              .setConfiguration(new ExporterTestConfiguration<>("elastic", config)));
      exporter.open(controller);

      // when
      ElasticsearchExporterException connectException = null;
      try {
        exporter.export(record);
      } catch (final ElasticsearchExporterException e) { // expected since ES is down
        connectException = e;
      }
      assertThat(connectException)
          .as("sanity check: should have failed to export since ES was down")
          .isNotNull();
      container.start();

      // then
      exporter.export(record);

      try (final var testClient = new TestClient(config, indexRouter)) {
        final var response = testClient.getExportedDocumentFor(record);
        assertThat(response.source()).isEqualTo(record);
      }
    }
  }
}
