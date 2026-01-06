/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.opensearch;

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
import org.opensearch.testcontainers.OpensearchContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.SocatContainer;

/**
 * A separate suite to run tests when Opensearch is down, and that exporting works again once it is
 * back up.
 */
final class FaultToleranceIT {

  private final OpensearchExporterConfiguration config = new OpensearchExporterConfiguration();
  // omit authorizations since they are removed from the records during serialization
  private final ProtocolFactory factory = new ProtocolFactory(b -> b.withAuthorizations(Map.of()));
  private final ExporterTestController controller = new ExporterTestController();
  private final OpensearchExporter exporter = new OpensearchExporter();
  private final RecordIndexRouter indexRouter = new RecordIndexRouter(config.index);

  @Test
  void shouldExportEvenIfOpensearchNotInitiallyReachable() {
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
                .withTarget(9200, "opensearch")
                .withNetwork(network)
                .withNetworkAliases("proxy")) {
      try (final OpensearchContainer<?> container =
          TestSearchContainers.createDefaultOpensearchContainer()
              .withNetwork(network)
              .withNetworkAliases("opensearch")) {
        // fix the ports beforehand - since we don't know the container port until it starts, and we
        // want to start it after the exporter is running, we need a fixed, predictable endpoint;
        // this
        // can be done by using socat to proxy Open's 9200 port to a predictable endpoint without
        // starting Open
        proxy.start();
        config.url = container.getHost() + ":" + proxy.getMappedPort(9200);

        exporter.configure(
            new ExporterTestContext()
                .setConfiguration(new ExporterTestConfiguration<>("opensearch", config)));
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
