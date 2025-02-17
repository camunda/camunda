/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import java.util.List;
import org.junit.jupiter.api.Test;

class FlowNodeExportHandlerTest {

  @Test
  void shouldBuildTreePathWithElementInstancePath() {
    // given
    final Record<ProcessInstanceRecordValue> record = mock(Record.class);
    final ProcessInstanceRecordValue value = mock(ProcessInstanceRecordValue.class);
    when(record.getValue()).thenReturn(value);
    when(value.getElementInstancePath()).thenReturn(List.of(List.of(1L, 2L, 3L)));
    when(value.getProcessInstanceKey()).thenReturn(123L);
    when(record.getKey()).thenReturn(456L);

    // when
    final String treePath = FlowNodeExportHandler.buildTreePath(record);

    // then
    assertThat(treePath).isEqualTo("1/2/3");
  }

  @Test
  void shouldBuildTreePathWithElementInstancePathForLastProcessOnly() {
    // given
    final Record<ProcessInstanceRecordValue> record = mock(Record.class);
    final ProcessInstanceRecordValue value = mock(ProcessInstanceRecordValue.class);
    when(record.getValue()).thenReturn(value);
    when(value.getElementInstancePath()).thenReturn(List.of(List.of(1L, 2L, 3L), List.of(4L, 5L)));
    when(value.getProcessInstanceKey()).thenReturn(123L);
    when(record.getKey()).thenReturn(456L);

    // when
    final String treePath = FlowNodeExportHandler.buildTreePath(record);

    // then
    assertThat(treePath).isEqualTo("4/5");
  }

  @Test
  void shouldBuildTreePathWithoutElementInstancePath() {
    // given
    final Record<ProcessInstanceRecordValue> record = mock(Record.class);
    final ProcessInstanceRecordValue value = mock(ProcessInstanceRecordValue.class);
    when(record.getValue()).thenReturn(value);
    when(value.getElementInstancePath()).thenReturn(null);
    when(value.getProcessInstanceKey()).thenReturn(123L);
    when(record.getKey()).thenReturn(456L);

    // when
    final String treePath = FlowNodeExportHandler.buildTreePath(record);

    // then
    assertThat(treePath).isEqualTo("123/456");
  }

  @Test
  void shouldBuildTreePathWithEmptyElementInstancePath() {
    // given
    final Record<ProcessInstanceRecordValue> record = mock(Record.class);
    final ProcessInstanceRecordValue value = mock(ProcessInstanceRecordValue.class);
    when(record.getValue()).thenReturn(value);
    when(value.getElementInstancePath()).thenReturn(List.of());
    when(value.getProcessInstanceKey()).thenReturn(123L);
    when(record.getKey()).thenReturn(456L);

    // when
    final String treePath = FlowNodeExportHandler.buildTreePath(record);

    // then
    assertThat(treePath).isEqualTo("123/456");
  }
}
