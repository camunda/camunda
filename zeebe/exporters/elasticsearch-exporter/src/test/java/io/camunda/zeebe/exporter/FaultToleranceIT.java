/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.exporter.test.ExporterTestConfiguration;
import io.camunda.zeebe.exporter.test.ExporterTestContext;
import io.camunda.zeebe.exporter.test.ExporterTestController;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import io.camunda.zeebe.util.VersionUtil;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.SocatContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

/**
 * A separate suite to run tests when Elasticsearch is down, and that exporting works again once it
 * is back up.
 */
final class FaultToleranceIT {

  private final ElasticsearchExporterConfiguration config =
      new ElasticsearchExporterConfiguration();
  // omit authorizations since they are removed from the records during serialization
  private final ProtocolFactory factory = new ProtocolFactory(b -> b.withAuthorizations(Map.of()));
  private final ExporterTestController controller = new ExporterTestController();
  private final ElasticsearchExporter exporter = new ElasticsearchExporter();
  private final RecordIndexRouter indexRouter = new RecordIndexRouter(config.index);

  @Test
  void shouldExportEvenIfElasticNotInitiallyReachable() {
    // given
    final var record =
        factory.generateRecord(
            ValueType.VARIABLE, r -> r.withBrokerVersion(VersionUtil.getVersionLowerCase()));
    config.bulk.size = 1; // force flushing after a single record
    config.index.variable = true;
    config.index.createTemplate = true;

    try (final Network network = Network.newNetwork();
        final SocatContainer proxy =
            new SocatContainer()
                .withTarget(9200, "elastic")
                .withNetwork(network)
                .withNetworkAliases("proxy")) {
      try (final ElasticsearchContainer container =
          TestSearchContainers.createDefeaultElasticsearchContainer()
              .withNetwork(network)
              .withNetworkAliases("elastic")) {
        // fix the ports beforehand - since we don't know the container port until it starts, and we
        // want to start it after the exporter is running, we need a fixed, predictable endpoint;
        // this
        // can be done by using socat to proxy Elastic's 9200 port to a predictable endpoint without
        // starting Elastic
        proxy.start();
        config.url = container.getHost() + ":" + proxy.getMappedPort(9200);

        exporter.configure(
            new ExporterTestContext()
                .setConfiguration(new ExporterTestConfiguration<>("elastic", config)));
        exporter.open(controller);

        // when
        assertThatThrownBy(() -> exporter.export(record))
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
}
