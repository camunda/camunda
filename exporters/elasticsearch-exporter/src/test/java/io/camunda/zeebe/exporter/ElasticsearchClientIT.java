/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.exporter;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import org.junit.Before;
import org.junit.Test;

public class ElasticsearchClientIT extends AbstractElasticsearchExporterIntegrationTestCase {
  private final ProtocolFactory recordFactory = new ProtocolFactory();

  private ElasticsearchExporterConfiguration config;
  private ElasticsearchClient client;
  private BulkIndexRequest bulkRequest;

  @Before
  public void init() {
    elastic.start();

    config = getDefaultConfiguration();
    bulkRequest = new BulkIndexRequest();
    client = new ElasticsearchClient(config, bulkRequest);
  }

  @Test
  public void shouldThrowExceptionIfFailToFlushBulk() {
    // given - a record with a negative timestamp will not be indexed because its field in ES is a
    // date, which must be a positive number of milliseconds since the UNIX epoch
    final var invalidRecord =
        recordFactory.generateRecord(ValueType.VARIABLE, b -> b.withTimestamp(Long.MIN_VALUE));
    client.index(invalidRecord);
    client.putComponentTemplate();
    client.putIndexTemplate(ValueType.VARIABLE);

    // when/then
    assertThatThrownBy(client::flush)
        .isInstanceOf(ElasticsearchExporterException.class)
        .hasMessageContaining(
            "Failed to flush bulk request: [Failed to flush 1 item(s) of bulk request [type: mapper_parsing_exception, reason: failed to parse field [timestamp]");
  }
}
