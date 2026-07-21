/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter;

import static io.camunda.exporter.utils.CamundaExporterSchemaUtils.createSchemas;
import static io.camunda.search.test.utils.SearchDBExtension.CUSTOM_PREFIX;
import static io.camunda.search.test.utils.SearchDBExtension.TEST_INTEGRATION_OPENSEARCH_AWS_URL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.exporter.cache.ExporterEntityCacheProvider;
import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.exporter.handlers.ExportHandler;
import io.camunda.exporter.utils.CamundaExporterITTemplateExtension;
import io.camunda.search.test.utils.SearchClientAdapter;
import io.camunda.search.test.utils.SearchDBExtension;
import io.camunda.search.test.utils.TestObjectMapper;
import io.camunda.webapps.schema.entities.agenthistory.AgentHistoryCommitStatus;
import io.camunda.webapps.schema.entities.agenthistory.AgentHistoryContentType;
import io.camunda.webapps.schema.entities.agenthistory.AgentHistoryEntity;
import io.camunda.webapps.schema.entities.agenthistory.AgentHistoryEntity.AgentHistoryContentValue;
import io.camunda.webapps.schema.entities.agenthistory.AgentHistoryEntity.AgentHistoryEmbeddedToolCallValue;
import io.camunda.zeebe.exporter.test.ExporterTestConfiguration;
import io.camunda.zeebe.exporter.test.ExporterTestContext;
import io.camunda.zeebe.exporter.test.ExporterTestController;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AgentHistoryIntent;
import io.camunda.zeebe.protocol.record.value.AgentHistoryRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableAgentHistoryEmbeddedToolCallValue;
import io.camunda.zeebe.protocol.record.value.ImmutableAgentHistoryMessageContentValue;
import io.camunda.zeebe.protocol.record.value.ImmutableAgentHistoryMetricsValue;
import io.camunda.zeebe.protocol.record.value.ImmutableAgentHistoryRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import io.camunda.zeebe.util.DateUtil;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Verifies, end-to-end against a real Elasticsearch/OpenSearch backend, that a CREATED event's
 * content/toolCalls/metrics/producedAt survive a trimmed COMMITTED/DISCARDED counterpart for the
 * same id landing in the same export batch, rather than being clobbered before the document is
 * first written.
 */
@TestInstance(Lifecycle.PER_CLASS)
final class AgentHistoryExportConsistencyIT {

  private static final long ORIGINAL_PRODUCED_AT_MS = 1_000_000_000_000L;
  // only producedAt survival is asserted; this documents the CREATED event's timestamp
  private static final long CREATED_TIMESTAMP_MS = 1_500_000_000_000L;
  private static final long TERMINAL_TIMESTAMP_MS = 1_600_000_000_000L;

  @RegisterExtension private static final SearchDBExtension SEARCH_DB = SearchDBExtension.create();

  @RegisterExtension
  private static final CamundaExporterITTemplateExtension TEMPLATE_EXTENSION =
      new CamundaExporterITTemplateExtension(SEARCH_DB);

  private final ProtocolFactory factory = new ProtocolFactory();

  @AfterEach
  void afterEach() throws IOException {
    final var openSearchAwsInstanceUrl =
        Optional.ofNullable(System.getProperty(TEST_INTEGRATION_OPENSEARCH_AWS_URL)).orElse("");
    if (openSearchAwsInstanceUrl.isEmpty()) {
      SEARCH_DB.esClient().indices().delete(req -> req.index(CUSTOM_PREFIX + "*"));
    }
    SEARCH_DB.osClient().indices().delete(req -> req.index(CUSTOM_PREFIX + "*"));
  }

  @TestTemplate
  void shouldNotLoseContentToolCallsMetricsAndProducedAtWhenTerminalEventCoalescesWithCreated(
      final ExporterConfiguration config, final SearchClientAdapter clientAdapter)
      throws IOException {
    // given — schema ready and auto-flush disabled (bulk size above the entity count, long delay)
    // so the CREATED and terminal records coalesce in one batch instead of flushing separately.
    createSchemas(config);
    config.getBulk().setSize(10);
    config.getBulk().setDelay(60);

    final var agentHistoryHandler = agentHistoryHandler(config);

    final var camundaExporter = new CamundaExporter();
    final var exporterTestContext =
        new ExporterTestContext()
            .setConfiguration(new ExporterTestConfiguration<>("camundaExporter", config));
    camundaExporter.configure(exporterTestContext);
    camundaExporter.open(new ExporterTestController());

    // when — for BOTH terminal intents, export a full CREATED record immediately followed by its
    // trimmed terminal counterpart for the SAME id, with no flush in between so both land in the
    // writer's cached-entity map and get coalesced onto the same entity instance.
    final long committedKey = 1_001L;
    final long discardedKey = 1_002L;

    exportCreatedThenTerminal(camundaExporter, committedKey, AgentHistoryIntent.COMMITTED);
    exportCreatedThenTerminal(camundaExporter, discardedKey, AgentHistoryIntent.DISCARDED);

    // self-proof that the records coalesced: a realtime GET before the flush must find nothing
    // (both still cached in-memory). If auto-flush suppression ever regressed, the CREATED doc
    // would already be persisted here and this fails loudly instead of passing a separate-flush
    // path.
    final var committedBeforeFlush =
        clientAdapter.get(
            String.valueOf(committedKey),
            agentHistoryHandler.getIndexName(),
            AgentHistoryEntity.class);
    final var discardedBeforeFlush =
        clientAdapter.get(
            String.valueOf(discardedKey),
            agentHistoryHandler.getIndexName(),
            AgentHistoryEntity.class);
    assertThat(committedBeforeFlush)
        .as(
            "COMMITTED document must not exist before the flush — otherwise the two records did"
                + " not coalesce into one batch and this test would not reproduce the bug")
        .isNull();
    assertThat(discardedBeforeFlush)
        .as(
            "DISCARDED document must not exist before the flush — otherwise the two records did"
                + " not coalesce into one batch and this test would not reproduce the bug")
        .isNull();

    // a single flush now persists everything accumulated above in one bulk request
    camundaExporter.close();

    // then — the persisted documents must retain the original CREATED values, not the values the
    // shared in-memory entity would have been left with if the terminal event's trimmed record
    // had overwritten them before either record was ever flushed to the search engine.
    final var committedEntity =
        clientAdapter.get(
            String.valueOf(committedKey),
            agentHistoryHandler.getIndexName(),
            AgentHistoryEntity.class);
    final var discardedEntity =
        clientAdapter.get(
            String.valueOf(discardedKey),
            agentHistoryHandler.getIndexName(),
            AgentHistoryEntity.class);

    // self-proof, continued: after the single flush, both documents must now exist.
    assertThat(committedEntity).as("COMMITTED document must exist after the flush").isNotNull();
    assertThat(discardedEntity).as("DISCARDED document must exist after the flush").isNotNull();

    SoftAssertions.assertSoftly(
        softly -> {
          assertPreservedCreatedValues(
              softly, committedEntity, AgentHistoryCommitStatus.COMMITTED, "COMMITTED");
          assertPreservedCreatedValues(
              softly, discardedEntity, AgentHistoryCommitStatus.DISCARDED, "DISCARDED");
        });
  }

  private void exportCreatedThenTerminal(
      final CamundaExporter exporter,
      final long recordKey,
      final AgentHistoryIntent terminalIntent) {
    final var createdValue = buildFullCreatedRecordValue();
    final Record<AgentHistoryRecordValue> createdRecord =
        factory.generateRecord(
            ValueType.AGENT_HISTORY,
            r ->
                r.withIntent(AgentHistoryIntent.CREATED)
                    .withKey(recordKey)
                    .withPartitionId(1)
                    .withTimestamp(CREATED_TIMESTAMP_MS)
                    .withValue(createdValue));

    final var trimmedValue = buildTrimmedTerminalRecordValue(createdValue);
    final Record<AgentHistoryRecordValue> terminalRecord =
        factory.generateRecord(
            ValueType.AGENT_HISTORY,
            r ->
                r.withIntent(terminalIntent)
                    .withKey(recordKey)
                    .withPartitionId(1)
                    .withTimestamp(TERMINAL_TIMESTAMP_MS)
                    .withValue(trimmedValue));

    // no flush() call between these two — they must coalesce into the same batch
    exporter.export(createdRecord);
    exporter.export(terminalRecord);
  }

  private AgentHistoryRecordValue buildFullCreatedRecordValue() {
    final var content =
        ImmutableAgentHistoryMessageContentValue.builder()
            .withContentType(io.camunda.zeebe.protocol.record.value.AgentHistoryContentType.TEXT)
            .withText("the original user prompt")
            .withObject(Map.of())
            .build();

    final var toolCall =
        ImmutableAgentHistoryEmbeddedToolCallValue.builder()
            .withToolCallId("tc-1")
            .withToolName("search")
            .withElementId("searchElement")
            .withArguments(Map.of("query", "weather"))
            .build();

    return ImmutableAgentHistoryRecordValue.builder()
        .withAgentInstanceKey(50L)
        .withElementInstanceKey(200L)
        .withProcessInstanceKey(300L)
        .withRootProcessInstanceKey(300L)
        .withBpmnProcessId("agent-process")
        .withProcessDefinitionKey(400L)
        .withTenantId("<default>")
        .withJobKey(500L)
        .withJobLease("lease-token")
        .withLoopIteration(1)
        .withRole(io.camunda.zeebe.protocol.record.value.AgentHistoryRole.ASSISTANT)
        .withProducedAt(ORIGINAL_PRODUCED_AT_MS)
        .withMetrics(
            ImmutableAgentHistoryMetricsValue.builder()
                .withInputTokens(50L)
                .withOutputTokens(30L)
                .withDurationMs(1200L)
                .build())
        .withContent(List.of(content))
        .withToolCalls(List.of(toolCall))
        .build();
  }

  private AgentHistoryRecordValue buildTrimmedTerminalRecordValue(
      final AgentHistoryRecordValue createdValue) {
    return ImmutableAgentHistoryRecordValue.builder()
        .from(createdValue)
        .withProducedAt(0L)
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

  private void assertPreservedCreatedValues(
      final SoftAssertions softly,
      final AgentHistoryEntity entity,
      final AgentHistoryCommitStatus expectedStatus,
      final String label) {
    softly.assertThat(entity).as("%s document must exist", label).isNotNull();
    if (entity == null) {
      return;
    }
    softly
        .assertThat(entity.getContent())
        .as("%s: content must still hold the original CREATED text", label)
        .containsExactly(
            new AgentHistoryContentValue(
                AgentHistoryContentType.TEXT, "the original user prompt", null, null));
    softly
        .assertThat(entity.getToolCalls())
        .as("%s: toolCalls must still hold the original CREATED tool call", label)
        .containsExactly(
            new AgentHistoryEmbeddedToolCallValue(
                "tc-1", "search", "searchElement", Map.of("query", "weather")));
    softly
        .assertThat(entity.getInputTokens())
        .as("%s: inputTokens must still hold the original CREATED value", label)
        .isEqualTo(50L);
    softly
        .assertThat(entity.getOutputTokens())
        .as("%s: outputTokens must still hold the original CREATED value", label)
        .isEqualTo(30L);
    softly
        .assertThat(entity.getDurationMs())
        .as("%s: durationMs must still hold the original CREATED value", label)
        .isEqualTo(1200L);
    softly
        .assertThat(entity.getProducedAt())
        .as(
            "%s: producedAt must still hold the original CREATED value, not the terminal event's"
                + " fallback timestamp",
            label)
        .isEqualTo(DateUtil.toOffsetDateTime(Instant.ofEpochMilli(ORIGINAL_PRODUCED_AT_MS)));
    softly
        .assertThat(entity.getCommitStatus())
        .as("%s: commitStatus must reflect the terminal event that was actually applied", label)
        .isEqualTo(expectedStatus);
  }

  /**
   * Builds the AGENT_HISTORY export handler so the test can read documents back by the {@code
   * CUSTOM_PREFIX}-qualified index name the exporter actually wrote to; the unprefixed {@code
   * AgentHistoryTemplate.INDEX_NAME} would miss the document.
   */
  private ExportHandler<?, ?> agentHistoryHandler(final ExporterConfiguration config) {
    final var cacheProvider = mock(ExporterEntityCacheProvider.class);
    when(cacheProvider.getProcessCacheLoader(anyString(), any())).thenReturn(k -> null);
    when(cacheProvider.getBatchOperationCacheLoader(anyString())).thenReturn(k -> null);
    when(cacheProvider.getDecisionRequirementsCacheLoader(anyString())).thenReturn(k -> null);
    when(cacheProvider.getFormCacheLoader(anyString())).thenReturn(k -> null);
    final var resourceProvider = new DefaultExporterResourceProvider();
    resourceProvider.init(
        config,
        cacheProvider,
        new ExporterTestContext(),
        new ExporterMetadata(TestObjectMapper.objectMapper()),
        TestObjectMapper.objectMapper());

    return resourceProvider.getExportHandlers().stream()
        .filter(h -> h.getHandledValueType() == ValueType.AGENT_HISTORY)
        .findFirst()
        .orElseThrow();
  }
}
