/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.exporter;

import io.zeebe.protocol.record.Record;
import io.zeebe.test.util.TestUtil;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.socket.SocketUtil;
import org.junit.Test;

public class ElasticsearchExporterFaultToleranceIT
    extends AbstractElasticsearchExporterIntegrationTestCase {

  @Test
  public void shouldExportEvenIfElasticNotInitiallyReachable() {
    // given
    elastic.withPort(SocketUtil.getNextAddress().getPort());
    configuration = getDefaultConfiguration();
    configuration.index.prefix = "zeebe";
    esClient = createElasticsearchClient(configuration);

    // when
    exporterBrokerRule.configure("es", ElasticsearchExporter.class, configuration);
    exporterBrokerRule.start();
    exporterBrokerRule.publishMessage("message", "123");
    elastic.start();

    // then
    RecordingExporter.messageRecords()
        .withCorrelationKey("123")
        .withName("message")
        .forEach(r -> TestUtil.waitUntil(() -> wasExported(r)));
    assertIndexSettings();
  }

  private boolean wasExported(final Record<?> record) {
    try {
      return esClient.getDocument(record) != null;
    } catch (final Exception e) {
      // suppress exception in order to retry and see if it was exported yet or not
      // the exception can occur since elastic may not be ready yet, or maybe the index hasn't been
      // created yet, etc.
    }

    return false;
  }
}
