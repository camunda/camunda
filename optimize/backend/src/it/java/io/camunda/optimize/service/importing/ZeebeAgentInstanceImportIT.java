/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing;

import static io.camunda.optimize.service.db.DatabaseConstants.ZEEBE_AGENT_INSTANCE_INDEX_NAME;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.optimize.AbstractBrokerlessZeebeCCSMIT;
import io.camunda.optimize.dto.optimize.query.process.AgentInstanceDto;
import io.camunda.optimize.dto.zeebe.agentinstance.ZeebeAgentInstanceDataDto;
import io.camunda.optimize.dto.zeebe.agentinstance.ZeebeAgentInstanceDataDto.AgentDefinitionValueDto;
import io.camunda.optimize.dto.zeebe.agentinstance.ZeebeAgentInstanceDataDto.AgentMetricsValueDto;
import io.camunda.optimize.dto.zeebe.agentinstance.ZeebeAgentInstanceDataDto.AgentToolValueDto;
import io.camunda.optimize.dto.zeebe.agentinstance.ZeebeAgentInstanceRecordDto;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AgentInstanceIntent;
import io.camunda.zeebe.protocol.record.value.AgentInstanceStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Full pipeline IT: seeds fake AGENT_INSTANCE records directly into the Zeebe export index, runs
 * the Optimize import pipeline, and verifies the resulting ProcessInstance document.
 *
 * <p>Seeding fake records avoids a live Zeebe broker dependency and makes the tests fast and
 * deterministic.
 */
class ZeebeAgentInstanceImportIT extends AbstractBrokerlessZeebeCCSMIT {

  private static final long PROCESS_INSTANCE_KEY = 100L;
  private static final long PROCESS_DEFINITION_KEY = 10L;
  private static final String BPMN_PROCESS_ID = "agentTestProcess";
  private static final String ELEMENT_ID = "agentTask";
  private static final String MODEL = "gpt-4o";
  private static final String PROVIDER = "openai";
  private static final String SYSTEM_PROMPT = "You are a helpful assistant.";
  // Fixed base timestamp so seeded record times are deterministic across runs (no wall-clock
  // dependency). Each seeded record's timestamp = BASE + position * 1000ms.
  private static final long BASE_TIMESTAMP_MS =
      OffsetDateTime.parse("2024-01-01T10:00:00+00:00").toInstant().toEpochMilli();

  // Counters are instance fields, not static: JUnit Jupiter creates a fresh test instance per
  // method, so positionCounter and keyCounter reset between tests and stay isolated.
  private final AtomicLong positionCounter = new AtomicLong(1);
  private final AtomicLong keyCounter = new AtomicLong(200);

  private String agentInstanceIndex;

  @BeforeEach
  void setUp() {
    agentInstanceIndex = ZEEBE_RECORD_PREFIX + "-" + ZEEBE_AGENT_INSTANCE_INDEX_NAME;
  }

  @Test
  void shouldImportCreatedAgentInstance() {
    // given
    final long agentKey = keyCounter.getAndIncrement();
    seedRecord(
        agentKey, AgentInstanceIntent.CREATED, AgentInstanceStatus.INITIALIZING, 0L, 0L, 0, 0);

    // when
    importAllZeebeEntitiesFromScratch();

    // then
    assertThat(databaseIntegrationTestExtension.getAllProcessInstances())
        .singleElement()
        .satisfies(
            pi -> {
              assertThat(pi.getProcessInstanceId()).isEqualTo(String.valueOf(PROCESS_INSTANCE_KEY));
              assertThat(pi.getAgentInstances()).hasSize(1);
              final AgentInstanceDto agent = pi.getAgentInstances().get(0);
              assertThat(agent.getAgentInstanceId()).isEqualTo(String.valueOf(agentKey));
              assertThat(agent.getFlowNodeId()).isEqualTo(ELEMENT_ID);
              assertThat(agent.getStatus()).isEqualTo(AgentInstanceStatus.INITIALIZING.name());
              assertThat(agent.getStartDate()).isNotNull();
              assertThat(agent.getEndDate()).isNull();
              assertThat(agent.getTotalDurationInMs()).isNull();
              assertThat(agent.getDefinition()).isNotNull();
              assertThat(agent.getDefinition().getModel()).isEqualTo(MODEL);
              assertThat(agent.getDefinition().getProvider()).isEqualTo(PROVIDER);
              assertThat(agent.getMetrics().getInputTokens()).isZero();
              assertThat(agent.getMetrics().getOutputTokens()).isZero();
              assertThat(agent.getMetrics().getModelCalls()).isZero();
              assertThat(agent.getMetrics().getToolCalls()).isZero();
            });
  }

  @Test
  void shouldImportUpdatedAgentInstance_accumulatesMetrics() {
    // given
    final long agentKey = keyCounter.getAndIncrement();
    seedRecord(
        agentKey, AgentInstanceIntent.CREATED, AgentInstanceStatus.INITIALIZING, 0L, 0L, 0, 0);
    seedRecord(
        agentKey, AgentInstanceIntent.UPDATED, AgentInstanceStatus.THINKING, 512L, 128L, 1, 2);

    // when
    importAllZeebeEntitiesFromScratch();

    // then
    assertThat(databaseIntegrationTestExtension.getAllProcessInstances())
        .singleElement()
        .satisfies(
            pi -> {
              assertThat(pi.getProcessInstanceId()).isEqualTo(String.valueOf(PROCESS_INSTANCE_KEY));
              assertThat(pi.getAgentInstances()).hasSize(1);
              final AgentInstanceDto agent = pi.getAgentInstances().get(0);
              assertThat(agent.getStatus()).isEqualTo(AgentInstanceStatus.THINKING.name());
              assertThat(agent.getMetrics().getInputTokens()).isEqualTo(512L);
              assertThat(agent.getMetrics().getOutputTokens()).isEqualTo(128L);
              assertThat(agent.getMetrics().getModelCalls()).isEqualTo(1L);
              assertThat(agent.getMetrics().getToolCalls()).isEqualTo(2L);

              assertThat(pi.getAgentTotalInputTokens()).isEqualTo(512L);
              assertThat(pi.getAgentTotalOutputTokens()).isEqualTo(128L);
              assertThat(pi.getAgentTotalModelCalls()).isEqualTo(1L);
              assertThat(pi.getAgentTotalToolCalls()).isEqualTo(2L);
              assertThat(pi.getAgentTotalTokens()).isEqualTo(640L);
            });
  }

  @Test
  void shouldImportCompletedAgentInstance_setsDurationAndEndDate() {
    // given
    final long agentKey = keyCounter.getAndIncrement();
    seedRecord(
        agentKey, AgentInstanceIntent.CREATED, AgentInstanceStatus.INITIALIZING, 0L, 0L, 0, 0);
    seedRecord(
        agentKey, AgentInstanceIntent.UPDATED, AgentInstanceStatus.THINKING, 500L, 200L, 2, 3);
    seedRecord(
        agentKey, AgentInstanceIntent.COMPLETED, AgentInstanceStatus.COMPLETED, 800L, 300L, 3, 4);

    // when
    importAllZeebeEntitiesFromScratch();

    // then
    assertThat(databaseIntegrationTestExtension.getAllProcessInstances())
        .singleElement()
        .satisfies(
            pi -> {
              assertThat(pi.getAgentInstances()).hasSize(1);
              final AgentInstanceDto agent = pi.getAgentInstances().get(0);
              assertThat(agent.getStatus()).isEqualTo(AgentInstanceStatus.COMPLETED.name());
              assertThat(agent.getEndDate()).isNotNull();
              // CREATED seeded at position 1 (timestamp +1000ms), COMPLETED at position 3
              // (+3000ms); see seedRecord -> setTimestamp(now + position * 1000).
              assertThat(agent.getTotalDurationInMs()).isEqualTo(2000L);
              // Metrics reflect final cumulative totals from the COMPLETED event
              assertThat(agent.getMetrics().getInputTokens()).isEqualTo(800L);
              assertThat(agent.getMetrics().getOutputTokens()).isEqualTo(300L);
              assertThat(agent.getMetrics().getModelCalls()).isEqualTo(3L);
              assertThat(agent.getMetrics().getToolCalls()).isEqualTo(4L);
            });
  }

  @Test
  void shouldImportMultipleAgentInstancesForSameProcessInstance() {
    // given: two distinct agent instances on the same process instance
    final long agentKey1 = keyCounter.getAndIncrement();
    final long agentKey2 = keyCounter.getAndIncrement();
    seedRecord(
        agentKey1, AgentInstanceIntent.CREATED, AgentInstanceStatus.INITIALIZING, 0L, 0L, 0, 0);
    seedRecord(
        agentKey2, AgentInstanceIntent.CREATED, AgentInstanceStatus.INITIALIZING, 0L, 0L, 0, 0);

    // when
    importAllZeebeEntitiesFromScratch();

    // then
    assertThat(databaseIntegrationTestExtension.getAllProcessInstances())
        .singleElement()
        .satisfies(
            pi -> {
              assertThat(pi.getProcessInstanceId()).isEqualTo(String.valueOf(PROCESS_INSTANCE_KEY));
              assertThat(pi.getAgentInstances()).hasSize(2);
            });
  }

  @Test
  void shouldHandleNullMetricsFromZeebeRecord() {
    // given: Zeebe exports AGENT_INSTANCE record with metrics=null
    final long agentKey = keyCounter.getAndIncrement();
    seedRecord(agentKey, AgentInstanceIntent.CREATED, AgentInstanceStatus.INITIALIZING, null, null);

    // when
    importAllZeebeEntitiesFromScratch();

    // then: import succeeds, metrics are null (mapMetrics leaves metrics unset when source has
    // no metrics)
    assertThat(databaseIntegrationTestExtension.getAllProcessInstances())
        .singleElement()
        .satisfies(
            pi -> {
              assertThat(pi.getProcessInstanceId()).isEqualTo(String.valueOf(PROCESS_INSTANCE_KEY));
              assertThat(pi.getAgentInstances()).hasSize(1);
              final AgentInstanceDto agent = pi.getAgentInstances().get(0);
              assertThat(agent.getAgentInstanceId()).isEqualTo(String.valueOf(agentKey));
              // When source metrics is absent, mapMetrics() does not populate the DTO, so the
              // ES document has no metrics object rather than a misleading zero-value one.
              assertThat(agent.getMetrics()).isNull();
              // Aggregates should be zero when metrics is null
              assertThat(pi.getAgentTotalInputTokens()).isZero();
              assertThat(pi.getAgentTotalOutputTokens()).isZero();
              assertThat(pi.getAgentTotalModelCalls()).isZero();
              assertThat(pi.getAgentTotalToolCalls()).isZero();
              assertThat(pi.getAgentTotalTokens()).isZero();
            });
  }

  @Test
  void shouldHandleNullDefinitionFromZeebeRecord() {
    // given: Zeebe exports AGENT_INSTANCE record with definition=null
    final long agentKey = keyCounter.getAndIncrement();
    seedRecord(
        PROCESS_INSTANCE_KEY,
        agentKey,
        AgentInstanceIntent.CREATED,
        AgentInstanceStatus.INITIALIZING,
        null,
        null,
        List.of(),
        List.of(),
        RecordType.EVENT);

    // when: import must not throw NPE inside mapDefinition()
    importAllZeebeEntitiesFromScratch();

    // then: import succeeds and definition is stored as null (not an empty object)
    assertThat(databaseIntegrationTestExtension.getAllProcessInstances())
        .singleElement()
        .satisfies(
            pi -> {
              assertThat(pi.getAgentInstances()).hasSize(1);
              final AgentInstanceDto agent = pi.getAgentInstances().get(0);
              assertThat(agent.getAgentInstanceId()).isEqualTo(String.valueOf(agentKey));
              assertThat(agent.getDefinition())
                  .as("definition must be null when absent from Zeebe record")
                  .isNull();
            });
  }

  @Test
  void shouldPopulateFlowNodeInstanceIdsFromElementInstanceKeys() {
    // given: Agent with elementInstanceKeys representing migration across flow nodes
    final long agentKey = keyCounter.getAndIncrement();
    final List<Long> migrationHistory = List.of(1001L, 1002L, 1003L);

    seedRecord(
        agentKey,
        AgentInstanceIntent.UPDATED,
        AgentInstanceStatus.THINKING,
        metrics(100L, 50L, 1, 2),
        migrationHistory);

    // when
    importAllZeebeEntitiesFromScratch();

    // then: import maps Zeebe's elementInstanceKey -> Optimize's flowNodeInstanceId, and
    // elementInstanceKeys -> flowNodeInstanceIds, both stringified.
    assertThat(databaseIntegrationTestExtension.getAllProcessInstances())
        .singleElement()
        .satisfies(
            pi -> {
              assertThat(pi.getAgentInstances()).hasSize(1);
              final AgentInstanceDto agent = pi.getAgentInstances().get(0);
              assertThat(agent.getFlowNodeInstanceId()).isEqualTo("1003");
              assertThat(agent.getFlowNodeInstanceIds()).containsExactly("1001", "1002", "1003");
            });
  }

  @Test
  void shouldMergeAgentInstanceAcrossImportBatches() {
    // Verifies the full pipeline (import service → Painless merge script) across two separate
    // import rounds, simulating records arriving in different Zeebe broker export batches.
    final long agentKey = keyCounter.getAndIncrement();

    // given: batch 1 — CREATED record imported on its own
    seedRecord(
        agentKey, AgentInstanceIntent.CREATED, AgentInstanceStatus.INITIALIZING, 0L, 0L, 0, 0);
    importAllZeebeEntitiesFromScratch();

    // Intermediate assertion: doc exists with initial state before the second batch arrives
    assertThat(databaseIntegrationTestExtension.getAllProcessInstances())
        .singleElement()
        .satisfies(
            pi -> {
              assertThat(pi.getAgentInstances()).hasSize(1);
              assertThat(pi.getAgentInstances().get(0).getStatus())
                  .isEqualTo(AgentInstanceStatus.INITIALIZING.name());
              assertThat(pi.getAgentTotalInputTokens()).isZero();
            });

    // when: batch 2 — UPDATED record arrives in a new import round
    seedRecord(
        agentKey, AgentInstanceIntent.UPDATED, AgentInstanceStatus.THINKING, 512L, 128L, 1, 2);
    importAllZeebeEntitiesFromLastIndex();

    // then: Painless script merges the UPDATED record into the existing doc
    assertThat(databaseIntegrationTestExtension.getAllProcessInstances())
        .singleElement()
        .satisfies(
            pi -> {
              assertThat(pi.getProcessInstanceId()).isEqualTo(String.valueOf(PROCESS_INSTANCE_KEY));
              assertThat(pi.getAgentInstances()).hasSize(1);
              final AgentInstanceDto agent = pi.getAgentInstances().get(0);
              assertThat(agent.getStatus()).isEqualTo(AgentInstanceStatus.THINKING.name());
              assertThat(agent.getMetrics().getInputTokens()).isEqualTo(512L);
              assertThat(agent.getMetrics().getOutputTokens()).isEqualTo(128L);
              assertThat(agent.getMetrics().getModelCalls()).isEqualTo(1L);
              assertThat(agent.getMetrics().getToolCalls()).isEqualTo(2L);
              assertThat(pi.getAgentTotalInputTokens()).isEqualTo(512L);
              assertThat(pi.getAgentTotalOutputTokens()).isEqualTo(128L);
              assertThat(pi.getAgentTotalModelCalls()).isEqualTo(1L);
              assertThat(pi.getAgentTotalToolCalls()).isEqualTo(2L);
              assertThat(pi.getAgentTotalTokens()).isEqualTo(640L);
            });
  }

  @Test
  void shouldCompleteAgentInstanceAcrossThreeImportBatches() {
    // Verifies the full CREATED → UPDATED → COMPLETED lifecycle when each event arrives in its own
    // separate import round, exercising the Painless merge script at every transition.
    final long agentKey = keyCounter.getAndIncrement();

    // given: batch 1 — CREATED
    seedRecord(
        agentKey, AgentInstanceIntent.CREATED, AgentInstanceStatus.INITIALIZING, 0L, 0L, 0, 0);
    importAllZeebeEntitiesFromScratch();

    assertThat(databaseIntegrationTestExtension.getAllProcessInstances())
        .singleElement()
        .satisfies(
            pi -> {
              final AgentInstanceDto agent = pi.getAgentInstances().get(0);
              assertThat(agent.getStatus()).isEqualTo(AgentInstanceStatus.INITIALIZING.name());
              assertThat(agent.getStartDate()).isNotNull();
              assertThat(agent.getEndDate()).isNull();
              assertThat(agent.getTotalDurationInMs()).isNull();
              assertThat(pi.getAgentTotalInputTokens()).isZero();
            });

    // when: batch 2 — UPDATED
    seedRecord(
        agentKey, AgentInstanceIntent.UPDATED, AgentInstanceStatus.THINKING, 500L, 200L, 2, 3);
    importAllZeebeEntitiesFromLastIndex();

    assertThat(databaseIntegrationTestExtension.getAllProcessInstances())
        .singleElement()
        .satisfies(
            pi -> {
              final AgentInstanceDto agent = pi.getAgentInstances().get(0);
              assertThat(agent.getStatus()).isEqualTo(AgentInstanceStatus.THINKING.name());
              assertThat(agent.getEndDate()).isNull();
              assertThat(agent.getTotalDurationInMs()).isNull();
              assertThat(agent.getMetrics().getInputTokens()).isEqualTo(500L);
              assertThat(pi.getAgentTotalInputTokens()).isEqualTo(500L);
            });

    // when: batch 3 — COMPLETED
    seedRecord(
        agentKey, AgentInstanceIntent.COMPLETED, AgentInstanceStatus.COMPLETED, 800L, 300L, 3, 4);
    importAllZeebeEntitiesFromLastIndex();

    // then: COMPLETED merged — endDate set, duration correct, final metrics
    assertThat(databaseIntegrationTestExtension.getAllProcessInstances())
        .singleElement()
        .satisfies(
            pi -> {
              assertThat(pi.getAgentInstances()).hasSize(1);
              final AgentInstanceDto agent = pi.getAgentInstances().get(0);
              assertThat(agent.getStatus()).isEqualTo(AgentInstanceStatus.COMPLETED.name());
              assertThat(agent.getEndDate()).isNotNull();
              // CREATED at position 1 (BASE+1000ms), COMPLETED at position 3 (BASE+3000ms)
              assertThat(agent.getTotalDurationInMs()).isEqualTo(2000L);
              assertThat(agent.getMetrics().getInputTokens()).isEqualTo(800L);
              assertThat(agent.getMetrics().getOutputTokens()).isEqualTo(300L);
              assertThat(agent.getMetrics().getModelCalls()).isEqualTo(3L);
              assertThat(agent.getMetrics().getToolCalls()).isEqualTo(4L);
              assertThat(pi.getAgentTotalInputTokens()).isEqualTo(800L);
              assertThat(pi.getAgentTotalOutputTokens()).isEqualTo(300L);
              assertThat(pi.getAgentTotalModelCalls()).isEqualTo(3L);
              assertThat(pi.getAgentTotalToolCalls()).isEqualTo(4L);
              assertThat(pi.getAgentTotalTokens()).isEqualTo(1100L);
            });
  }

  @Test
  void shouldPreserveMetricsWhenSubsequentUpdateHasNoMetrics() {
    // Verifies that a later UPDATED record with null metrics does NOT overwrite metrics that were
    // established by an earlier UPDATED record. This relies on AgentInstanceDto.metrics being
    // nullable: the Painless upsert script only skips the metrics write when newAgent.metrics IS
    // null, so a non-null zero-value default would pass the guard and clobber real data.
    final long agentKey = keyCounter.getAndIncrement();

    // given: batch 1 — CREATED
    seedRecord(
        agentKey, AgentInstanceIntent.CREATED, AgentInstanceStatus.INITIALIZING, 0L, 0L, 0, 0);
    importAllZeebeEntitiesFromScratch();

    // when: batch 2 — UPDATED with real metrics
    seedRecord(
        agentKey, AgentInstanceIntent.UPDATED, AgentInstanceStatus.THINKING, 512L, 128L, 1, 2);
    importAllZeebeEntitiesFromLastIndex();

    // when: batch 3 — UPDATED with null metrics (e.g. partial record from Zeebe)
    seedRecord(agentKey, AgentInstanceIntent.UPDATED, AgentInstanceStatus.IDLE, null, null);
    importAllZeebeEntitiesFromLastIndex();

    // then: metrics from batch 2 are preserved, not overwritten by the null-metrics record
    assertThat(databaseIntegrationTestExtension.getAllProcessInstances())
        .singleElement()
        .satisfies(
            pi -> {
              assertThat(pi.getAgentInstances()).hasSize(1);
              final AgentInstanceDto agent = pi.getAgentInstances().get(0);
              assertThat(agent.getStatus()).isEqualTo(AgentInstanceStatus.IDLE.name());
              assertThat(agent.getMetrics()).isNotNull();
              assertThat(agent.getMetrics().getInputTokens()).isEqualTo(512L);
              assertThat(agent.getMetrics().getOutputTokens()).isEqualTo(128L);
              assertThat(agent.getMetrics().getModelCalls()).isEqualTo(1L);
              assertThat(agent.getMetrics().getToolCalls()).isEqualTo(2L);
              assertThat(pi.getAgentTotalInputTokens()).isEqualTo(512L);
              assertThat(pi.getAgentTotalOutputTokens()).isEqualTo(128L);
              assertThat(pi.getAgentTotalTokens()).isEqualTo(640L);
            });
  }

  @Test
  void shouldPropagateToolsThroughFullPipeline() {
    // Verifies that tools seeded in the Zeebe record are correctly mapped by the import service
    // (mapTools) and persisted in the resulting AgentInstanceDto.
    final long agentKey = keyCounter.getAndIncrement();
    final List<AgentToolValueDto> tools = List.of(tool("search_web"), tool("code_executor"));
    seedRecord(
        PROCESS_INSTANCE_KEY,
        agentKey,
        AgentInstanceIntent.CREATED,
        AgentInstanceStatus.INITIALIZING,
        metrics(0L, 0L, 0, 2),
        defaultDefinition(),
        List.of(),
        tools,
        RecordType.EVENT);

    // when
    importAllZeebeEntitiesFromScratch();

    // then: both tools propagated through the full import pipeline
    assertThat(databaseIntegrationTestExtension.getAllProcessInstances())
        .singleElement()
        .satisfies(
            pi -> {
              assertThat(pi.getAgentInstances()).hasSize(1);
              final AgentInstanceDto agent = pi.getAgentInstances().get(0);
              assertThat(agent.getTools())
                  .extracting(AgentInstanceDto.AgentToolDto::getName)
                  .containsExactlyInAnyOrder("search_web", "code_executor");
            });
  }

  @Test
  void shouldImportAgentInstancesForMultipleProcessInstancesInSameBatch() {
    // Verifies the groupingBy(processInstanceKey) path: records belonging to two different process
    // instances that arrive in the same import batch produce two separate ProcessInstance
    // documents.
    final long agentKey1 = keyCounter.getAndIncrement();
    final long agentKey2 = keyCounter.getAndIncrement();
    final long secondProcessInstanceKey = 200L;

    seedRecord(
        PROCESS_INSTANCE_KEY,
        agentKey1,
        AgentInstanceIntent.CREATED,
        AgentInstanceStatus.INITIALIZING,
        metrics(100L, 50L, 1, 0),
        defaultDefinition(),
        List.of(),
        List.of(),
        RecordType.EVENT);
    seedRecord(
        secondProcessInstanceKey,
        agentKey2,
        AgentInstanceIntent.CREATED,
        AgentInstanceStatus.INITIALIZING,
        metrics(200L, 75L, 1, 0),
        defaultDefinition(),
        List.of(),
        List.of(),
        RecordType.EVENT);

    // when
    importAllZeebeEntitiesFromScratch();

    // then: two separate ProcessInstance documents, one per PI key
    assertThat(databaseIntegrationTestExtension.getAllProcessInstances())
        .hasSize(2)
        .extracting(pi -> pi.getProcessInstanceId())
        .containsExactlyInAnyOrder(
            String.valueOf(PROCESS_INSTANCE_KEY), String.valueOf(secondProcessInstanceKey));
  }

  @Test
  void shouldIgnoreCommandIntentRecords() {
    // Verifies that COMMAND records (CREATE/UPDATE/COMPLETE intents) are filtered out by the import
    // service before any processing. Only EVENT records (CREATED/UPDATED/COMPLETED) are imported.
    final long agentKey = keyCounter.getAndIncrement();
    seedRecord(
        PROCESS_INSTANCE_KEY,
        agentKey,
        AgentInstanceIntent.CREATE,
        AgentInstanceStatus.UNSPECIFIED,
        null,
        defaultDefinition(),
        List.of(),
        List.of(),
        RecordType.COMMAND);

    // when
    importAllZeebeEntitiesFromScratch();

    // then: command records are excluded; no documents created
    assertThat(databaseIntegrationTestExtension.getAllProcessInstances()).isEmpty();
  }

  @Test
  void shouldPersistLastUpdatedDate() {
    // Verifies that lastUpdatedDate is set on the initial CREATED record and then advances to a
    // later value when a subsequent UPDATED record arrives in a separate batch. lastUpdatedDate is
    // critical: the Painless script uses it as a timestamp guard to prevent out-of-order records
    // from overwriting newer state.
    final long agentKey = keyCounter.getAndIncrement();

    // given: batch 1 — CREATED
    seedRecord(
        agentKey, AgentInstanceIntent.CREATED, AgentInstanceStatus.INITIALIZING, 0L, 0L, 0, 0);
    importAllZeebeEntitiesFromScratch();

    // capture the date after the first batch
    final OffsetDateTime[] createdDate = new OffsetDateTime[1];
    assertThat(databaseIntegrationTestExtension.getAllProcessInstances())
        .singleElement()
        .satisfies(
            pi -> {
              final AgentInstanceDto agent = pi.getAgentInstances().get(0);
              assertThat(agent.getLastUpdatedDate())
                  .as("lastUpdatedDate must be set after CREATED import")
                  .isNotNull();
              createdDate[0] = agent.getLastUpdatedDate();
            });

    // when: batch 2 — UPDATED with a later timestamp (positionCounter advances → timestamp is +1s)
    seedRecord(
        agentKey, AgentInstanceIntent.UPDATED, AgentInstanceStatus.THINKING, 100L, 50L, 1, 0);
    importAllZeebeEntitiesFromLastIndex();

    // then: lastUpdatedDate advanced to the UPDATED record's timestamp
    assertThat(databaseIntegrationTestExtension.getAllProcessInstances())
        .singleElement()
        .satisfies(
            pi -> {
              final AgentInstanceDto agent = pi.getAgentInstances().get(0);
              assertThat(agent.getLastUpdatedDate())
                  .as("lastUpdatedDate must advance after UPDATED import")
                  .isNotNull()
                  .isAfter(createdDate[0]);
            });
  }

  @Test
  void shouldPreserveDefinitionAcrossCrossBatchUpdate() {
    // Verifies that agent definition (model + provider) set on CREATED is not lost when an UPDATED
    // record arrives in a separate batch. The Painless script is "set-once" for definition:
    // it only writes definition if the existing value is null, so it must survive cross-batch
    // updates without being wiped.
    final long agentKey = keyCounter.getAndIncrement();

    // given: batch 1 — CREATED, definition is set
    seedRecord(
        agentKey, AgentInstanceIntent.CREATED, AgentInstanceStatus.INITIALIZING, 0L, 0L, 0, 0);
    importAllZeebeEntitiesFromScratch();

    // when: batch 2 — UPDATED (definition is always present in seeded records; script guards it)
    seedRecord(
        agentKey, AgentInstanceIntent.UPDATED, AgentInstanceStatus.THINKING, 100L, 50L, 1, 0);
    importAllZeebeEntitiesFromLastIndex();

    // then: definition fields from CREATED are still present
    assertThat(databaseIntegrationTestExtension.getAllProcessInstances())
        .singleElement()
        .satisfies(
            pi -> {
              final AgentInstanceDto agent = pi.getAgentInstances().get(0);
              assertThat(agent.getDefinition())
                  .as("definition must not be null after cross-batch update")
                  .isNotNull();
              assertThat(agent.getDefinition().getModel())
                  .as("definition.model must survive cross-batch update")
                  .isEqualTo(MODEL);
              assertThat(agent.getDefinition().getProvider())
                  .as("definition.provider must survive cross-batch update")
                  .isEqualTo(PROVIDER);
            });
  }

  // --- helpers ---

  /**
   * Convenience overload: primitive metrics, default processInstanceKey, no tools, no custom
   * elementInstanceKeys.
   */
  private void seedRecord(
      final long agentKey,
      final AgentInstanceIntent intent,
      final AgentInstanceStatus status,
      final long inputTokens,
      final long outputTokens,
      final int modelCalls,
      final int toolCalls) {
    seedRecord(
        PROCESS_INSTANCE_KEY,
        agentKey,
        intent,
        status,
        metrics(inputTokens, outputTokens, modelCalls, toolCalls),
        defaultDefinition(),
        List.of(),
        List.of(),
        RecordType.EVENT);
  }

  /**
   * Convenience overload: explicit metrics and elementInstanceKeys, default processInstanceKey, no
   * tools.
   *
   * @param metrics nullable; pass {@code null} to exercise the null-metrics import path
   * @param elementInstanceKeys nullable/empty; falls back to a single default key derived from
   *     {@code agentKey}
   */
  private void seedRecord(
      final long agentKey,
      final AgentInstanceIntent intent,
      final AgentInstanceStatus status,
      final AgentMetricsValueDto metrics,
      final List<Long> elementInstanceKeys) {
    seedRecord(
        PROCESS_INSTANCE_KEY,
        agentKey,
        intent,
        status,
        metrics,
        defaultDefinition(),
        elementInstanceKeys,
        List.of(),
        RecordType.EVENT);
  }

  /**
   * Full record builder: supports custom processInstanceKey, tools list, and RecordType.
   *
   * @param metrics nullable; pass {@code null} to exercise the null-metrics import path
   * @param definition nullable; pass {@code null} to exercise the null-definition import path
   * @param elementInstanceKeys nullable/empty; falls back to a single default key derived from
   *     {@code agentKey}
   * @param tools empty list for no tools; non-empty to set tools on the record
   * @param recordType use {@link RecordType#EVENT} for normal import, {@link RecordType#COMMAND} to
   *     test command filtering
   */
  private void seedRecord(
      final long processInstanceKey,
      final long agentKey,
      final AgentInstanceIntent intent,
      final AgentInstanceStatus status,
      final AgentMetricsValueDto metrics,
      final AgentDefinitionValueDto definition,
      final List<Long> elementInstanceKeys,
      final List<AgentToolValueDto> tools,
      final RecordType recordType) {

    final List<Long> resolvedKeys =
        (elementInstanceKeys == null || elementInstanceKeys.isEmpty())
            ? List.of(agentKey + 1000)
            : elementInstanceKeys;
    final long currentElementInstanceKey = resolvedKeys.get(resolvedKeys.size() - 1);

    final ZeebeAgentInstanceDataDto value = new ZeebeAgentInstanceDataDto();
    value.setAgentInstanceKey(agentKey);
    value.setElementInstanceKey(currentElementInstanceKey);
    value.setElementInstanceKeys(resolvedKeys);
    value.setElementId(ELEMENT_ID);
    value.setProcessInstanceKey(processInstanceKey);
    value.setBpmnProcessId(BPMN_PROCESS_ID);
    value.setProcessDefinitionKey(PROCESS_DEFINITION_KEY);
    value.setProcessDefinitionVersion(1);
    value.setStatus(status);

    value.setDefinition(definition);

    value.setMetrics(metrics);
    if (!tools.isEmpty()) {
      value.setTools(tools);
    }

    final long position = positionCounter.getAndIncrement();
    final ZeebeAgentInstanceRecordDto record = new ZeebeAgentInstanceRecordDto();
    record.setPosition(position);
    record.setKey(agentKey);
    record.setPartitionId(1);
    record.setTimestamp(BASE_TIMESTAMP_MS + position * 1000);
    record.setRecordType(recordType);
    record.setValueType(ValueType.AGENT_INSTANCE);
    record.setIntent(intent);
    record.setValue(value);

    databaseIntegrationTestExtension.addEntryWithRawIndex(
        agentInstanceIndex, String.valueOf(position), record);
  }

  private AgentToolValueDto tool(final String name) {
    final AgentToolValueDto t = new AgentToolValueDto();
    t.setName(name);
    return t;
  }

  private AgentDefinitionValueDto defaultDefinition() {
    final AgentDefinitionValueDto def = new AgentDefinitionValueDto();
    def.setModel(MODEL);
    def.setProvider(PROVIDER);
    def.setSystemPrompt(SYSTEM_PROMPT);
    return def;
  }

  private AgentMetricsValueDto metrics(
      final long inputTokens, final long outputTokens, final int modelCalls, final int toolCalls) {
    final AgentMetricsValueDto m = new AgentMetricsValueDto();
    m.setInputTokens(inputTokens);
    m.setOutputTokens(outputTokens);
    m.setModelCalls(modelCalls);
    m.setToolCalls(toolCalls);
    return m;
  }
}
