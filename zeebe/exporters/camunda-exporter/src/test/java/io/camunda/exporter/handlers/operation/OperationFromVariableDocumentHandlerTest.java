/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers.operation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.exporter.index.TargetIndex;
import io.camunda.exporter.index.TargetIndexLocator;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.VariableDocumentIntent;
import io.camunda.zeebe.protocol.record.value.VariableDocumentRecordValue;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OperationFromVariableDocumentHandlerTest
    extends AbstractOperationHandlerTest<VariableDocumentRecordValue> {

  @BeforeEach
  void setUp() {
    underTest = new OperationFromVariableDocumentHandler(indexName);
    valueType = ValueType.VARIABLE_DOCUMENT;
  }

  @Test
  void shouldHandleRecord() {
    final var record = generateRecord(VariableDocumentIntent.UPDATED);
    assertThat(underTest.handlesRecord(record)).isTrue();
  }

  @Test
  void shouldNotHandleRecord() {
    Stream.of(VariableDocumentIntent.values())
        .filter(intent -> intent != VariableDocumentIntent.UPDATED)
        .map(this::generateRecord)
        .forEach(record -> assertThat(underTest.handlesRecord(record)).isFalse());
  }

  @Override
  TargetIndexLocator setupMockIndexLocator(final TargetIndex index) {
    final var indexLocator = mock(TargetIndexLocator.class);
    when(indexLocator.locateMainIndex(indexName)).thenReturn(index);
    return indexLocator;
  }
}
