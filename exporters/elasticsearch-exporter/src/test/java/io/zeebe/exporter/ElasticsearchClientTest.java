/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.exporter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.value.VariableRecordValue;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElasticsearchClientTest {

  private static final long RECORD_KEY = 1234L;
  private ElasticsearchExporterConfiguration configuration;
  private Logger logSpy;
  private ElasticsearchClient client;

  @Before
  public void setUp() {
    configuration = new ElasticsearchExporterConfiguration();
    logSpy = spy(LoggerFactory.getLogger(ElasticsearchClientTest.class));
    client = new ElasticsearchClient(configuration, logSpy);
  }

  @Test
  public void shouldNotLogWarningWhenIndexingSmallVariableValue() {
    // given
    final String variableValue = "x".repeat(configuration.index.ignoreVariablesAbove);

    final Record<VariableRecordValue> recordMock = mock(Record.class);
    when(recordMock.getPartitionId()).thenReturn(1);
    when(recordMock.getKey()).thenReturn(RECORD_KEY);
    when(recordMock.getValueType()).thenReturn(ValueType.VARIABLE);
    when(recordMock.toJson()).thenReturn("{}");

    final VariableRecordValue value = mock(VariableRecordValue.class);
    when(value.getValue()).thenReturn(variableValue);
    when(recordMock.getValue()).thenReturn(value);

    // when
    client.index(recordMock);

    // then
    verify(logSpy, never()).warn(anyString(), ArgumentMatchers.<Object[]>any());
  }

  @Test
  public void shouldLogWarnWhenIndexingLargeVariableValue() {
    // given
    final String variableName = "varName";
    final String variableValue = "x".repeat(configuration.index.ignoreVariablesAbove + 1);
    final long scopeKey = 1234L;
    final long workflowInstanceKey = 5678L;

    final Record<VariableRecordValue> recordMock = mock(Record.class);
    when(recordMock.getPartitionId()).thenReturn(1);
    when(recordMock.getKey()).thenReturn(RECORD_KEY);
    when(recordMock.getValueType()).thenReturn(ValueType.VARIABLE);
    when(recordMock.toJson()).thenReturn("{}");

    final VariableRecordValue value = mock(VariableRecordValue.class);
    when(value.getName()).thenReturn(variableName);
    when(value.getValue()).thenReturn(variableValue);
    when(value.getScopeKey()).thenReturn(scopeKey);
    when(value.getWorkflowInstanceKey()).thenReturn(workflowInstanceKey);

    when(recordMock.getValue()).thenReturn(value);

    // when
    client.index(recordMock);

    // then
    final ArgumentCaptor<Object[]> argumentCaptor = ArgumentCaptor.forClass(Object[].class);

    verify(logSpy).warn(anyString(), argumentCaptor.capture());

    // required to explicitly cast List<Objects[]> to List<Object>
    final List<Object> args = new ArrayList<>(argumentCaptor.getAllValues());
    assertThat(args)
        .contains(
            RECORD_KEY,
            variableName,
            variableValue.getBytes().length,
            scopeKey,
            workflowInstanceKey);
  }
}
