/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.gateway.mapping.http.validator.AgentInstanceRequestValidator;
import io.camunda.gateway.protocol.model.AgentInstanceCreationRequest;
import io.camunda.gateway.protocol.model.AgentInstanceHistoryItem;
import io.camunda.gateway.protocol.model.AgentInstanceHistoryItemMetricsRequest;
import io.camunda.gateway.protocol.model.AgentInstanceHistoryRoleEnum;
import io.camunda.gateway.protocol.model.AgentInstanceLimits;
import io.camunda.gateway.protocol.model.AgentInstanceMessageContent;
import io.camunda.gateway.protocol.model.AgentInstanceTextContent;
import io.camunda.gateway.protocol.model.AgentInstanceToolCall;
import io.camunda.gateway.protocol.model.AgentInstanceUpdateRequest;
import io.camunda.gateway.protocol.model.AgentInstanceUpdateStatusEnum;
import io.camunda.gateway.protocol.model.AgentTool;
import io.camunda.zeebe.protocol.impl.record.value.agenthistory.AgentHistoryRecord;
import io.camunda.zeebe.protocol.impl.record.value.agentinstance.AgentInstanceRecord;
import io.camunda.zeebe.util.Either;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;

class AgentInstanceMapperTest {

  private static final String AGENT_INSTANCE_KEY = "9007199254741017";
  private static final String ELEMENT_INSTANCE_KEY = "2251799813685248";
  private static final String JOB_KEY = "2251799813685249";
  private static final String JOB_LEASE = "lease-token-1";

  private final AgentInstanceMapper mapper =
      new AgentInstanceMapper(new AgentInstanceRequestValidator());

  private static AgentInstanceHistoryItem historyItem(
      final String historyItemId, final AgentInstanceHistoryRoleEnum role, final String text) {
    return historyItem(historyItemId, 1, role, text);
  }

  private static AgentInstanceHistoryItem historyItem(
      final String historyItemId,
      final int loopIteration,
      final AgentInstanceHistoryRoleEnum role,
      final String text) {
    return AgentInstanceHistoryItem.Builder.create()
        .historyItemId(historyItemId)
        .loopIteration(loopIteration)
        .role(role)
        .content(List.of(textContent(text)))
        .producedAt("2025-06-01T12:00:00Z")
        .build();
  }

  private static AgentInstanceMessageContent textContent(final String text) {
    return AgentInstanceTextContent.Builder.create().contentType("TEXT").text(text).build();
  }

  @Nested
  class ExistingUpdateFieldMappingTest {

    @Test
    void shouldMapStatusOntoRecord() {
      // given
      final var request =
          AgentInstanceUpdateRequest.Builder.create()
              .elementInstanceKey(ELEMENT_INSTANCE_KEY)
              .jobKey(JOB_KEY)
              .jobLease(JOB_LEASE)
              .build();
      request.setStatus(AgentInstanceUpdateStatusEnum.THINKING);

      // when
      final Either<ProblemDetail, AgentInstanceRecord> result =
          mapper.toUpdateAgentInstanceRecord(AGENT_INSTANCE_KEY, request);

      // then
      assertThat(result.isRight()).isTrue();
      final var record = result.get();
      assertThat(record.getStatus().name()).isEqualTo("THINKING");
      assertThat(record.getChangedAttributes()).containsExactly("status");
    }
  }

  @Nested
  class HistoryBatchMappingTest {

    @Test
    void shouldMapHistoryBatchOntoRecordPreservingOrderAndFields() {
      // given
      final var request =
          AgentInstanceUpdateRequest.Builder.create()
              .elementInstanceKey(ELEMENT_INSTANCE_KEY)
              .jobKey(JOB_KEY)
              .jobLease(JOB_LEASE)
              .build();
      request.setHistory(
          List.of(
              historyItem("item-1", AgentInstanceHistoryRoleEnum.USER, "hello"),
              historyItem("item-2", AgentInstanceHistoryRoleEnum.ASSISTANT, "hi there")));

      // when
      final Either<ProblemDetail, AgentInstanceRecord> result =
          mapper.toUpdateAgentInstanceRecord(AGENT_INSTANCE_KEY, request);

      // then
      assertThat(result.isRight()).isTrue();
      final var record = result.get();
      assertThat(record.getHistory()).hasSize(2);
      assertThat(record.getHistory().get(0).getHistoryItemId()).isEqualTo("item-1");
      assertThat(record.getHistory().get(0).getRole().name()).isEqualTo("USER");
      assertThat(record.getHistory().get(0).getContent()).hasSize(1);
      assertThat(record.getHistory().get(0).getContent().get(0).getText()).isEqualTo("hello");
      assertThat(record.getHistory().get(1).getHistoryItemId()).isEqualTo("item-2");
      assertThat(record.getHistory().get(1).getRole().name()).isEqualTo("ASSISTANT");
    }

    @Test
    void shouldMapHistoryItemToolCallsAndMetricsOntoRecord() {
      // given
      final var request =
          AgentInstanceUpdateRequest.Builder.create()
              .elementInstanceKey(ELEMENT_INSTANCE_KEY)
              .jobKey(JOB_KEY)
              .jobLease(JOB_LEASE)
              .build();
      final var toolCall =
          AgentInstanceToolCall.Builder.create()
              .toolCallId("tc-001")
              .toolName("extract_data")
              .elementId("extract-task")
              .arguments(null)
              .build();
      final var item =
          AgentInstanceHistoryItem.Builder.create()
              .historyItemId("item-1")
              .loopIteration(1)
              .role(AgentInstanceHistoryRoleEnum.ASSISTANT)
              .content(List.of(textContent("computing")))
              .producedAt("2025-06-01T12:00:00Z")
              .build();
      item.setToolCalls(List.of(toolCall));
      item.setMetrics(
          AgentInstanceHistoryItemMetricsRequest.Builder.create()
              .inputTokens(512L)
              .outputTokens(128L)
              .reasoningTokenCount(64L)
              .cacheCreationTokenCount(32L)
              .cacheReadTokenCount(16L)
              .durationMs(1500L)
              .build());
      request.setHistory(List.of(item));

      // when
      final Either<ProblemDetail, AgentInstanceRecord> result =
          mapper.toUpdateAgentInstanceRecord(AGENT_INSTANCE_KEY, request);

      // then
      assertThat(result.isRight()).isTrue();
      final var mappedItem = result.get().getHistory().get(0);
      assertThat(mappedItem.getToolCalls()).hasSize(1);
      assertThat(mappedItem.getToolCalls().get(0).getToolCallId()).isEqualTo("tc-001");
      assertThat(mappedItem.getToolCalls().get(0).getToolName()).isEqualTo("extract_data");
      assertThat(mappedItem.getMetrics().getInputTokens()).isEqualTo(512L);
      assertThat(mappedItem.getMetrics().getOutputTokens()).isEqualTo(128L);
      assertThat(mappedItem.getMetrics().getReasoningTokenCount()).isEqualTo(64L);
      assertThat(mappedItem.getMetrics().getCacheCreationTokenCount()).isEqualTo(32L);
      assertThat(mappedItem.getMetrics().getCacheReadTokenCount()).isEqualTo(16L);
      assertThat(mappedItem.getMetrics().getDurationMs()).isEqualTo(1500L);
      assertThat(mappedItem.getProducedAt())
          .isEqualTo(OffsetDateTime.parse("2025-06-01T12:00:00Z").toInstant().toEpochMilli());
    }

    @Test
    void shouldMapRequestLevelJobKeyAndJobLeaseOntoRecord() {
      // given
      final var request =
          AgentInstanceUpdateRequest.Builder.create()
              .elementInstanceKey(ELEMENT_INSTANCE_KEY)
              .jobKey(JOB_KEY)
              .jobLease("lease-abc")
              .build();
      request.setHistory(List.of(historyItem("item-1", AgentInstanceHistoryRoleEnum.USER, "hi")));

      // when
      final Either<ProblemDetail, AgentInstanceRecord> result =
          mapper.toUpdateAgentInstanceRecord(AGENT_INSTANCE_KEY, request);

      // then
      assertThat(result.isRight()).isTrue();
      final var record = result.get();
      assertThat(record.getJobKey()).isEqualTo(2251799813685249L);
      assertThat(record.getJobLease()).isEqualTo("lease-abc");
    }

    @Test
    void shouldMapConfigurationFieldsOntoHistoryRecord() {
      // given
      final var request =
          AgentInstanceUpdateRequest.Builder.create()
              .elementInstanceKey(ELEMENT_INSTANCE_KEY)
              .jobKey(JOB_KEY)
              .jobLease(JOB_LEASE)
              .build();
      final var item =
          AgentInstanceHistoryItem.Builder.create()
              .historyItemId("item-1")
              .loopIteration(1)
              .role(AgentInstanceHistoryRoleEnum.CONFIGURATION)
              .content(List.of(textContent("switching model")))
              .producedAt("2025-06-01T12:00:00Z")
              .build();
      item.tools(
          List.of(
              AgentTool.Builder.create()
                  .name("searchDatabase")
                  .description("Searches the database")
                  .elementId(null)
                  .build()));
      item.model("gpt-5");
      item.provider("openai");
      item.limits(
          AgentInstanceLimits.Builder.create()
              .maxModelCalls(10)
              .maxTokens(1000L)
              .maxToolCalls(5)
              .build());
      item.systemPrompt(List.of(textContent("be helpful")));
      request.setHistory(List.of(item));

      // when
      final Either<ProblemDetail, AgentInstanceRecord> result =
          mapper.toUpdateAgentInstanceRecord(AGENT_INSTANCE_KEY, request);

      // then
      assertThat(result.isRight()).isTrue();
      final var mappedItem = result.get().getHistory().get(0);
      assertThat(mappedItem.getTools()).hasSize(1);
      assertThat(mappedItem.getTools().get(0).getName()).isEqualTo("searchDatabase");
      assertThat(mappedItem.getModel()).isEqualTo("gpt-5");
      assertThat(mappedItem.getProvider()).isEqualTo("openai");
      assertThat(mappedItem.getLimits().getMaxModelCalls()).isEqualTo(10);
      assertThat(mappedItem.getLimits().getMaxTokens()).isEqualTo(1000L);
      assertThat(mappedItem.getLimits().getMaxToolCalls()).isEqualTo(5);
      assertThat(mappedItem.getSystemPrompt()).hasSize(1);
      assertThat(mappedItem.getSystemPrompt().get(0).getText()).isEqualTo("be helpful");
      assertThat(mappedItem.getChangedAttributes())
          .containsExactlyInAnyOrder(
              "tools",
              "model",
              "provider",
              "maxTokens",
              "maxModelCalls",
              "maxToolCalls",
              "systemPrompt");
    }

    @Test
    void shouldLeaveConfigurationFieldsAtDefaultsWhenAbsentFromHistoryItem() {
      // given
      final var request =
          AgentInstanceUpdateRequest.Builder.create()
              .elementInstanceKey(ELEMENT_INSTANCE_KEY)
              .jobKey(JOB_KEY)
              .jobLease(JOB_LEASE)
              .build();
      request.setHistory(List.of(historyItem("item-1", AgentInstanceHistoryRoleEnum.USER, "hi")));

      // when
      final Either<ProblemDetail, AgentInstanceRecord> result =
          mapper.toUpdateAgentInstanceRecord(AGENT_INSTANCE_KEY, request);

      // then
      assertThat(result.isRight()).isTrue();
      final var mappedItem = result.get().getHistory().get(0);
      assertThat(mappedItem.getTools()).isEmpty();
      assertThat(mappedItem.getModel()).isEmpty();
      assertThat(mappedItem.getProvider()).isEmpty();
      assertThat(mappedItem.getLimits().getMaxTokens()).isEqualTo(-1L);
      assertThat(mappedItem.getLimits().getMaxModelCalls()).isEqualTo(-1);
      assertThat(mappedItem.getLimits().getMaxToolCalls()).isEqualTo(-1);
      assertThat(mappedItem.getSystemPrompt()).isEmpty();
      assertThat(mappedItem.getChangedAttributes()).isEmpty();
    }

    @Test
    void shouldTreatExplicitEmptyToolsAsProvided() {
      // given
      final var request =
          AgentInstanceUpdateRequest.Builder.create()
              .elementInstanceKey(ELEMENT_INSTANCE_KEY)
              .jobKey(JOB_KEY)
              .jobLease(JOB_LEASE)
              .build();
      final var item = historyItem("item-1", AgentInstanceHistoryRoleEnum.CONFIGURATION, "hi");
      item.tools(List.of());
      request.setHistory(List.of(item));

      // when
      final Either<ProblemDetail, AgentInstanceRecord> result =
          mapper.toUpdateAgentInstanceRecord(AGENT_INSTANCE_KEY, request);

      // then
      assertThat(result.isRight()).isTrue();
      final var mappedItem = result.get().getHistory().get(0);
      assertThat(mappedItem.getTools()).isEmpty();
      assertThat(mappedItem.getChangedAttributes()).containsExactlyInAnyOrder("tools");
    }

    @Test
    void shouldMapPerItemLoopIterationOntoHistoryRecord() {
      // given
      final var request =
          AgentInstanceUpdateRequest.Builder.create()
              .elementInstanceKey(ELEMENT_INSTANCE_KEY)
              .jobKey(JOB_KEY)
              .jobLease(JOB_LEASE)
              .build();
      final var itemIterationTwo =
          historyItem("item-1", 2, AgentInstanceHistoryRoleEnum.USER, "hi");
      final var itemIterationThree =
          historyItem("item-2", 3, AgentInstanceHistoryRoleEnum.USER, "still there?");
      request.setHistory(List.of(itemIterationTwo, itemIterationThree));

      // when
      final Either<ProblemDetail, AgentInstanceRecord> result =
          mapper.toUpdateAgentInstanceRecord(AGENT_INSTANCE_KEY, request);

      // then
      assertThat(result.isRight()).isTrue();
      final var record = result.get();
      assertThat(record.getHistory().get(0).getLoopIteration()).isEqualTo(2);
      assertThat(record.getHistory().get(1).getLoopIteration()).isEqualTo(3);
    }
  }

  @Nested
  class CreateRequestMappingTest {

    @Test
    void shouldMapJobKeyJobLeaseAndHistoryOntoRecordPreservingOrder() {
      // given
      final var request =
          AgentInstanceCreationRequest.Builder.create()
              .elementInstanceKey(ELEMENT_INSTANCE_KEY)
              .jobKey(JOB_KEY)
              .jobLease("lease-abc")
              .history(
                  List.of(
                      AgentInstanceHistoryItem.Builder.create()
                          .historyItemId("item-0")
                          .loopIteration(1)
                          .role(AgentInstanceHistoryRoleEnum.CONFIGURATION)
                          .content(List.of(textContent("configuration")))
                          .producedAt("2025-06-01T12:00:00Z")
                          .build()
                          .model("gpt-4o")
                          .provider("openai")
                          .systemPrompt(List.of(textContent("You are a helpful assistant."))),
                      historyItem("item-1", AgentInstanceHistoryRoleEnum.USER, "hello"),
                      historyItem("item-2", AgentInstanceHistoryRoleEnum.ASSISTANT, "hi there")))
              .build();

      // when
      final Either<ProblemDetail, AgentInstanceRecord> result =
          mapper.toCreateAgentInstanceRecord(request);

      // then
      assertThat(result.isRight()).isTrue();
      final var record = result.get();
      assertThat(record.getJobKey()).isEqualTo(2251799813685249L);
      assertThat(record.getJobLease()).isEqualTo("lease-abc");
      assertThat(record.getHistory()).hasSize(3);
      assertThat(record.getHistory().get(0).getHistoryItemId()).isEqualTo("item-0");
      assertThat(record.getHistory().get(1).getHistoryItemId()).isEqualTo("item-1");
      assertThat(record.getHistory().get(2).getHistoryItemId()).isEqualTo("item-2");
    }

    @Test
    void shouldMapConfigurationLimitsOnCreateUsingGranularChangedAttributes() {
      // given
      final var request =
          AgentInstanceCreationRequest.Builder.create()
              .elementInstanceKey(ELEMENT_INSTANCE_KEY)
              .jobKey(JOB_KEY)
              .jobLease(JOB_LEASE)
              .history(
                  List.of(
                      AgentInstanceHistoryItem.Builder.create()
                          .historyItemId("item-0")
                          .loopIteration(1)
                          .role(AgentInstanceHistoryRoleEnum.CONFIGURATION)
                          .content(List.of(textContent("configuration")))
                          .producedAt("2025-06-01T12:00:00Z")
                          .build()
                          .model("gpt-4o")
                          .provider("openai")
                          .systemPrompt(List.of(textContent("You are a helpful assistant.")))
                          .limits(
                              AgentInstanceLimits.Builder.create()
                                  .maxModelCalls(10)
                                  .maxTokens(1000L)
                                  .maxToolCalls(5)
                                  .build())))
              .build();

      // when
      final Either<ProblemDetail, AgentInstanceRecord> result =
          mapper.toCreateAgentInstanceRecord(request);

      // then
      assertThat(result.isRight()).isTrue();
      final var mappedItem = result.get().getHistory().get(0);
      assertThat(mappedItem.getLimits().getMaxModelCalls()).isEqualTo(10);
      assertThat(mappedItem.getLimits().getMaxTokens()).isEqualTo(1000L);
      assertThat(mappedItem.getLimits().getMaxToolCalls()).isEqualTo(5);
      assertThat(mappedItem.getChangedAttributes())
          .containsExactlyInAnyOrder(
              "model", "provider", "maxTokens", "maxModelCalls", "maxToolCalls", "systemPrompt");
    }

    @Test
    void shouldMapCreatedHistoryFromCreationResult() {
      // given
      final var record = new AgentInstanceRecord();
      record.setAgentInstanceKey(2251799813685250L);
      record.addHistoryItem(
          new AgentHistoryRecord().setHistoryItemId("item-1").setAgentHistoryKey(100L));
      record.addHistoryItem(
          new AgentHistoryRecord()
              .setHistoryItemId("item-2")
              .setAgentHistoryKey(101L)
              .setDuplicate(true));

      // when
      final var result = mapper.toAgentInstanceCreationResult(record);

      // then
      assertThat(result.getAgentInstanceKey()).isEqualTo("2251799813685250");
      assertThat(result.getCreatedHistory()).hasSize(2);
      final var first = result.getCreatedHistory().get(0);
      assertThat(first.getHistoryItemId()).isEqualTo("item-1");
      assertThat(first.getHistoryItemKey()).isEqualTo("100");
      assertThat(first.getIsDuplicate()).isFalse();
      final var second = result.getCreatedHistory().get(1);
      assertThat(second.getHistoryItemId()).isEqualTo("item-2");
      assertThat(second.getHistoryItemKey()).isEqualTo("101");
      assertThat(second.getIsDuplicate()).isTrue();
    }
  }

  @Nested
  class UpdateResultMappingTest {

    @Test
    void shouldMapCreatedHistoryFromRecordWithHistoryItemIdKeyAndDuplicateFlag() {
      // given
      final var record = new AgentInstanceRecord();
      record.addHistoryItem(
          new AgentHistoryRecord().setHistoryItemId("item-1").setAgentHistoryKey(100L));
      record.addHistoryItem(
          new AgentHistoryRecord()
              .setHistoryItemId("item-2")
              .setAgentHistoryKey(101L)
              .setDuplicate(true));

      // when
      final var result = mapper.toAgentInstanceUpdateResult(record);

      // then
      assertThat(result.getCreatedHistory()).hasSize(2);
      final var first = result.getCreatedHistory().get(0);
      assertThat(first.getHistoryItemId()).isEqualTo("item-1");
      assertThat(first.getHistoryItemKey()).isEqualTo("100");
      assertThat(first.getIsDuplicate()).isFalse();
      final var second = result.getCreatedHistory().get(1);
      assertThat(second.getHistoryItemId()).isEqualTo("item-2");
      assertThat(second.getHistoryItemKey()).isEqualTo("101");
      assertThat(second.getIsDuplicate()).isTrue();
    }
  }

  @Nested
  class CombinedFieldMappingTest {

    @Test
    void shouldMapStatusAndHistoryTogetherWithoutLosingAny() {
      // given
      final var request =
          AgentInstanceUpdateRequest.Builder.create()
              .elementInstanceKey(ELEMENT_INSTANCE_KEY)
              .jobKey(JOB_KEY)
              .jobLease(JOB_LEASE)
              .build();
      request.setStatus(AgentInstanceUpdateStatusEnum.IDLE);
      request.setHistory(
          List.of(
              historyItem("item-1", AgentInstanceHistoryRoleEnum.USER, "hello"),
              historyItem("item-2", AgentInstanceHistoryRoleEnum.ASSISTANT, "hi there")));

      // when
      final Either<ProblemDetail, AgentInstanceRecord> result =
          mapper.toUpdateAgentInstanceRecord(AGENT_INSTANCE_KEY, request);

      // then
      assertThat(result.isRight()).isTrue();
      final var record = result.get();
      assertThat(record.getStatus().name()).isEqualTo("IDLE");
      assertThat(record.getHistory()).hasSize(2);
      assertThat(record.getHistory().get(0).getHistoryItemId()).isEqualTo("item-1");
      assertThat(record.getHistory().get(1).getHistoryItemId()).isEqualTo("item-2");
      assertThat(record.getChangedAttributes()).containsExactly("status");
    }
  }
}
