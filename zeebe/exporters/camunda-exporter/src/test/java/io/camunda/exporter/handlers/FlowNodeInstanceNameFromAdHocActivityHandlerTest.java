/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.exporter.cache.TestProcessCache;
import io.camunda.exporter.index.TargetIndex;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.descriptors.template.FlowNodeInstanceTemplate;
import io.camunda.webapps.schema.entities.flownode.FlowNodeInstanceEntity;
import io.camunda.zeebe.exporter.common.cache.process.CachedProcessEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class FlowNodeInstanceNameFromAdHocActivityHandlerTest {
  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = "test-flow-node-instance";
  private final TestProcessCache processCache = new TestProcessCache();
  private final FlowNodeInstanceNameFromAdHocActivityHandler underTest =
      new FlowNodeInstanceNameFromAdHocActivityHandler(indexName, processCache);

  @Test
  public void shouldHandleActivatingAdHocActivity() {
    // given
    processCache.put(
        222L, cachedProcessEntity(Map.of("listUsers", "List users"), Set.of("listUsers")));
    final Record<ProcessInstanceRecordValue> record =
        createRecord(ProcessInstanceIntent.ELEMENT_ACTIVATING, "listUsers", 222L, 0L);

    // when - then
    assertThat(underTest.handlesRecord(record)).isTrue();
  }

  @Test
  public void shouldNotHandleNonAdHocActivity() {
    // given
    processCache.put(
        222L, cachedProcessEntity(Map.of("listUsers", "List users"), Set.of("listUsers")));
    final Record<ProcessInstanceRecordValue> record =
        createRecord(ProcessInstanceIntent.ELEMENT_ACTIVATING, "someTask", 222L, 0L);

    // when - then
    assertThat(underTest.handlesRecord(record)).isFalse();
  }

  @Test
  public void shouldNotHandleNonActivatingIntent() {
    // given
    processCache.put(
        222L, cachedProcessEntity(Map.of("listUsers", "List users"), Set.of("listUsers")));
    final Record<ProcessInstanceRecordValue> record =
        createRecord(ProcessInstanceIntent.ELEMENT_COMPLETED, "listUsers", 222L, 0L);

    // when - then
    assertThat(underTest.handlesRecord(record)).isFalse();
  }

  @Test
  public void shouldNotHandleWhenProcessNotInCache() {
    // given - no entry in the process cache for this process definition key
    final Record<ProcessInstanceRecordValue> record =
        createRecord(ProcessInstanceIntent.ELEMENT_ACTIVATING, "listUsers", 222L, 0L);

    // when - then
    // a cache miss must not be treated as a match (and must not NPE resolving adHocActivityIds)
    assertThat(underTest.handlesRecord(record)).isFalse();
  }

  @Test
  public void shouldGenerateInnerInstanceIdFromFlowScopeKey() {
    // given
    final Record<ProcessInstanceRecordValue> record =
        createRecord(ProcessInstanceIntent.ELEMENT_ACTIVATING, "listUsers", 222L, 999L);

    // when
    final var idList = underTest.generateIds(record);

    // then
    assertThat(idList).containsExactly("999");
  }

  @Test
  public void shouldResolveEntryElementName() {
    // given
    processCache.put(
        222L, cachedProcessEntity(Map.of("listUsers", "List users"), Set.of("listUsers")));
    final Record<ProcessInstanceRecordValue> record =
        createRecord(ProcessInstanceIntent.ELEMENT_ACTIVATING, "listUsers", 222L, 999L);

    // when
    // the entity is created by the exporter framework with the id from generateIds (the parent
    // inner-instance key); updateEntity must only enrich it, never re-key it to the child.
    final FlowNodeInstanceEntity entity = new FlowNodeInstanceEntity().setId("999");
    underTest.updateEntity(record, entity);

    // then
    assertThat(entity.getFlowNodeName()).isEqualTo("List users");
    assertThat(entity.getId())
        .describedAs(
            "updateEntity must keep the parent inner-instance id from generateIds; re-keying it to "
                + "the child would make flush write the name onto the child doc instead")
        .isEqualTo("999");
  }

  @Test
  public void shouldFallbackToElementIdWhenEntryUnnamed() {
    // given
    final Map<String, String> flowNodesMap = new HashMap<>();
    flowNodesMap.put("listUsers", null);
    processCache.put(222L, cachedProcessEntity(flowNodesMap, Set.of("listUsers")));
    final Record<ProcessInstanceRecordValue> record =
        createRecord(ProcessInstanceIntent.ELEMENT_ACTIVATING, "listUsers", 222L, 999L);

    // when
    final FlowNodeInstanceEntity entity = new FlowNodeInstanceEntity();
    underTest.updateEntity(record, entity);

    // then
    assertThat(entity.getFlowNodeName()).isEqualTo("listUsers");
  }

  @Test
  public void shouldUpsertNameWithSetIfNullScriptOnFlush() {
    // given
    final FlowNodeInstanceEntity entity =
        new FlowNodeInstanceEntity().setId("111").setFlowNodeName("List users");
    final TargetIndex index = TargetIndex.mainIndex("test-index");
    final BatchRequest mockRequest = mock(BatchRequest.class);

    // when
    underTest.flush(index, entity, mockRequest);

    // then
    verify(mockRequest, times(1))
        .upsertWithScript(
            eq(index),
            eq("111"),
            eq(entity),
            eq(FlowNodeInstanceNameFromAdHocActivityHandler.SET_IF_NULL_NAME_SCRIPT),
            eq(Map.of(FlowNodeInstanceTemplate.FLOW_NODE_NAME, "List users")));
  }

  private CachedProcessEntity cachedProcessEntity(
      final Map<String, String> flowNodesMap, final Set<String> adHocActivityIds) {
    return new CachedProcessEntity(
        "process", 1, null, List.of(), flowNodesMap, false, Map.of(), adHocActivityIds);
  }

  private Record<ProcessInstanceRecordValue> createRecord(
      final ProcessInstanceIntent intent,
      final String elementId,
      final long processDefinitionKey,
      final long flowScopeKey) {
    final ProcessInstanceRecordValue value =
        ImmutableProcessInstanceRecordValue.builder()
            .from(factory.generateObject(ProcessInstanceRecordValue.class))
            .withElementId(elementId)
            .withProcessDefinitionKey(processDefinitionKey)
            .withFlowScopeKey(flowScopeKey)
            .build();
    return factory.generateRecord(
        ValueType.PROCESS_INSTANCE, r -> r.withIntent(intent).withValue(value));
  }
}
