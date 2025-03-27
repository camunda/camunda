/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor.POSITION;
import static io.camunda.webapps.schema.descriptors.template.IncidentTemplate.BPMN_PROCESS_ID;
import static io.camunda.webapps.schema.descriptors.template.IncidentTemplate.FLOW_NODE_ID;
import static io.camunda.webapps.schema.descriptors.template.IncidentTemplate.PROCESS_DEFINITION_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.exporter.cache.TestProcessCache;
import io.camunda.exporter.cache.process.CachedProcessEntity;
import io.camunda.exporter.notifier.IncidentNotifier;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.exporter.utils.ExporterUtil;
import io.camunda.webapps.schema.entities.incident.IncidentEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.ImmutableIncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class IncidentHandlerTest {
  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = "test-incident";
  private final TestProcessCache processCache = new TestProcessCache();
  private final IncidentNotifier incidentNotifier = mock(IncidentNotifier.class);
  private final IncidentHandler underTest =
      new IncidentHandler(indexName, processCache, incidentNotifier);

  @Test
  void testGetHandledValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.INCIDENT);
  }

  @Test
  void testGetEntityType() {
    assertThat(underTest.getEntityType()).isEqualTo(IncidentEntity.class);
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
  void shouldGenerateIds() {
    // given
    final long expectedId = 123;
    final IncidentRecordValue incidentRecordValue =
        ImmutableIncidentRecordValue.builder()
            .from(factory.generateObject(IncidentRecordValue.class))
            .build();

    final Record<IncidentRecordValue> incidentRecord =
        factory.generateRecord(
            ValueType.INCIDENT,
            r ->
                r.withIntent(ProcessIntent.CREATED)
                    .withValue(incidentRecordValue)
                    .withKey(expectedId));

    // when
    final var idList = underTest.generateIds(incidentRecord);

    // then
    assertThat(idList).containsExactly(String.valueOf(expectedId));
  }

  @Test
  void shouldCreateNewEntity() {
    // when
    final var result = underTest.createNewEntity("id");

    // then
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo("id");
  }

  @Test
  void shouldAddEntityOnFlush() {
    // given
    final long recordKey = 123L;
    final IncidentRecordValue incidentRecordValue =
        ImmutableIncidentRecordValue.builder()
            .from(factory.generateObject(IncidentRecordValue.class))
            .build();

    final Record<IncidentRecordValue> incidentRecord =
        factory.generateRecord(
            ValueType.INCIDENT,
            r ->
                r.withIntent(IncidentIntent.CREATED)
                    .withValue(incidentRecordValue)
                    .withKey(recordKey)
                    .withPartitionId(2)
                    .withPosition(1L)
                    .withTimestamp(System.currentTimeMillis()));

    final IncidentEntity incidentEntity = new IncidentEntity();
    underTest.updateEntity(incidentRecord, incidentEntity);

    final BatchRequest mockRequest = mock(BatchRequest.class);

    // when
    underTest.flush(incidentEntity, mockRequest);

    // then
    verify(mockRequest, times(1))
        .upsert(
            indexName,
            String.valueOf(recordKey),
            incidentEntity,
            Map.of(
                "position",
                1L,
                "flowNodeId",
                incidentRecordValue.getElementId(),
                "bpmnProcessId",
                incidentRecordValue.getBpmnProcessId(),
                "processDefinitionKey",
                incidentRecordValue.getProcessDefinitionKey(),
                "treePath",
                incidentEntity.getTreePath()));
    verify(incidentNotifier).notifyAsync(List.of(incidentEntity));
  }

  @Test
  void shouldUpdateEntityFromRecord() {
    // given
    final long recordKey = 123L;
    final IncidentRecordValue incidentRecordValue =
        ImmutableIncidentRecordValue.builder()
            .from(factory.generateObject(IncidentRecordValue.class))
            .build();

    final Record<IncidentRecordValue> incidentRecord =
        factory.generateRecord(
            ValueType.INCIDENT,
            r ->
                r.withIntent(IncidentIntent.CREATED)
                    .withValue(incidentRecordValue)
                    .withKey(recordKey)
                    .withPartitionId(2)
                    .withPosition(100)
                    .withTimestamp(System.currentTimeMillis()));

    // when
    final IncidentEntity incidentEntity = new IncidentEntity();
    underTest.updateEntity(incidentRecord, incidentEntity);

    // then
    assertEntityFields(incidentEntity, recordKey, incidentRecord, incidentRecordValue);
  }

  @Test
  void shouldUpdateEntityFromFollowUpRecord() {
    // given
    final long recordKey = 123L;
    final IncidentRecordValue incidentRecordValue =
        ImmutableIncidentRecordValue.builder()
            .from(factory.generateObject(IncidentRecordValue.class))
            .build();

    final Record<IncidentRecordValue> incidentRecord =
        factory.generateRecord(
            ValueType.INCIDENT,
            r ->
                r.withIntent(IncidentIntent.CREATED)
                    .withValue(incidentRecordValue)
                    .withKey(recordKey)
                    .withPartitionId(2)
                    .withPosition(100)
                    .withTimestamp(System.currentTimeMillis()));

    final IncidentEntity incidentEntity = new IncidentEntity();
    underTest.updateEntity(incidentRecord, incidentEntity);

    final IncidentRecordValue migratedIncidentRecordValue =
        ImmutableIncidentRecordValue.builder()
            .from(factory.generateObject(IncidentRecordValue.class))
            .build();
    final Record<IncidentRecordValue> migratedIncidentRecord =
        factory.generateRecord(
            ValueType.INCIDENT,
            r ->
                r.withIntent(IncidentIntent.MIGRATED)
                    .withValue(migratedIncidentRecordValue)
                    .withKey(recordKey)
                    .withPartitionId(2)
                    .withPosition(123)
                    .withTimestamp(System.currentTimeMillis()));

    // when
    underTest.updateEntity(migratedIncidentRecord, incidentEntity);

    // then
    assertEntityFields(
        incidentEntity, recordKey, migratedIncidentRecord, migratedIncidentRecordValue);
  }

  @Test
  void shouldTrimIncidentErrorMessage() {
    // given
    final IncidentRecordValue incidentRecordValue =
        ImmutableIncidentRecordValue.builder()
            .from(factory.generateObject(IncidentRecordValue.class))
            .withErrorMessage(
                """
   Message with Whitespaces
   """)
            .build();

    final Record<IncidentRecordValue> incidentRecord =
        factory.generateRecord(
            ValueType.INCIDENT,
            r -> r.withIntent(ProcessIntent.CREATED).withValue(incidentRecordValue));

    // when
    final IncidentEntity incidentEntity = new IncidentEntity();
    underTest.updateEntity(incidentRecord, incidentEntity);

    // then
    assertThat(incidentEntity.getErrorMessage()).isEqualTo("Message with Whitespaces");
  }

  @Test
  void shouldUpdateTreePathForCallActivitiesCase() {
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
    final ImmutableIncidentRecordValue.Builder valueBuilder =
        ImmutableIncidentRecordValue.builder()
            .from(factory.generateObject(IncidentRecordValue.class));
    addCallStackInfo(
        valueBuilder,
        List.of(
            List.of(pi1Key, callActivity1Key),
            List.of(pi2Key, callActivity2Key),
            List.of(pi3Key, flowNodeInstanceKey)),
        List.of(callActivityIndex1, callActivityIndex2),
        List.of(processDefinitionKey1, processDefinitionKey2, 777L),
        "userTask");
    final IncidentRecordValue incidentRecordValue = valueBuilder.build();

    final Record<IncidentRecordValue> incidentRecord =
        factory.generateRecord(
            ValueType.INCIDENT,
            r -> r.withIntent(ProcessIntent.CREATED).withValue(incidentRecordValue));

    processCache.put(
        processDefinitionKey1,
        new CachedProcessEntity(null, null, List.of("0", "1", "2", callActivityId1)));

    processCache.put(
        processDefinitionKey2, new CachedProcessEntity(null, null, List.of("0", callActivityId2)));

    // when
    final IncidentEntity incidentEntity = new IncidentEntity();
    underTest.updateEntity(incidentRecord, incidentEntity);

    // then
    assertThat(incidentEntity.getTreePath())
        .isEqualTo(
            "PI_111/FN_callActivity1/FNI_123/PI_222/FN_callActivity2/FNI_234/PI_333/FN_userTask/FNI_345");
  }

  @Test
  void shouldUpdateTreePathForSimpleCase() {
    // given
    final Long pi3Key = 333L;
    final ImmutableIncidentRecordValue.Builder valueBuilder =
        ImmutableIncidentRecordValue.builder()
            .from(factory.generateObject(IncidentRecordValue.class));
    addCallStackInfo(
        valueBuilder, List.of(List.of(pi3Key, 345L)), List.of(), List.of(777L), "userTask");
    final IncidentRecordValue incidentRecordValue = valueBuilder.build();

    final Record<IncidentRecordValue> incidentRecord =
        factory.generateRecord(
            ValueType.INCIDENT,
            r -> r.withIntent(ProcessIntent.CREATED).withValue(incidentRecordValue));

    // when
    final IncidentEntity incidentEntity = new IncidentEntity();
    underTest.updateEntity(incidentRecord, incidentEntity);

    // then
    assertThat(incidentEntity.getTreePath()).isEqualTo("PI_333/FN_userTask/FNI_345");
  }

  private void addCallStackInfo(
      final ImmutableIncidentRecordValue.Builder builder,
      final List<List<Long>> elementInstancePath,
      final List<Integer> callingElementPath,
      final List<Long> processDefinitionPath,
      final String elementId) {
    builder
        .withProcessInstanceKey(elementInstancePath.getLast().get(0))
        .withElementId(elementId)
        .withElementInstanceKey(elementInstancePath.getLast().get(1))
        .withElementInstancePath(elementInstancePath);
    if (callingElementPath != null) {
      builder.withCallingElementPath(callingElementPath);
    }
    if (processDefinitionPath != null) {
      builder.withProcessDefinitionPath(processDefinitionPath);
    }
  }

  /**
   * Must be imported correctly so that resolving the incident considers incrementing job retries.
   */
  @Test
  void shouldHandleJobNoRetriesErrorType() {
    // given
    final long expectedId = 123;
    final IncidentRecordValue incidentRecordValue =
        ImmutableIncidentRecordValue.builder()
            .from(factory.generateObject(IncidentRecordValue.class))
            .withErrorType(ErrorType.JOB_NO_RETRIES)
            .build();

    final Record<IncidentRecordValue> incidentRecord =
        factory.generateRecord(
            ValueType.INCIDENT,
            r ->
                r.withIntent(ProcessIntent.CREATED)
                    .withValue(incidentRecordValue)
                    .withKey(expectedId));

    final IncidentEntity incidentEntity = new IncidentEntity();

    // when
    underTest.updateEntity(incidentRecord, incidentEntity);

    // then
    assertThat(incidentEntity.getErrorType())
        .isEqualTo(io.camunda.webapps.schema.entities.incident.ErrorType.JOB_NO_RETRIES);
  }

  /**
   * Must be imported correctly so that resolving the incident considers deploying the missing
   * resource.
   */
  @Test
  void shouldHandleResourceNotFoundErrorType() {
    // given
    final long expectedId = 123;
    final IncidentRecordValue incidentRecordValue =
        ImmutableIncidentRecordValue.builder()
            .from(factory.generateObject(IncidentRecordValue.class))
            .withErrorType(ErrorType.RESOURCE_NOT_FOUND)
            .build();

    final Record<IncidentRecordValue> incidentRecord =
        factory.generateRecord(
            ValueType.INCIDENT,
            r ->
                r.withIntent(ProcessIntent.CREATED)
                    .withValue(incidentRecordValue)
                    .withKey(expectedId));

    final IncidentEntity incidentEntity = new IncidentEntity();

    // when
    underTest.updateEntity(incidentRecord, incidentEntity);

    // then
    assertThat(incidentEntity.getErrorType())
        .isEqualTo(io.camunda.webapps.schema.entities.incident.ErrorType.RESOURCE_NOT_FOUND);
  }

  /**
   * Must be imported correctly so that resolving the incident considers incrementing job retries.
   */
  @Test
  void shouldHandleExecutionListenerNoRetriesErrorType() {
    // given
    final long expectedId = 123;
    final IncidentRecordValue incidentRecordValue =
        ImmutableIncidentRecordValue.builder()
            .from(factory.generateObject(IncidentRecordValue.class))
            .withErrorType(ErrorType.EXECUTION_LISTENER_NO_RETRIES)
            .build();

    final Record<IncidentRecordValue> incidentRecord =
        factory.generateRecord(
            ValueType.INCIDENT,
            r ->
                r.withIntent(ProcessIntent.CREATED)
                    .withValue(incidentRecordValue)
                    .withKey(expectedId));

    final IncidentEntity incidentEntity = new IncidentEntity();

    // when
    underTest.updateEntity(incidentRecord, incidentEntity);

    // then
    assertThat(incidentEntity.getErrorType())
        .isEqualTo(
            io.camunda.webapps.schema.entities.incident.ErrorType.EXECUTION_LISTENER_NO_RETRIES);
  }

  @Test
  void shouldHandleTaskListenerNoRetriesErrorType() {
    // given
    final long expectedId = 123;
    final IncidentRecordValue incidentRecordValue =
        ImmutableIncidentRecordValue.builder()
            .from(factory.generateObject(IncidentRecordValue.class))
            .withErrorType(ErrorType.TASK_LISTENER_NO_RETRIES)
            .build();

    final Record<IncidentRecordValue> incidentRecord =
        factory.generateRecord(
            ValueType.INCIDENT,
            r ->
                r.withIntent(ProcessIntent.CREATED)
                    .withValue(incidentRecordValue)
                    .withKey(expectedId));

    final IncidentEntity incidentEntity = new IncidentEntity();

    // when
    underTest.updateEntity(incidentRecord, incidentEntity);

    // then
    assertThat(incidentEntity.getErrorType())
        .isEqualTo(io.camunda.webapps.schema.entities.incident.ErrorType.TASK_LISTENER_NO_RETRIES);
  }

  private String concurrencyScriptMock() {
    return String.format(
        "if (ctx._source.%s == null || ctx._source.%s < params.%s) { "
            + "ctx._source.%s = params.%s; " // position
            + "if (params.%s != null) {"
            + "   ctx._source.%s = params.%s; " // PROCESS_DEFINITION_KEY
            + "   ctx._source.%s = params.%s; " // BPMN_PROCESS_ID
            + "   ctx._source.%s = params.%s; " // FLOW_NODE_ID
            + "}"
            + "}",
        POSITION,
        POSITION,
        POSITION,
        POSITION,
        POSITION,
        PROCESS_DEFINITION_KEY,
        PROCESS_DEFINITION_KEY,
        PROCESS_DEFINITION_KEY,
        BPMN_PROCESS_ID,
        BPMN_PROCESS_ID,
        FLOW_NODE_ID,
        FLOW_NODE_ID);
  }

  private static void assertEntityFields(
      final IncidentEntity incidentEntity,
      final long recordKey,
      final Record<IncidentRecordValue> incidentRecord,
      final IncidentRecordValue incidentRecordValue) {
    assertThat(incidentEntity.getId()).isEqualTo(String.valueOf(recordKey));
    assertThat(incidentEntity.getKey()).isEqualTo(123L);
    assertThat(incidentEntity.getPartitionId()).isEqualTo(incidentRecord.getPartitionId());
    assertThat(incidentEntity.getPosition()).isEqualTo(incidentRecord.getPosition());
    assertThat(incidentEntity.getFlowNodeId()).isEqualTo(incidentRecordValue.getElementId());
    assertThat(incidentEntity.getErrorMessage())
        .isEqualTo(ExporterUtil.trimWhitespace(incidentRecordValue.getErrorMessage()));
    assertThat(incidentEntity.getErrorType().name())
        .isEqualTo(incidentRecordValue.getErrorType().name());
    assertThat(incidentEntity.getBpmnProcessId()).isEqualTo(incidentRecordValue.getBpmnProcessId());
    assertThat(incidentEntity.getProcessDefinitionKey())
        .isEqualTo(incidentRecordValue.getProcessDefinitionKey());
    assertThat(incidentEntity.getJobKey()).isEqualTo(incidentRecordValue.getJobKey());
    assertThat(incidentEntity.getCreationTime())
        .isEqualTo(
            OffsetDateTime.ofInstant(
                Instant.ofEpochMilli(incidentRecord.getTimestamp()), ZoneOffset.UTC));
    assertThat(incidentEntity.getErrorMessage())
        .isEqualTo(ExporterUtil.trimWhitespace(incidentRecordValue.getErrorMessage()));
    assertThat(incidentEntity.getFlowNodeInstanceKey())
        .isEqualTo(incidentRecordValue.getElementInstanceKey());
    assertThat(incidentEntity.getErrorMessageHash())
        .isEqualTo(incidentRecordValue.getErrorMessage().hashCode());
  }
}
