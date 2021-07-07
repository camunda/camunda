/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.exporter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import java.util.ArrayList;
import java.util.function.Function;
import java.util.stream.IntStream;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.LoggerFactory;

public class ElasticsearchClientIT extends AbstractElasticsearchExporterIntegrationTestCase {

  private static final long RECORD_KEY = 1234L;
  private ElasticsearchExporterConfiguration configuration;
  private ElasticsearchClient client;
  private ArrayList<String> bulkRequest;

  @Before
  public void init() {
    elastic.start();

    configuration = getDefaultConfiguration();
    bulkRequest = new ArrayList<>();
    client =
        new ElasticsearchClient(
            configuration, LoggerFactory.getLogger(ElasticsearchClientIT.class), bulkRequest);
  }

  @Test
  public void shouldThrowExceptionIfFailToFlushBulk() {
    // given
    final int bulkSize = 10;

    final Record<VariableRecordValue> recordMock = mock(Record.class);
    when(recordMock.getPartitionId()).thenReturn(1);
    when(recordMock.getValueType()).thenReturn(ValueType.PROCESS_INSTANCE);

    // bulk contains records that fail on flush
    IntStream.range(0, bulkSize)
        .forEach(
            i -> {
              when(recordMock.getKey()).thenReturn(RECORD_KEY + i);
              when(recordMock.toJson()).thenReturn("invalid-json-" + i);
              client.index(recordMock);
            });

    // and one valid record
    when(recordMock.getKey()).thenReturn(RECORD_KEY + bulkSize);
    when(recordMock.toJson()).thenReturn("{}");
    client.index(recordMock);

    // when/then
    assertThatThrownBy(client::flush)
        .isInstanceOf(ElasticsearchExporterException.class)
        .hasMessageContaining(
            "Failed to flush 10 item(s) of bulk request [type: mapper_parsing_exception, reason: failed to parse]");
  }

  @Test
  public void shouldIgnoreRecordIfDuplicateOfLast() {
    // given
    final Record<VariableRecordValue> recordMock = mock(Record.class);
    when(recordMock.getPartitionId()).thenReturn(1);
    when(recordMock.getValueType()).thenReturn(ValueType.PROCESS_INSTANCE);
    when(recordMock.getKey()).thenReturn(RECORD_KEY + 1);
    when(recordMock.toJson()).thenReturn("{}");

    client.index(recordMock);
    assertThat(bulkRequest).hasSize(1);

    // when
    client.index(recordMock);

    // then
    assertThat(bulkRequest).hasSize(1);
  }

  @Test
  public void shouldFlushOnMemoryLimit() {
    // given
    final var bulkMemoryLimit = 1024;
    final var recordSize = 2;

    configuration.bulk.memoryLimit = bulkMemoryLimit;
    configuration.bulk.size = Integer.MAX_VALUE;
    configuration.bulk.delay = Integer.MAX_VALUE;

    final var variableValue1 = "x".repeat(bulkMemoryLimit / recordSize);
    final var variableValue2 = "y".repeat(bulkMemoryLimit / recordSize);
    final Function<String, String> jsonRecord =
        (String value) -> String.format("{\"value\":\"%s\"}", value);

    final VariableRecordValue recordValue = mock(VariableRecordValue.class);
    when(recordValue.getValue()).thenReturn(variableValue1);

    final Record<VariableRecordValue> recordMock = mock(Record.class);
    when(recordMock.getKey()).thenReturn(1L);
    when(recordMock.getPartitionId()).thenReturn(1);
    when(recordMock.getValueType()).thenReturn(ValueType.VARIABLE);
    when(recordMock.getValue()).thenReturn(recordValue);
    when(recordMock.toJson()).thenReturn(jsonRecord.apply(variableValue1));

    // when
    client.index(recordMock);

    assertThat(client.shouldFlush()).isFalse();

    when(recordMock.getKey()).thenReturn(2L);
    when(recordMock.toJson()).thenReturn(jsonRecord.apply(variableValue2));

    client.index(recordMock);

    // then
    assertThat(client.shouldFlush()).isTrue();
  }
}
