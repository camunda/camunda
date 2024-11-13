/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate.TREE_PATH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.exporter.cache.TestProcessCache;
import io.camunda.exporter.cache.process.CachedProcessEntity;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.entities.operate.listview.ProcessInstanceForListViewEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableIncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableIncidentRecordValue.Builder;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class ListViewProcessInstanceFromIncidentHandlerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = "test-list-view";

  private final TestProcessCache processCache = new TestProcessCache();
  private final ListViewProcessInstanceFromIncidentHandler underTest =
      new ListViewProcessInstanceFromIncidentHandler(indexName, processCache);

  @Test
  public void testGetHandledValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.INCIDENT);
  }

  @Test
  public void testGetEntityType() {
    assertThat(underTest.getEntityType()).isEqualTo(ProcessInstanceForListViewEntity.class);
  }

  @Test
  void shouldHandleRecord() {
    // given
    final Record<IncidentRecordValue> incidentRecord =
        factory.generateRecord(ValueType.INCIDENT, r -> r.withIntent(IncidentIntent.CREATED));

    // when - then
    assertThat(underTest.handlesRecord(incidentRecord)).isTrue();
  }

  @Test
  void shouldNotHandleRecord() {
    // given
    final Record<IncidentRecordValue> incidentRecord =
        factory.generateRecord(ValueType.INCIDENT, r -> r.withIntent(IncidentIntent.RESOLVED));

    // when - then
    assertThat(underTest.handlesRecord(incidentRecord)).isFalse();
  }

  @Test
  public void shouldGenerateIds() {
    // given
    final long processDefinitionKey1 = 999L;
    final Long pi1Key = 111L;
    final long callActivity1Key = 123L;
    final long processDefinitionKey2 = 888L;
    final Long pi2Key = 222L;
    final long callActivity2Key = 234L;
    final Long pi3Key = 333L;
    final long flowNodeInstanceKey = 345L;
    final Record<IncidentRecordValue> incidentRecord =
        createIncidentRecord(
            List.of(
                List.of(pi1Key, callActivity1Key),
                List.of(pi2Key, callActivity2Key),
                List.of(pi3Key, flowNodeInstanceKey)),
            List.of(0, 0),
            List.of(processDefinitionKey1, processDefinitionKey2, 777L));
    // when
    final var idList = underTest.generateIds(incidentRecord);
    // then
    assertThat(idList)
        .containsExactly(String.valueOf(pi1Key), String.valueOf(pi2Key), String.valueOf(pi3Key));
  }

  private Record<IncidentRecordValue> createIncidentRecord(
      final List<List<Long>> elementInstancePath,
      final List<Integer> callingElementPath,
      final List<Long> processDefinitionPath) {
    final Builder builder =
        ImmutableIncidentRecordValue.builder().withElementInstancePath(elementInstancePath);
    if (callingElementPath != null) {
      builder.withCallingElementPath(callingElementPath);
    }
    if (processDefinitionPath != null) {
      builder.withProcessDefinitionPath(processDefinitionPath);
    }
    final IncidentRecordValue incidentValue = builder.build();
    final Record<IncidentRecordValue> incidentRecord =
        factory.generateRecord(ValueType.INCIDENT, r -> r.withValue(incidentValue));
    return incidentRecord;
  }

  @Test
  public void shouldCreateNewEntity() {
    final var result = underTest.createNewEntity("id");
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo("id");
  }

  @Test
  public void shouldUpsertEntityOnFlush() {
    // given
    final ProcessInstanceForListViewEntity inputEntity =
        new ProcessInstanceForListViewEntity()
            .setId("111")
            .setTreePath("PI_111/FN_3/FNI_456677/PI_23432");

    final BatchRequest mockRequest = mock(BatchRequest.class);

    final Map<String, Object> expectedUpdateFields = new LinkedHashMap<>();
    expectedUpdateFields.put(TREE_PATH, inputEntity.getTreePath());
    // when
    underTest.flush(inputEntity, mockRequest);
    // then
    verify(mockRequest, times(1))
        .upsert(indexName, inputEntity.getId(), inputEntity, expectedUpdateFields);
  }

  @Test
  public void shouldUpdateEntityFromRecordWithCallActivity() {
    // given
    final long processDefinitionKey1 = 999L;
    final Long pi1Key = 111L;
    final Integer callActivityIndex1 = 3;
    final String callActivityId1 = "callActivity1";
    final long callActivity1Key = 123L;
    final long processDefinitionKey2 = 888L;
    final Long pi2Key = 222L;
    final Integer callActivityIndex2 = 1;
    final String callActivityId2 = "callActivity2";
    final long callActivity2Key = 234L;
    final Long pi3Key = 333L;
    final long flowNodeInstanceKey = 345L;
    final Record<IncidentRecordValue> incidentRecord =
        createIncidentRecord(
            List.of(
                List.of(pi1Key, callActivity1Key),
                List.of(pi2Key, callActivity2Key),
                List.of(pi3Key, flowNodeInstanceKey)),
            List.of(callActivityIndex1, callActivityIndex2),
            List.of(processDefinitionKey1, processDefinitionKey2, 777L));
    processCache.put(
        processDefinitionKey1,
        new CachedProcessEntity(null, null, List.of("0", "1", "2", callActivityId1)));

    processCache.put(
        processDefinitionKey2, new CachedProcessEntity(null, null, List.of("0", callActivityId2)));

    // when parent process instance
    final ProcessInstanceForListViewEntity processInstanceForListViewEntity1 =
        new ProcessInstanceForListViewEntity().setId(String.valueOf(pi1Key));
    underTest.updateEntity(incidentRecord, processInstanceForListViewEntity1);
    // then
    assertThat(processInstanceForListViewEntity1.getTreePath()).isEqualTo("PI_111");

    // when called process 2nd level
    final ProcessInstanceForListViewEntity processInstanceForListViewEntity2 =
        new ProcessInstanceForListViewEntity().setId(String.valueOf(pi2Key));
    underTest.updateEntity(incidentRecord, processInstanceForListViewEntity2);
    // then
    assertThat(processInstanceForListViewEntity2.getTreePath())
        .isEqualTo("PI_111/FN_callActivity1/FNI_123/PI_222");

    // when called process 3rd level
    final ProcessInstanceForListViewEntity processInstanceForListViewEntity3 =
        new ProcessInstanceForListViewEntity().setId(String.valueOf(pi3Key));
    underTest.updateEntity(incidentRecord, processInstanceForListViewEntity3);
    // then
    assertThat(processInstanceForListViewEntity3.getTreePath())
        .isEqualTo("PI_111/FN_callActivity1/FNI_123/PI_222/FN_callActivity2/FNI_234/PI_333");
  }

  @Test
  public void shouldUpdateEntityFromRecordWithoutCallActivity() {
    // given
    final Long pi3Key = 333L;
    final Record<IncidentRecordValue> incidentRecord =
        createIncidentRecord(List.of(List.of(pi3Key, 345L)), List.of(), List.of(777L));

    // when
    final ProcessInstanceForListViewEntity processInstanceForListViewEntity =
        new ProcessInstanceForListViewEntity().setId(String.valueOf(pi3Key));
    underTest.updateEntity(incidentRecord, processInstanceForListViewEntity);
    // then
    assertThat(processInstanceForListViewEntity.getTreePath()).isEqualTo("PI_333");
  }
}
