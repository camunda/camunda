/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static io.camunda.webapps.schema.descriptors.template.AgentInstanceTemplate.AGENT_DEFINITION_KEY;
import static io.camunda.webapps.schema.descriptors.template.AgentInstanceTemplate.BPMN_PROCESS_ID;
import static io.camunda.webapps.schema.descriptors.template.AgentInstanceTemplate.COMPLETION_DATE;
import static io.camunda.webapps.schema.descriptors.template.AgentInstanceTemplate.ELEMENT_ID;
import static io.camunda.webapps.schema.descriptors.template.AgentInstanceTemplate.ELEMENT_INSTANCE_KEYS;
import static io.camunda.webapps.schema.descriptors.template.AgentInstanceTemplate.INPUT_TOKENS;
import static io.camunda.webapps.schema.descriptors.template.AgentInstanceTemplate.LAST_UPDATED_DATE;
import static io.camunda.webapps.schema.descriptors.template.AgentInstanceTemplate.MAX_MODEL_CALLS;
import static io.camunda.webapps.schema.descriptors.template.AgentInstanceTemplate.MAX_TOKENS;
import static io.camunda.webapps.schema.descriptors.template.AgentInstanceTemplate.MAX_TOOL_CALLS;
import static io.camunda.webapps.schema.descriptors.template.AgentInstanceTemplate.MODEL;
import static io.camunda.webapps.schema.descriptors.template.AgentInstanceTemplate.MODEL_CALLS;
import static io.camunda.webapps.schema.descriptors.template.AgentInstanceTemplate.OUTPUT_TOKENS;
import static io.camunda.webapps.schema.descriptors.template.AgentInstanceTemplate.PROCESS_DEFINITION_KEY;
import static io.camunda.webapps.schema.descriptors.template.AgentInstanceTemplate.PROCESS_DEFINITION_VERSION;
import static io.camunda.webapps.schema.descriptors.template.AgentInstanceTemplate.PROCESS_DEFINITION_VERSION_TAG;
import static io.camunda.webapps.schema.descriptors.template.AgentInstanceTemplate.PROVIDER;
import static io.camunda.webapps.schema.descriptors.template.AgentInstanceTemplate.STATUS;
import static io.camunda.webapps.schema.descriptors.template.AgentInstanceTemplate.SYSTEM_PROMPT;
import static io.camunda.webapps.schema.descriptors.template.AgentInstanceTemplate.TOOLS;
import static io.camunda.webapps.schema.descriptors.template.AgentInstanceTemplate.TOOL_CALLS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.exporter.handlers.ExportHandler.IdAndIndex;
import io.camunda.exporter.index.TargetIndex;
import io.camunda.exporter.index.TargetIndexLocator;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.descriptors.template.AgentInstanceTemplate;
import io.camunda.webapps.schema.entities.agenthistory.AgentHistoryContentType;
import io.camunda.webapps.schema.entities.agenthistory.AgentHistoryContentValue;
import io.camunda.webapps.schema.entities.agentinstance.AgentInstanceEntity;
import io.camunda.webapps.schema.entities.agentinstance.AgentInstanceEntity.AgentInstanceToolValue;
import io.camunda.webapps.schema.entities.agentinstance.AgentInstanceStatus;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AgentInstanceIntent;
import io.camunda.zeebe.protocol.record.value.AgentInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableAgentHistoryMessageContentValue;
import io.camunda.zeebe.protocol.record.value.ImmutableAgentInstanceDefinitionValue;
import io.camunda.zeebe.protocol.record.value.ImmutableAgentInstanceLimitsValue;
import io.camunda.zeebe.protocol.record.value.ImmutableAgentInstanceMetricsValue;
import io.camunda.zeebe.protocol.record.value.ImmutableAgentInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableAgentInstanceToolValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import io.camunda.zeebe.util.DateUtil;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;
import org.mockito.Mockito;

final class AgentInstanceHandlerTest {
  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = AgentInstanceTemplate.INDEX_NAME;

  private final AgentInstanceHandler underTest = new AgentInstanceHandler(indexName);

  @Test
  void shouldReturnCorrectHandlerMetadata() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.AGENT_INSTANCE);
    assertThat(underTest.getEntityType()).isEqualTo(AgentInstanceEntity.class);
    assertThat(underTest.getIndexName()).isEqualTo(indexName);
  }

  @ParameterizedTest(name = "[{index}] Should handle \''{0}\'' record")
  @EnumSource(
      value = AgentInstanceIntent.class,
      names = {"CREATED", "UPDATED", "COMPLETED", "MIGRATED"},
      mode = Mode.INCLUDE)
  void shouldHandleRecord(final AgentInstanceIntent intent) {
    assertThat(underTest.handlesRecord(generateRecord(intent))).isTrue();
  }

  @ParameterizedTest(name = "[{index}] Should not handle \''{0}\'' record")
  @EnumSource(
      value = AgentInstanceIntent.class,
      names = {"CREATED", "UPDATED", "COMPLETED", "MIGRATED"},
      mode = Mode.EXCLUDE)
  void shouldNotHandleRecord(final AgentInstanceIntent intent) {
    assertThat(underTest.handlesRecord(generateRecord(intent))).isFalse();
  }

  @Test
  void shouldExtractIdAndIndexes() {
    // given
    final TargetIndexLocator indexLocator = mock(TargetIndexLocator.class);
    final TargetIndex index = TargetIndex.mainIndex(indexName);
    when(indexLocator.locateOrdinalIndex(eq(indexName), any())).thenReturn(index);
    final Record<AgentInstanceRecordValue> record =
        factory.generateRecord(ValueType.AGENT_INSTANCE);

    // when - then
    assertThat(underTest.extractIdAndIndexes(indexLocator, record))
        .containsExactly(new IdAndIndex(String.valueOf(record.getKey()), index));
  }

  @Test
  void shouldUpdateEntityOnCreated() {
    // given
    final long recordKey = 100L;
    final int partitionId = 1;
    final long elementInstanceKey = 200L;
    final String elementId = "elementId";
    final long processInstanceKey = 300L;
    final long rootProcessInstanceKey = 250L;
    final String bpmnProcessId = "myProcess";
    final long processDefinitionKey = 400L;
    final long agentDefinitionKey = 401L;
    final int processDefinitionVersion = 2;
    final String processDefinitionVersionTag = "v2";
    final String tenantId = "<default>";
    final String model = "gpt-4o";
    final String provider = "openai";
    final String systemPrompt = "You are a helpful assistant.";
    final var systemPromptBlock =
        ImmutableAgentHistoryMessageContentValue.builder()
            .withContentType(io.camunda.zeebe.protocol.record.value.AgentHistoryContentType.TEXT)
            .withText(systemPrompt)
            .build();
    final long maxTokens = 1000L;
    final int maxModelCalls = 10;
    final int maxToolCalls = 5;
    final long inputTokens = 50L;
    final long outputTokens = 30L;
    final int modelCalls = 2;
    final int toolCalls = 1;

    final var toolValue =
        ImmutableAgentInstanceToolValue.builder()
            .withName("search")
            .withDescription("Search the web")
            .withElementId("searchElement")
            .build();

    final var recordValue =
        ImmutableAgentInstanceRecordValue.builder()
            .withAgentInstanceKey(recordKey)
            .withElementInstanceKey(elementInstanceKey)
            .withElementId(elementId)
            .withProcessInstanceKey(processInstanceKey)
            .withRootProcessInstanceKey(rootProcessInstanceKey)
            .withBpmnProcessId(bpmnProcessId)
            .withProcessDefinitionKey(processDefinitionKey)
            .withAgentDefinitionKey(agentDefinitionKey)
            .withProcessDefinitionVersion(processDefinitionVersion)
            .withProcessDefinitionVersionTag(processDefinitionVersionTag)
            .withTenantId(tenantId)
            .withStatus(io.camunda.zeebe.protocol.record.value.AgentInstanceStatus.INITIALIZING)
            .withDefinition(
                ImmutableAgentInstanceDefinitionValue.builder()
                    .withModel(model)
                    .withProvider(provider)
                    .withSystemPrompt(List.of(systemPromptBlock))
                    .build())
            .withLimits(
                ImmutableAgentInstanceLimitsValue.builder()
                    .withMaxTokens(maxTokens)
                    .withMaxModelCalls(maxModelCalls)
                    .withMaxToolCalls(maxToolCalls)
                    .build())
            .withMetrics(
                ImmutableAgentInstanceMetricsValue.builder()
                    .withInputTokens(inputTokens)
                    .withOutputTokens(outputTokens)
                    .withModelCalls(modelCalls)
                    .withToolCalls(toolCalls)
                    .build())
            .withTools(List.of(toolValue))
            .withElementInstanceKeys(List.of(elementInstanceKey))
            .build();

    final Record<AgentInstanceRecordValue> record =
        factory.generateRecord(
            ValueType.AGENT_INSTANCE,
            r ->
                r.withIntent(AgentInstanceIntent.CREATED)
                    .withKey(recordKey)
                    .withPartitionId(partitionId)
                    .withValue(recordValue));

    final var entity = new AgentInstanceEntity().setId(String.valueOf(recordKey));

    // when
    underTest.updateEntity(record, entity);

    // then
    final var expectedTimestamp =
        DateUtil.toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp()));

    assertThat(entity.getKey()).isEqualTo(recordKey);
    assertThat(entity.getPartitionId()).isEqualTo(partitionId);
    assertThat(entity.getElementId()).isEqualTo(elementId);
    assertThat(entity.getProcessInstanceKey()).isEqualTo(processInstanceKey);
    assertThat(entity.getRootProcessInstanceKey()).isEqualTo(rootProcessInstanceKey);
    assertThat(entity.getBpmnProcessId()).isEqualTo(bpmnProcessId);
    assertThat(entity.getProcessDefinitionKey()).isEqualTo(processDefinitionKey);
    assertThat(entity.getAgentDefinitionKey()).isEqualTo(agentDefinitionKey);
    assertThat(entity.getProcessDefinitionVersion()).isEqualTo(processDefinitionVersion);
    assertThat(entity.getProcessDefinitionVersionTag()).isEqualTo(processDefinitionVersionTag);
    assertThat(entity.getTenantId()).isEqualTo(tenantId);
    assertThat(entity.getStatus()).isEqualTo(AgentInstanceStatus.INITIALIZING);
    assertThat(entity.getModel()).isEqualTo(model);
    assertThat(entity.getProvider()).isEqualTo(provider);
    assertThat(entity.getSystemPrompt())
        .containsExactly(
            new AgentHistoryContentValue(AgentHistoryContentType.TEXT, systemPrompt, null, null));
    assertThat(entity.getMaxTokens()).isEqualTo(maxTokens);
    assertThat(entity.getMaxModelCalls()).isEqualTo(maxModelCalls);
    assertThat(entity.getMaxToolCalls()).isEqualTo(maxToolCalls);
    assertThat(entity.getInputTokens()).isEqualTo(inputTokens);
    assertThat(entity.getOutputTokens()).isEqualTo(outputTokens);
    assertThat(entity.getModelCalls()).isEqualTo(modelCalls);
    assertThat(entity.getToolCalls()).isEqualTo(toolCalls);
    assertThat(entity.getTools())
        .containsExactly(new AgentInstanceToolValue("search", "Search the web", "searchElement"));
    assertThat(entity.getElementInstanceKeys())
        .containsExactlyElementsOf(recordValue.getElementInstanceKeys());
    assertThat(entity.getCreationDate()).isEqualTo(expectedTimestamp);
    assertThat(entity.getLastUpdatedDate()).isEqualTo(expectedTimestamp);
    assertThat(entity.getCompletionDate()).isNull();
  }

  @Test
  void shouldReResolveAgentDefinitionKeyOnMigrated() {
    // given — an entity created with an initial agent definition key
    final var entity = new AgentInstanceEntity().setId("1");
    final Record<AgentInstanceRecordValue> createdRecord =
        factory.generateRecord(
            ValueType.AGENT_INSTANCE,
            r ->
                r.withIntent(AgentInstanceIntent.CREATED)
                    .withValue(
                        ImmutableAgentInstanceRecordValue.builder()
                            .from(buildMinimalRecordValue(1L))
                            .withAgentDefinitionKey(700L)
                            .build()));
    underTest.updateEntity(createdRecord, entity);
    assertThat(entity.getAgentDefinitionKey()).isEqualTo(700L);

    // when — a MIGRATED record carries a re-resolved agent definition key
    final Record<AgentInstanceRecordValue> migratedRecord =
        factory.generateRecord(
            ValueType.AGENT_INSTANCE,
            r ->
                r.withIntent(AgentInstanceIntent.MIGRATED)
                    .withValue(
                        ImmutableAgentInstanceRecordValue.builder()
                            .from(buildMinimalRecordValue(1L))
                            .withAgentDefinitionKey(800L)
                            .build()));
    underTest.updateEntity(migratedRecord, entity);

    // then — the entity reflects the re-resolved key
    assertThat(entity.getAgentDefinitionKey()).isEqualTo(800L);
  }

  @Test
  void shouldMapMultipleElementInstanceKeys() {
    // given — agent instance associated with two element instances
    final long firstElementInstanceKey = 200L;
    final long secondElementInstanceKey = 201L;
    final var recordValue =
        ImmutableAgentInstanceRecordValue.builder()
            .from(buildMinimalRecordValue(100L))
            .withElementInstanceKey(secondElementInstanceKey)
            .withElementInstanceKeys(List.of(firstElementInstanceKey, secondElementInstanceKey))
            .build();

    final Record<AgentInstanceRecordValue> record =
        factory.generateRecord(
            ValueType.AGENT_INSTANCE,
            r -> r.withIntent(AgentInstanceIntent.UPDATED).withValue(recordValue));

    final var entity = new AgentInstanceEntity().setId("100");

    // when
    underTest.updateEntity(record, entity);

    // then — all element instance keys are propagated to the entity
    assertThat(entity.getElementInstanceKeys())
        .containsExactly(firstElementInstanceKey, secondElementInstanceKey);
  }

  @Test
  void shouldPreserveCreationDateAcrossSubsequentIntents() {
    // given — CREATED sets the creation date on the entity. Timestamps are pinned explicitly and
    // strictly increasing: the factory randomizes record.getTimestamp() independently per call, so
    // leaving it random makes the "lastUpdatedDate is after creationDate" assertion below flaky —
    // there's no guarantee an independently-random later record has a larger random timestamp.
    final var entity = new AgentInstanceEntity().setId("1");
    final Record<AgentInstanceRecordValue> createdRecord =
        factory.generateRecord(
            ValueType.AGENT_INSTANCE,
            r ->
                r.withIntent(AgentInstanceIntent.CREATED)
                    .withValue(buildMinimalRecordValue(1L))
                    .withTimestamp(1_000L));
    underTest.updateEntity(createdRecord, entity);
    final var creationDate = entity.getCreationDate();
    assertThat(creationDate).isNotNull();

    // when — UPDATED applied to the same entity
    final Record<AgentInstanceRecordValue> updatedRecord =
        factory.generateRecord(
            ValueType.AGENT_INSTANCE,
            r ->
                r.withIntent(AgentInstanceIntent.UPDATED)
                    .withValue(buildMinimalRecordValue(1L))
                    .withTimestamp(2_000L));
    underTest.updateEntity(updatedRecord, entity);

    // then — creation date preserved, last updated advanced, completion still absent
    assertThat(entity.getCreationDate()).isEqualTo(creationDate);
    assertThat(entity.getLastUpdatedDate()).isAfterOrEqualTo(creationDate);
    assertThat(entity.getCompletionDate()).isNull();

    // when — COMPLETED applied to the same entity
    final var completedValue =
        ImmutableAgentInstanceRecordValue.builder()
            .from(buildMinimalRecordValue(1L))
            .withStatus(io.camunda.zeebe.protocol.record.value.AgentInstanceStatus.COMPLETED)
            .build();
    final Record<AgentInstanceRecordValue> completedRecord =
        factory.generateRecord(
            ValueType.AGENT_INSTANCE,
            r ->
                r.withIntent(AgentInstanceIntent.COMPLETED)
                    .withValue(completedValue)
                    .withTimestamp(3_000L));
    underTest.updateEntity(completedRecord, entity);

    // then — creation date still untouched, completion date now set
    assertThat(entity.getCreationDate()).isEqualTo(creationDate);
    assertThat(entity.getCompletionDate()).isNotNull();
    assertThat(entity.getStatus()).isEqualTo(AgentInstanceStatus.COMPLETED);
  }

  @ParameterizedTest(name = "[{index}] Should map protocol status \''{0}\' to entity status")
  @EnumSource(
      value = io.camunda.zeebe.protocol.record.value.AgentInstanceStatus.class,
      // The broker should never emit UNSPECIFIED;
      // all other protocol statuses must map to an exporter status.
      names = {"UNSPECIFIED"},
      mode = Mode.EXCLUDE)
  void shouldMapAllProtocolStatuses(
      final io.camunda.zeebe.protocol.record.value.AgentInstanceStatus protocolStatus) {
    // given
    final var recordValue =
        ImmutableAgentInstanceRecordValue.builder()
            .from(buildMinimalRecordValue(1L))
            .withStatus(protocolStatus)
            .build();
    final Record<AgentInstanceRecordValue> record =
        factory.generateRecord(
            ValueType.AGENT_INSTANCE,
            r -> r.withIntent(AgentInstanceIntent.CREATED).withValue(recordValue));
    final var entity = new AgentInstanceEntity().setId("1");

    // when
    underTest.updateEntity(record, entity);

    // then
    assertThat(entity.getStatus()).isNotNull();
    assertThat(entity.getStatus().name())
        .as(
            """
            Protocol status '%s' has no explicit mapping in 'AgentInstanceHandler.mapStatus()' \
            and falls back to 'UNKNOWN' — add '%s' to '%s' entity enum and handle \
            it explicitly in the switch, or exclude it from this test if UNKNOWN is intentional.\
            """,
            protocolStatus, protocolStatus, AgentInstanceStatus.class.getName())
        .isEqualTo(protocolStatus.name());
  }

  @Test
  void shouldMapUnexpectedStatusToUnknown() {
    // given — UNSPECIFIED is not explicitly handled in mapStatus() and acts as a proxy
    // for any unexpected/future protocol status that hits the default branch
    final var recordValue =
        ImmutableAgentInstanceRecordValue.builder()
            .from(buildMinimalRecordValue(1L))
            .withStatus(io.camunda.zeebe.protocol.record.value.AgentInstanceStatus.UNSPECIFIED)
            .build();
    final Record<AgentInstanceRecordValue> record =
        factory.generateRecord(
            ValueType.AGENT_INSTANCE,
            r -> r.withIntent(AgentInstanceIntent.CREATED).withValue(recordValue));
    final var entity = new AgentInstanceEntity().setId("1");

    // when
    underTest.updateEntity(record, entity);

    // then
    assertThat(entity.getStatus()).isEqualTo(AgentInstanceStatus.UNKNOWN);
  }

  @Test
  void shouldFlushUpdateFieldsWithValuesPopulatedByUpdateEntity() {
    // given — entity populated via updateEntity, not hand-constructed, to catch field-name drift
    final var recordValue =
        ImmutableAgentInstanceRecordValue.builder()
            .from(buildMinimalRecordValue(1L))
            .withStatus(io.camunda.zeebe.protocol.record.value.AgentInstanceStatus.THINKING)
            .withMetrics(
                ImmutableAgentInstanceMetricsValue.builder()
                    .withInputTokens(42L)
                    .withOutputTokens(17L)
                    .withModelCalls(3)
                    .withToolCalls(1)
                    .build())
            .build();
    final Record<AgentInstanceRecordValue> record =
        factory.generateRecord(
            ValueType.AGENT_INSTANCE,
            r -> r.withIntent(AgentInstanceIntent.UPDATED).withKey(1L).withValue(recordValue));
    final var entity = new AgentInstanceEntity().setId("1");
    underTest.updateEntity(record, entity);

    final TargetIndex index = TargetIndex.mainIndex("test-index");
    final BatchRequest mockRequest = Mockito.mock(BatchRequest.class);

    // when
    underTest.flush(index, entity, mockRequest);

    // then — updateFields keys match template constants; values come from entity (not hardcoded)
    final var expectedUpdateFields = new LinkedHashMap<String, Object>();
    expectedUpdateFields.put(STATUS, entity.getStatus());
    expectedUpdateFields.put(MODEL, entity.getModel());
    expectedUpdateFields.put(PROVIDER, entity.getProvider());
    expectedUpdateFields.put(SYSTEM_PROMPT, entity.getSystemPrompt());
    expectedUpdateFields.put(MAX_TOKENS, entity.getMaxTokens());
    expectedUpdateFields.put(MAX_MODEL_CALLS, entity.getMaxModelCalls());
    expectedUpdateFields.put(MAX_TOOL_CALLS, entity.getMaxToolCalls());
    expectedUpdateFields.put(INPUT_TOKENS, entity.getInputTokens());
    expectedUpdateFields.put(OUTPUT_TOKENS, entity.getOutputTokens());
    expectedUpdateFields.put(MODEL_CALLS, entity.getModelCalls());
    expectedUpdateFields.put(TOOL_CALLS, entity.getToolCalls());
    expectedUpdateFields.put(TOOLS, entity.getTools());
    expectedUpdateFields.put(ELEMENT_INSTANCE_KEYS, entity.getElementInstanceKeys());
    expectedUpdateFields.put(LAST_UPDATED_DATE, entity.getLastUpdatedDate());
    // completionDate is null on UPDATED intent but still written so the upsert script
    // payload is identical across intents (no-op when null overwrites null in the index).
    expectedUpdateFields.put(COMPLETION_DATE, null);
    // The definition fields are only ever changed by MIGRATED, but are re-asserted on every
    // intent since flush() has no access to the record intent (see AgentInstanceHandler#flush).
    expectedUpdateFields.put(BPMN_PROCESS_ID, entity.getBpmnProcessId());
    expectedUpdateFields.put(PROCESS_DEFINITION_KEY, entity.getProcessDefinitionKey());
    expectedUpdateFields.put(AGENT_DEFINITION_KEY, entity.getAgentDefinitionKey());
    expectedUpdateFields.put(PROCESS_DEFINITION_VERSION, entity.getProcessDefinitionVersion());
    expectedUpdateFields.put(
        PROCESS_DEFINITION_VERSION_TAG, entity.getProcessDefinitionVersionTag());
    expectedUpdateFields.put(ELEMENT_ID, entity.getElementId());

    verify(mockRequest, times(1)).upsert(index, entity.getId(), entity, expectedUpdateFields);
  }

  @Test
  void shouldIncludeCompletionDateInUpdateFieldsWhenSet() {
    // given
    final String entityId = "100";
    final var completionDate = DateUtil.toOffsetDateTime(Instant.now());
    final var entity =
        new AgentInstanceEntity()
            .setId(entityId)
            .setStatus(AgentInstanceStatus.COMPLETED)
            .setCompletionDate(completionDate)
            .setLastUpdatedDate(completionDate);

    final TargetIndex index = TargetIndex.mainIndex("test-index");
    final BatchRequest mockRequest = Mockito.mock(BatchRequest.class);

    // when
    underTest.flush(index, entity, mockRequest);

    // then
    verify(mockRequest, times(1))
        .upsert(
            Mockito.eq(index),
            Mockito.eq(entityId),
            Mockito.eq(entity),
            Mockito.argThat(
                (Map<String, Object> fields) ->
                    fields.containsKey(COMPLETION_DATE)
                        && fields.get(COMPLETION_DATE).equals(completionDate)));
  }

  private Record<AgentInstanceRecordValue> generateRecord(final AgentInstanceIntent intent) {
    return factory.generateRecord(ValueType.AGENT_INSTANCE, r -> r.withIntent(intent));
  }

  private AgentInstanceRecordValue buildMinimalRecordValue(final long agentInstanceKey) {
    return ImmutableAgentInstanceRecordValue.builder()
        .withAgentInstanceKey(agentInstanceKey)
        .withAgentDefinitionKey(700L)
        .withRootProcessInstanceKey(50L)
        .withStatus(io.camunda.zeebe.protocol.record.value.AgentInstanceStatus.IDLE)
        .withDefinition(
            ImmutableAgentInstanceDefinitionValue.builder()
                .withModel("gpt-4o")
                .withProvider("openai")
                .withSystemPrompt(
                    List.of(
                        ImmutableAgentHistoryMessageContentValue.builder()
                            .withContentType(
                                io.camunda.zeebe.protocol.record.value.AgentHistoryContentType.TEXT)
                            .withText("You are helpful.")
                            .build()))
                .build())
        .withLimits(
            ImmutableAgentInstanceLimitsValue.builder()
                .withMaxTokens(500L)
                .withMaxModelCalls(5)
                .withMaxToolCalls(3)
                .build())
        .withMetrics(
            ImmutableAgentInstanceMetricsValue.builder()
                .withInputTokens(0L)
                .withOutputTokens(0L)
                .withModelCalls(0)
                .withToolCalls(0)
                .build())
        .build();
  }
}
