/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.exporter;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.exporter.api.ExporterException;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.test.util.TestUtil;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import org.junit.Test;

public class ElasticsearchExporterConfigurationIT
    extends AbstractElasticsearchExporterIntegrationTestCase {

  @Test
  public void shouldPropagateNumberOfShardsAndReplicas() {
    // given
    elastic.start();

    configuration = getDefaultConfiguration();

    // change number of shards and replicas
    configuration.index.setNumberOfShards(5);
    configuration.index.setNumberOfReplicas(4);

    esClient = createElasticsearchClient(configuration);

    // when
    exporterBrokerRule.configure("es", ElasticsearchExporter.class, configuration);
    exporterBrokerRule.start();
    exporterBrokerRule.publishMessage("message", "123");

    // then
    RecordingExporter.messageRecords()
        .withCorrelationKey("123")
        .withName("message")
        .forEach(r -> TestUtil.waitUntil(() -> wasExported(r)));
    assertIndexSettings();
  }

  @Test
  public void shouldFailWhenNumberOfShardsIsLessOne() {
    // given
    elastic.start();

    configuration = getDefaultConfiguration();

    // change number of shards
    configuration.index.setNumberOfShards(-1);

    esClient = createElasticsearchClient(configuration);

    // when
    exporterBrokerRule.configure("es", ElasticsearchExporter.class, configuration);

    // then
    assertThatThrownBy(() -> exporterBrokerRule.start())
        .isInstanceOf(IllegalStateException.class)
        .getRootCause()
        .isInstanceOf(ExporterException.class)
        .hasMessageContaining("numberOfShards must be >= 1. Current value: -1");
  }

  @Test
  public void shouldFailWhenNumberOfReplicasIsLessZero() {
    // given
    elastic.start();

    configuration = getDefaultConfiguration();

    // change number replicas
    configuration.index.setNumberOfReplicas(-1);

    esClient = createElasticsearchClient(configuration);

    // when
    exporterBrokerRule.configure("es", ElasticsearchExporter.class, configuration);

    // then
    assertThatThrownBy(() -> exporterBrokerRule.start())
        .isInstanceOf(IllegalStateException.class)
        .getRootCause()
        .isInstanceOf(ExporterException.class)
        .hasMessageContaining("numberOfReplicas must be >= 0. Current value: -1");
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
