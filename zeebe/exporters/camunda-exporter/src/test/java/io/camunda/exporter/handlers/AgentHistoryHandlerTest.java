/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static io.camunda.webapps.schema.descriptors.template.AgentHistoryTemplate.COMMIT_STATUS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;

import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.descriptors.template.AgentHistoryTemplate;
import io.camunda.webapps.schema.entities.agenthistory.AgentHistoryCommitStatus;
import io.camunda.webapps.schema.entities.agenthistory.AgentHistoryContentType;
import io.camunda.webapps.schema.entities.agenthistory.AgentHistoryEntity;
import io.camunda.webapps.schema.entities.agenthistory.AgentHistoryEntity.AgentHistoryContentValue;
import io.camunda.webapps.schema.entities.agenthistory.AgentHistoryEntity.AgentHistoryEmbeddedToolCallValue;
import io.camunda.webapps.schema.entities.agenthistory.AgentHistoryRole;
import io.camunda.webapps.schema.entities.document.DocumentReferenceEntity;
import io.camunda.webapps.schema.entities.document.DocumentReferenceMetadataEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AgentHistoryIntent;
import io.camunda.zeebe.protocol.record.value.AgentHistoryRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableAgentHistoryEmbeddedToolCallValue;
import io.camunda.zeebe.protocol.record.value.ImmutableAgentHistoryMessageContentValue;
import io.camunda.zeebe.protocol.record.value.ImmutableAgentHistoryMetricsValue;
import io.camunda.zeebe.protocol.record.value.ImmutableAgentHistoryRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableDocumentReferenceMetadataValue;
import io.camunda.zeebe.protocol.record.value.ImmutableDocumentReferenceValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import io.camunda.zeebe.util.DateUtil;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;
import org.mockito.Mockito;

final class AgentHistoryHandlerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = AgentHistoryTemplate.INDEX_NAME;
  private final AgentHistoryHandler underTest = new AgentHistoryHandler(indexName);

  @Test
  void shouldReturnCorrectHandlerMetadata() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.AGENT_HISTORY);
    assertThat(underTest.getEntityType()).isEqualTo(AgentHistoryEntity.class);
    assertThat(underTest.getIndexName()).isEqualTo(indexName);
  }

  @ParameterizedTest(name = "[{index}] Should handle ''{0}'' record")
  @EnumSource(
      value = AgentHistoryIntent.class,
      names = {"CREATED", "COMMITTED", "DISCARDED"},
      mode = Mode.INCLUDE)
  void shouldHandleRecord(final AgentHistoryIntent intent) {
    assertThat(underTest.handlesRecord(generateRecord(intent))).isTrue();
  }

  @ParameterizedTest(name = "[{index}] Should not handle ''{0}'' record")
  @EnumSource(
      value = AgentHistoryIntent.class,
      names = {"CREATED", "COMMITTED", "DISCARDED"},
      mode = Mode.EXCLUDE)
  void shouldNotHandleRecord(final AgentHistoryIntent intent) {
    assertThat(underTest.handlesRecord(generateRecord(intent))).isFalse();
  }

  @Test
  void shouldGenerateIdFromRecordKey() {
    // given
    final Record<AgentHistoryRecordValue> record = factory.generateRecord(ValueType.AGENT_HISTORY);

    // when - then
    assertThat(underTest.generateIds(record)).containsExactly(String.valueOf(record.getKey()));
  }

  @ParameterizedTest(name = "[{index}] Should populate all entity fields for ''{0}'' intent")
  @EnumSource(
      value = AgentHistoryIntent.class,
      names = {"CREATE", "COMMIT"},
      mode = Mode.EXCLUDE)
  void shouldUpdateEntityForAllHandledIntents(final AgentHistoryIntent intent) {
    // given — updateEntity() must populate ALL fields for every handled intent; the partial update
    // (commitStatus-only) is an artefact of flush(), not of updateEntity().
    final long recordKey = 100L;
    final int partitionId = 1;
    final long agentInstanceKey = 50L;
    final long elementInstanceKey = 200L;
    final long processInstanceKey = 300L;
    final long rootProcessInstanceKey = 250L;
    final long processDefinitionKey = 400L;
    final String tenantId = "<default>";
    final long jobKey = 500L;
    final String jobLease = "lease-token-abc";
    final int iteration = 3;
    final long producedAtMs = System.currentTimeMillis();
    final long inputTokens = 50L;
    final long outputTokens = 30L;
    final long durationMs = 1200L;

    final var textContent =
        ImmutableAgentHistoryMessageContentValue.builder()
            .withContentType(io.camunda.zeebe.protocol.record.value.AgentHistoryContentType.TEXT)
            .withText("Hello, world!")
            .withObject(Map.of())
            .build();

    final var toolCall =
        ImmutableAgentHistoryEmbeddedToolCallValue.builder()
            .withToolCallId("tc-1")
            .withToolName("search")
            .withElementId("searchElement")
            .withArguments(Map.of("query", "weather"))
            .build();

    final var recordValue =
        ImmutableAgentHistoryRecordValue.builder()
            .withAgentInstanceKey(agentInstanceKey)
            .withElementInstanceKey(elementInstanceKey)
            .withProcessInstanceKey(processInstanceKey)
            .withRootProcessInstanceKey(rootProcessInstanceKey)
            .withProcessDefinitionKey(processDefinitionKey)
            .withTenantId(tenantId)
            .withJobKey(jobKey)
            .withJobLease(jobLease)
            .withIteration(iteration)
            .withRole(io.camunda.zeebe.protocol.record.value.AgentHistoryRole.ASSISTANT)
            .withProducedAt(producedAtMs)
            .withMetrics(
                ImmutableAgentHistoryMetricsValue.builder()
                    .withInputTokens(inputTokens)
                    .withOutputTokens(outputTokens)
                    .withDurationMs(durationMs)
                    .build())
            .withContent(List.of(textContent))
            .withToolCalls(List.of(toolCall))
            .build();

    final Record<AgentHistoryRecordValue> record =
        factory.generateRecord(
            ValueType.AGENT_HISTORY,
            r ->
                r.withIntent(intent)
                    .withKey(recordKey)
                    .withPartitionId(partitionId)
                    .withValue(recordValue));

    final var entity = new AgentHistoryEntity().setId(String.valueOf(recordKey));

    // when
    underTest.updateEntity(record, entity);

    // then
    final AgentHistoryCommitStatus expectedStatus =
        switch (intent) {
          case CREATED -> AgentHistoryCommitStatus.PENDING;
          case COMMITTED -> AgentHistoryCommitStatus.COMMITTED;
          case DISCARDED -> AgentHistoryCommitStatus.DISCARDED;
          default -> throw new IllegalStateException("Unexpected intent: " + intent);
        };

    assertThat(entity.getKey()).isEqualTo(recordKey);
    assertThat(entity.getPartitionId()).isEqualTo(partitionId);
    assertThat(entity.getAgentInstanceKey()).isEqualTo(agentInstanceKey);
    assertThat(entity.getElementInstanceKey()).isEqualTo(elementInstanceKey);
    assertThat(entity.getProcessInstanceKey()).isEqualTo(processInstanceKey);
    assertThat(entity.getRootProcessInstanceKey()).isEqualTo(rootProcessInstanceKey);
    assertThat(entity.getProcessDefinitionKey()).isEqualTo(processDefinitionKey);
    assertThat(entity.getTenantId()).isEqualTo(tenantId);
    assertThat(entity.getJobKey()).isEqualTo(jobKey);
    assertThat(entity.getJobLease()).isEqualTo(jobLease);
    assertThat(entity.getIteration()).isEqualTo(iteration);
    assertThat(entity.getRole()).isEqualTo(AgentHistoryRole.ASSISTANT);
    assertThat(entity.getCommitStatus()).isEqualTo(expectedStatus);
    assertThat(entity.getProducedAt())
        .isEqualTo(DateUtil.toOffsetDateTime(Instant.ofEpochMilli(producedAtMs)));
    assertThat(entity.getInputTokens()).isEqualTo(inputTokens);
    assertThat(entity.getOutputTokens()).isEqualTo(outputTokens);
    assertThat(entity.getDurationMs()).isEqualTo(durationMs);
    assertThat(entity.getContent())
        .containsExactly(
            new AgentHistoryContentValue(
                AgentHistoryContentType.TEXT, "Hello, world!", null, null));
    assertThat(entity.getToolCalls())
        .containsExactly(
            new AgentHistoryEmbeddedToolCallValue(
                "tc-1", "search", "searchElement", Map.of("query", "weather")));
  }

  @Test
  void shouldFlushWithUpsertContainingOnlyCommitStatus() {
    // given — entity populated via updateEntity
    final var recordValue = buildMinimalRecordValue(1L, 1);
    final Record<AgentHistoryRecordValue> record =
        factory.generateRecord(
            ValueType.AGENT_HISTORY,
            r -> r.withIntent(AgentHistoryIntent.CREATED).withKey(1L).withValue(recordValue));
    final var entity = new AgentHistoryEntity().setId("1");
    underTest.updateEntity(record, entity);

    final BatchRequest mockRequest = Mockito.mock(BatchRequest.class);

    // when
    underTest.flush(entity, mockRequest);

    // then — only commitStatus is included in the upsert updateFields map
    verify(mockRequest)
        .upsert(indexName, entity.getId(), entity, Map.of(COMMIT_STATUS, entity.getCommitStatus()));
  }

  @ParameterizedTest(name = "[{index}] Should map protocol role ''{0}'' to entity role")
  @EnumSource(
      value = io.camunda.zeebe.protocol.record.value.AgentHistoryRole.class,
      names = {"UNSPECIFIED"},
      mode = Mode.EXCLUDE)
  void shouldMapAllRoleValues(
      final io.camunda.zeebe.protocol.record.value.AgentHistoryRole protocolRole) {
    // given
    final var recordValue =
        ImmutableAgentHistoryRecordValue.builder()
            .from(buildMinimalRecordValue(1L, 1))
            .withRole(protocolRole)
            .build();
    final Record<AgentHistoryRecordValue> record =
        factory.generateRecord(
            ValueType.AGENT_HISTORY,
            r -> r.withIntent(AgentHistoryIntent.CREATED).withValue(recordValue));
    final var entity = new AgentHistoryEntity().setId("1");

    // when
    underTest.updateEntity(record, entity);

    // then — each protocol role maps to an entity role with the same name
    assertThat(entity.getRole()).isNotNull();
    assertThat(entity.getRole().name())
        .as(
            """
            Protocol role '%s' has no explicit mapping in 'AgentHistoryHandler.mapRole()' \
            and falls back to 'UNKNOWN' — add '%s' to '%s' entity enum and handle \
            it explicitly in the switch, or exclude it from this test if UNKNOWN is intentional.\
            """,
            protocolRole.name(), protocolRole.name(), AgentHistoryRole.class.getSimpleName())
        .isEqualTo(protocolRole.name());
  }

  @Test
  void shouldMapUnspecifiedRoleToUnknown() {
    // given
    final var recordValue =
        ImmutableAgentHistoryRecordValue.builder()
            .from(buildMinimalRecordValue(1L, 1))
            .withRole(io.camunda.zeebe.protocol.record.value.AgentHistoryRole.UNSPECIFIED)
            .build();
    final Record<AgentHistoryRecordValue> record =
        factory.generateRecord(
            ValueType.AGENT_HISTORY,
            r -> r.withIntent(AgentHistoryIntent.CREATED).withValue(recordValue));
    final var entity = new AgentHistoryEntity().setId("1");

    // when
    underTest.updateEntity(record, entity);

    // then
    assertThat(entity.getRole()).isEqualTo(AgentHistoryRole.UNKNOWN);
  }

  @ParameterizedTest(name = "[{index}] Intent ''{0}'' should map to the expected commitStatus")
  @EnumSource(
      value = AgentHistoryIntent.class,
      names = {"CREATE", "COMMIT"},
      mode = Mode.EXCLUDE)
  void shouldMapIntentToExpectedCommitStatus(final AgentHistoryIntent intent) {
    // given
    final Record<AgentHistoryRecordValue> record =
        factory.generateRecord(
            ValueType.AGENT_HISTORY,
            r -> r.withIntent(intent).withValue(buildMinimalRecordValue(1L, 1)));
    final var entity = new AgentHistoryEntity().setId("1");

    // when
    underTest.updateEntity(record, entity);

    // then — commitStatus is derived from intent, not from the record value
    final AgentHistoryCommitStatus expected =
        switch (intent) {
          case CREATED -> AgentHistoryCommitStatus.PENDING;
          default -> AgentHistoryCommitStatus.valueOf(intent.name());
        };
    assertThat(entity.getCommitStatus()).isEqualTo(expected);
  }

  @ParameterizedTest(name = "[{index}] Unexpected ''{0}'' should map to ''UNKNOWN'' commitStatus")
  @EnumSource(
      value = AgentHistoryIntent.class,
      names = {"CREATED", "COMMITTED", "DISCARDED"},
      mode = Mode.EXCLUDE)
  void shouldMapUnexpectedIntentToUnknownCommitStatus(final AgentHistoryIntent intent) {
    // given
    final Record<AgentHistoryRecordValue> record =
        factory.generateRecord(
            ValueType.AGENT_HISTORY,
            r -> r.withIntent(intent).withValue(buildMinimalRecordValue(1L, 1)));
    final var entity = new AgentHistoryEntity().setId("1");

    // when
    underTest.updateEntity(record, entity);

    // then
    assertThat(entity.getCommitStatus()).isEqualTo(AgentHistoryCommitStatus.UNKNOWN);
  }

  @Test
  void shouldMapObjectContentType() {
    // given
    final Map<String, Object> objectData = Map.of("result", "42", "confidence", "high");
    final var contentItem =
        ImmutableAgentHistoryMessageContentValue.builder()
            .withContentType(io.camunda.zeebe.protocol.record.value.AgentHistoryContentType.OBJECT)
            .withText("")
            .withObject(objectData)
            .build();
    final var recordValue =
        ImmutableAgentHistoryRecordValue.builder()
            .from(buildMinimalRecordValue(1L, 1))
            .withContent(List.of(contentItem))
            .build();
    final Record<AgentHistoryRecordValue> record =
        factory.generateRecord(
            ValueType.AGENT_HISTORY,
            r -> r.withIntent(AgentHistoryIntent.CREATED).withValue(recordValue));
    final var entity = new AgentHistoryEntity().setId("1");

    // when
    underTest.updateEntity(record, entity);

    // then — object field is populated; text and documentReference are null
    assertThat(entity.getContent())
        .singleElement()
        .satisfies(
            content -> {
              assertThat(content.contentType()).isEqualTo(AgentHistoryContentType.OBJECT);
              assertThat(content.text()).isNull();
              assertThat(content.documentReference()).isNull();
              assertThat(content.object()).isEqualTo(objectData);
            });
  }

  @Test
  void shouldMapUnspecifiedContentTypeToUnknown() {
    // given — UNSPECIFIED is the protocol sentinel; it should survive as UNKNOWN (not be dropped)
    final var unspecifiedItem =
        ImmutableAgentHistoryMessageContentValue.builder()
            .withContentType(
                io.camunda.zeebe.protocol.record.value.AgentHistoryContentType.UNSPECIFIED)
            .withText("some text")
            .withObject(Map.of("key", "value"))
            .build();
    final var recordValue =
        ImmutableAgentHistoryRecordValue.builder()
            .from(buildMinimalRecordValue(1L, 1))
            .withContent(List.of(unspecifiedItem))
            .build();
    final Record<AgentHistoryRecordValue> record =
        factory.generateRecord(
            ValueType.AGENT_HISTORY,
            r -> r.withIntent(AgentHistoryIntent.CREATED).withValue(recordValue));
    final var entity = new AgentHistoryEntity().setId("1");

    // when
    underTest.updateEntity(record, entity);

    // then — UNSPECIFIED is preserved as UNKNOWN with all available fields
    assertThat(entity.getContent())
        .singleElement()
        .satisfies(
            content -> {
              assertThat(content.contentType()).isEqualTo(AgentHistoryContentType.UNKNOWN);
              assertThat(content.text()).isEqualTo("some text");
              assertThat(content.object()).isEqualTo(Map.of("key", "value"));
            });
  }

  @ParameterizedTest(
      name = "[{index}] Protocol type ''{0}'' should have a matching entity enum constant")
  @EnumSource(
      value = io.camunda.zeebe.protocol.record.value.AgentHistoryContentType.class,
      names = "UNSPECIFIED",
      mode = Mode.EXCLUDE)
  void shouldMapAllSupportedContentTypes(
      final io.camunda.zeebe.protocol.record.value.AgentHistoryContentType protocolType) {
    // This test will fail if a new content type is added to the protocol without a corresponding
    // constant in the entity AgentHistoryContentType enum. Add the new constant to fix it.
    assertThatCode(() -> AgentHistoryContentType.valueOf(protocolType.name()))
        .as(
            "AgentHistoryContentType must have a constant named '%s' to match protocol type %s."
                + " Add the constant to AgentHistoryContentType and handle it properly in AgentHistoryHandler.",
            protocolType.name(), protocolType)
        .doesNotThrowAnyException();
  }

  @Test
  void shouldConvertNegativeExpiresAtToNull() {
    // given — document reference with expiresAt == -1 (sentinel for "no expiry")
    final var metadata =
        ImmutableDocumentReferenceMetadataValue.builder()
            .withExpiresAt(-1L)
            .withSize(1024L)
            .withProcessInstanceKey(-1L)
            .build();
    final var docRef = ImmutableDocumentReferenceValue.builder().withMetadata(metadata).build();
    final var contentItem =
        ImmutableAgentHistoryMessageContentValue.builder()
            .withContentType(
                io.camunda.zeebe.protocol.record.value.AgentHistoryContentType.DOCUMENT)
            .withText("")
            .withObject(Map.of())
            .withDocumentReference(docRef)
            .build();
    final var recordValue =
        ImmutableAgentHistoryRecordValue.builder()
            .from(buildMinimalRecordValue(1L, 1))
            .withContent(List.of(contentItem))
            .build();
    final Record<AgentHistoryRecordValue> record =
        factory.generateRecord(
            ValueType.AGENT_HISTORY,
            r -> r.withIntent(AgentHistoryIntent.CREATED).withValue(recordValue));
    final var entity = new AgentHistoryEntity().setId("1");

    // when
    underTest.updateEntity(record, entity);

    // then
    final DocumentReferenceEntity docRefEntity = entity.getContent().getFirst().documentReference();
    assertThat(docRefEntity).isNotNull();
    final DocumentReferenceMetadataEntity metaEntity = docRefEntity.metadata();
    assertThat(metaEntity.expiresAt()).isNull();
    assertThat(metaEntity.processInstanceKey()).isNull();
  }

  @Test
  void shouldMapDocumentContentWithValidMetadata() {
    // given — document reference with positive expiresAt and processInstanceKey (not sentinels)
    final long expiresAtMs = System.currentTimeMillis() + 86_400_000L;
    final long docProcessInstanceKey = 42L;
    final var metadata =
        ImmutableDocumentReferenceMetadataValue.builder()
            .withExpiresAt(expiresAtMs)
            .withSize(2048L)
            .withProcessInstanceKey(docProcessInstanceKey)
            .withContentType("application/pdf")
            .withFileName("report.pdf")
            .withProcessDefinitionId("my-process")
            .withCustomProperties(Map.of("source", "upload"))
            .build();
    final var docRef =
        ImmutableDocumentReferenceValue.builder()
            .withDocumentId("doc-1")
            .withStoreId("store-1")
            .withContentHash("abc123")
            .withMetadata(metadata)
            .build();
    final var contentItem =
        ImmutableAgentHistoryMessageContentValue.builder()
            .withContentType(
                io.camunda.zeebe.protocol.record.value.AgentHistoryContentType.DOCUMENT)
            .withText("")
            .withObject(Map.of())
            .withDocumentReference(docRef)
            .build();
    final var recordValue =
        ImmutableAgentHistoryRecordValue.builder()
            .from(buildMinimalRecordValue(1L, 1))
            .withContent(List.of(contentItem))
            .build();
    final Record<AgentHistoryRecordValue> record =
        factory.generateRecord(
            ValueType.AGENT_HISTORY,
            r -> r.withIntent(AgentHistoryIntent.CREATED).withValue(recordValue));
    final var entity = new AgentHistoryEntity().setId("1");

    // when
    underTest.updateEntity(record, entity);

    // then — valid expiresAt and processInstanceKey are preserved (not nulled)
    final DocumentReferenceEntity docRefEntity = entity.getContent().getFirst().documentReference();
    assertThat(docRefEntity).isNotNull();
    assertThat(docRefEntity.documentId()).isEqualTo("doc-1");
    assertThat(docRefEntity.storeId()).isEqualTo("store-1");
    assertThat(docRefEntity.contentHash()).isEqualTo("abc123");
    final DocumentReferenceMetadataEntity metaEntity = docRefEntity.metadata();
    assertThat(metaEntity.expiresAt())
        .isEqualTo(DateUtil.toOffsetDateTime(Instant.ofEpochMilli(expiresAtMs)));
    assertThat(metaEntity.size()).isEqualTo(2048L);
    assertThat(metaEntity.processInstanceKey()).isEqualTo(docProcessInstanceKey);
    assertThat(metaEntity.contentType()).isEqualTo("application/pdf");
    assertThat(metaEntity.fileName()).isEqualTo("report.pdf");
    assertThat(metaEntity.processDefinitionId()).isEqualTo("my-process");
    assertThat(metaEntity.customProperties()).isEqualTo(Map.of("source", "upload"));
  }

  @Test
  void shouldConvertNonPositiveIterationToNull() {
    // given — iteration == 0 (non-positive sentinel → null)
    final var recordValue =
        ImmutableAgentHistoryRecordValue.builder().from(buildMinimalRecordValue(1L, 0)).build();
    final Record<AgentHistoryRecordValue> record =
        factory.generateRecord(
            ValueType.AGENT_HISTORY,
            r -> r.withIntent(AgentHistoryIntent.CREATED).withValue(recordValue));
    final var entity = new AgentHistoryEntity().setId("1");

    // when
    underTest.updateEntity(record, entity);

    // then
    assertThat(entity.getIteration()).isNull();
  }

  @Test
  void shouldStorePositiveIterationAsIs() {
    // given
    final var recordValue =
        ImmutableAgentHistoryRecordValue.builder().from(buildMinimalRecordValue(1L, 5)).build();
    final Record<AgentHistoryRecordValue> record =
        factory.generateRecord(
            ValueType.AGENT_HISTORY,
            r -> r.withIntent(AgentHistoryIntent.CREATED).withValue(recordValue));
    final var entity = new AgentHistoryEntity().setId("1");

    // when
    underTest.updateEntity(record, entity);

    // then
    assertThat(entity.getIteration()).isEqualTo(5);
  }

  @Test
  void shouldConvertNonPositiveProducedAtToNull() {
    // given — producedAt == -1 is the protocol default for "unset"; Instant.ofEpochMilli(-1)
    // would produce a 1969 timestamp, which is invalid for this field
    final var recordValue =
        ImmutableAgentHistoryRecordValue.builder()
            .from(buildMinimalRecordValue(1L, 1))
            .withProducedAt(-1L)
            .build();
    final Record<AgentHistoryRecordValue> record =
        factory.generateRecord(
            ValueType.AGENT_HISTORY,
            r -> r.withIntent(AgentHistoryIntent.CREATED).withValue(recordValue));
    final var entity = new AgentHistoryEntity().setId("1");

    // when
    underTest.updateEntity(record, entity);

    // then
    assertThat(entity.getProducedAt()).isNull();
  }

  // --- helpers ---

  private Record<AgentHistoryRecordValue> generateRecord(final AgentHistoryIntent intent) {
    return factory.generateRecord(ValueType.AGENT_HISTORY, r -> r.withIntent(intent));
  }

  private AgentHistoryRecordValue buildMinimalRecordValue(
      final long agentInstanceKey, final int iteration) {
    return ImmutableAgentHistoryRecordValue.builder()
        .withAgentInstanceKey(agentInstanceKey)
        .withElementInstanceKey(1L)
        .withProcessInstanceKey(10L)
        .withRootProcessInstanceKey(10L)
        .withProcessDefinitionKey(20L)
        .withTenantId("<default>")
        .withJobKey(30L)
        .withJobLease("lease")
        .withIteration(iteration)
        .withRole(io.camunda.zeebe.protocol.record.value.AgentHistoryRole.ASSISTANT)
        .withProducedAt(System.currentTimeMillis())
        .withMetrics(
            ImmutableAgentHistoryMetricsValue.builder()
                .withInputTokens(0L)
                .withOutputTokens(0L)
                .withDurationMs(0L)
                .build())
        .withContent(List.of())
        .withToolCalls(List.of())
        .build();
  }
}
