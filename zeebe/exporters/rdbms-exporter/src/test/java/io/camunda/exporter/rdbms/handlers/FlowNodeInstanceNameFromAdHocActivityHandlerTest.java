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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.db.rdbms.write.service.FlowNodeInstanceWriter;
import io.camunda.zeebe.exporter.common.cache.ExporterEntityCache;
import io.camunda.zeebe.exporter.common.cache.process.CachedProcessEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FlowNodeInstanceNameFromAdHocActivityHandlerTest {

  private static final long PROCESS_DEFINITION_KEY = 42L;
  private static final long PARENT_INNER_INSTANCE_KEY = 999L;

  private final FlowNodeInstanceWriter writer = mock(FlowNodeInstanceWriter.class);

  @SuppressWarnings("unchecked")
  private final ExporterEntityCache<Long, CachedProcessEntity> processCache =
      mock(ExporterEntityCache.class);

  private final FlowNodeInstanceNameFromAdHocActivityHandler handler =
      new FlowNodeInstanceNameFromAdHocActivityHandler(writer, processCache);

  @Test
  void shouldExportActivatingAdHocEntryElement() {
    // given
    when(processCache.get(PROCESS_DEFINITION_KEY))
        .thenReturn(
            Optional.of(cachedProcess(Map.of("listUsers", "List users"), Set.of("listUsers"))));
    final var record =
        record(ProcessInstanceIntent.ELEMENT_ACTIVATING, "listUsers", PARENT_INNER_INSTANCE_KEY);

    // when
    final boolean canExport = handler.canExport(record);

    // then
    assertThat(canExport).isTrue();
  }

  @Test
  void shouldNotExportNonAdHocElement() {
    // given
    when(processCache.get(PROCESS_DEFINITION_KEY))
        .thenReturn(
            Optional.of(cachedProcess(Map.of("listUsers", "List users"), Set.of("listUsers"))));
    final var record =
        record(ProcessInstanceIntent.ELEMENT_ACTIVATING, "someTask", PARENT_INNER_INSTANCE_KEY);

    // when
    final boolean canExport = handler.canExport(record);

    // then
    assertThat(canExport).isFalse();
  }

  @Test
  void shouldNotExportNonActivatingIntent() {
    // given
    when(processCache.get(PROCESS_DEFINITION_KEY))
        .thenReturn(
            Optional.of(cachedProcess(Map.of("listUsers", "List users"), Set.of("listUsers"))));
    final var record =
        record(ProcessInstanceIntent.ELEMENT_COMPLETED, "listUsers", PARENT_INNER_INSTANCE_KEY);

    // when
    final boolean canExport = handler.canExport(record);

    // then
    assertThat(canExport).isFalse();
  }

  @Test
  void shouldNotExportWhenProcessNotInCache() {
    // given
    when(processCache.get(PROCESS_DEFINITION_KEY)).thenReturn(Optional.empty());
    final var record =
        record(ProcessInstanceIntent.ELEMENT_ACTIVATING, "listUsers", PARENT_INNER_INSTANCE_KEY);

    // when
    final boolean canExport = handler.canExport(record);

    // then
    assertThat(canExport).isFalse();
  }

  @Test
  void shouldWriteEntryNameOntoParentInnerInstanceRow() {
    // given
    when(processCache.get(PROCESS_DEFINITION_KEY))
        .thenReturn(
            Optional.of(cachedProcess(Map.of("listUsers", "List users"), Set.of("listUsers"))));
    final var record =
        record(ProcessInstanceIntent.ELEMENT_ACTIVATING, "listUsers", PARENT_INNER_INSTANCE_KEY);

    // when
    handler.export(record);

    // then
    // the resolved name must target the PARENT inner-instance row (flowScopeKey), not the
    // activating child element's own key
    verify(writer).updateName(PARENT_INNER_INSTANCE_KEY, "List users");
  }

  @Test
  void shouldFallbackToElementIdWhenEntryUnnamed() {
    // given
    // the entry element is an ad-hoc activity but carries no name in the cached model. The map is
    // not empty but holds another element's name, so a lookup under the wrong key would surface
    // "Some other name" instead of silently falling back.
    when(processCache.get(PROCESS_DEFINITION_KEY))
        .thenReturn(
            Optional.of(
                cachedProcess(Map.of("someOtherElement", "Some other name"), Set.of("listUsers"))));
    final var record =
        record(ProcessInstanceIntent.ELEMENT_ACTIVATING, "listUsers", PARENT_INNER_INSTANCE_KEY);

    // when
    handler.export(record);

    // then
    // unnamed entry element falls back to its element id
    verify(writer).updateName(PARENT_INNER_INSTANCE_KEY, "listUsers");
  }

  private static CachedProcessEntity cachedProcess(
      final Map<String, String> flowNodesMap, final Set<String> adHocActivityIds) {
    return new CachedProcessEntity(
        "process name", 1, null, List.of(), flowNodesMap, false, Map.of(), adHocActivityIds);
  }

  @SuppressWarnings("unchecked")
  private static Record<ProcessInstanceRecordValue> record(
      final ProcessInstanceIntent intent, final String elementId, final long flowScopeKey) {
    final var value = mock(ProcessInstanceRecordValue.class);
    when(value.getProcessDefinitionKey()).thenReturn(PROCESS_DEFINITION_KEY);
    when(value.getElementId()).thenReturn(elementId);
    when(value.getFlowScopeKey()).thenReturn(flowScopeKey);
    final var record = mock(Record.class);
    when(record.getValue()).thenReturn(value);
    when(record.getIntent()).thenReturn(intent);
    return record;
  }
}
