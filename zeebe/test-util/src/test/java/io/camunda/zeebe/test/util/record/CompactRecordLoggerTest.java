/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.util.record;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.protocol.record.ImmutableRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.value.AgentHistoryCommitStatus;
import io.camunda.zeebe.protocol.record.value.AgentHistoryContentType;
import io.camunda.zeebe.protocol.record.value.AgentHistoryRole;
import io.camunda.zeebe.protocol.record.value.ImmutableAgentHistoryEmbeddedToolCallValue;
import io.camunda.zeebe.protocol.record.value.ImmutableAgentHistoryMessageContentValue;
import io.camunda.zeebe.protocol.record.value.ImmutableAgentHistoryMetricsValue;
import io.camunda.zeebe.protocol.record.value.ImmutableAgentHistoryRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableUsageMetricRecordValue;
import io.camunda.zeebe.protocol.record.value.UsageMetricRecordValue.EventType;
import io.camunda.zeebe.protocol.record.value.UsageMetricRecordValue.IntervalType;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class CompactRecordLoggerTest {

  private CompactRecordLogger compactRecordLogger;

  @BeforeEach
  public void beforeEach() {
    compactRecordLogger = new CompactRecordLogger(List.of());
  }

  @ParameterizedTest(name = "has compact logging for {0}")
  @EnumSource(ValueType.class)
  public void shouldHaveCompactLoggerForValueType(final ValueType valueType) {
    assertThat(compactRecordLogger.getSupportedValueTypes())
        .as(
            """
            Expected CompactRecordLogger to support logging for value type '%s'.
            If you're introducing a new record, make sure to add a compact logger for it.
            """,
            valueType)
        .contains(valueType);
  }

  /**
   * Tests the summarization logic for records via the `summarizeXyz(...)` methods in {@link
   * CompactRecordLogger}.
   */
  @Nested
  class CompactSummaryTest {

    @Test
    void shouldSummarizeUsageMetricsWithCounterCorrectly() {
      // given
      final var logger = new CompactRecordLogger(List.of());
      final var usageMetricsRecord =
          ImmutableRecord.builder()
              .withValueType(ValueType.USAGE_METRIC)
              .withValue(
                  ImmutableUsageMetricRecordValue.builder()
                      .withEventType(EventType.RPI)
                      .withIntervalType(IntervalType.ACTIVE)
                      .withStartTime(1718000000000L)
                      .withEndTime(1718003600000L)
                      .withResetTime(1718001800000L)
                      .withCounterValues(Map.of("tenant1", 42L, "tenant2", 84L))
                      .build())
              .build();

      // when
      final String result = logger.summarizeUsageMetrics(usageMetricsRecord);

      // then
      final String expected =
          "RPI:ACTIVE start[2024-06-10T06:13:20] end[2024-06-10T07:13:20] reset[2024-06-10T06:43:20] metricValues: {tenant1=42, tenant2=84}";
      assertThat(result).isEqualTo(expected);
    }

    @Test
    void shouldSummarizeUsageMetricsWithSetCorrectly() {
      // given
      final var logger = new CompactRecordLogger(List.of());
      final var usageMetricsRecord =
          ImmutableRecord.builder()
              .withValueType(ValueType.USAGE_METRIC)
              .withValue(
                  ImmutableUsageMetricRecordValue.builder()
                      .withEventType(EventType.TU)
                      .withIntervalType(IntervalType.ACTIVE)
                      .withStartTime(1718000000000L)
                      .withEndTime(1718003600000L)
                      .withResetTime(1718001800000L)
                      .withSetValues(
                          Map.of(
                              "tenant1", Set.of(1234567L, 7654321L), "tenant2", Set.of(9876543L)))
                      .build())
              .build();

      // when
      final String result = logger.summarizeUsageMetrics(usageMetricsRecord);

      // then
      assertThat(result)
          .startsWith(
              "TU:ACTIVE start[2024-06-10T06:13:20] end[2024-06-10T07:13:20] reset[2024-06-10T06:43:20] metricValues: {")
          .satisfies(
              r ->
                  assertThat(r)
                      .containsAnyOf("tenant1=[7654321, 1234567]", "tenant1=[1234567, 7654321]")
                      .contains("tenant2=[9876543]"));
    }

    @Test
    void shouldSummarizeProcessInfoInProcessInstanceRecords() {
      // given
      final var logger = new CompactRecordLogger(List.of());
      final var record =
          ImmutableRecord.builder()
              .withValueType(ValueType.PROCESS_INSTANCE)
              .withValue(
                  ImmutableProcessInstanceRecordValue.builder()
                      .withBpmnProcessId("procID")
                      .withBusinessId("bizzID")
                      .withProcessInstanceKey(123L)
                      .build())
              .build();

      // when
      final String result = logger.summarizeProcessInstance(record);

      // then
      assertThat(result)
          .contains(
              """
              in <process "procID"[K123]"bizzID">""");
    }

    @Test
    void shouldSummarizeAgentHistoryRecord() {
      // given
      final var logger = new CompactRecordLogger(List.of());
      final var record =
          ImmutableRecord.builder()
              .withValueType(ValueType.AGENT_HISTORY)
              .withValue(
                  ImmutableAgentHistoryRecordValue.builder()
                      .withAgentInstanceKey(1L)
                      .withElementInstanceKey(2L)
                      .withJobKey(3L)
                      .withRole(AgentHistoryRole.ASSISTANT)
                      .withCommitStatus(AgentHistoryCommitStatus.COMMITTED)
                      .withJobLease("1")
                      .withIteration(2)
                      .withMetrics(
                          ImmutableAgentHistoryMetricsValue.builder()
                              .withInputTokens(100)
                              .withOutputTokens(50)
                              .withDurationMs(1234)
                              .build())
                      .addContent(
                          ImmutableAgentHistoryMessageContentValue.builder()
                              .withContentType(AgentHistoryContentType.TEXT)
                              .withText("I will analyze the customer data")
                              .build())
                      .addToolCalls(
                          ImmutableAgentHistoryEmbeddedToolCallValue.builder()
                              .withToolCallId("call_xyz")
                              .withToolName("getAccount")
                              .withElementId("task_2")
                              .build())
                      .addToolCalls(
                          ImmutableAgentHistoryEmbeddedToolCallValue.builder()
                              .withToolCallId("call_abc")
                              .withToolName("getOrderDetails")
                              .withElementId("task_1")
                              .build())
                      .build())
              .build();

      // when
      final String result = logger.summarizeAgentHistory(record);

      // then
      assertThat(result)
          .isEqualTo(
              """
              K1#2 ASSISTANT COMMITTED @K2 K3#1 tokens:100/50 ms:1234
                     I will analyze the customer data
                     calling tools: getAccount, getOrderDetails""");
    }

    @Test
    void shouldSummarizeAgentHistoryToolResultRecord() {
      // given
      final var logger = new CompactRecordLogger(List.of());
      final var record =
          ImmutableRecord.builder()
              .withValueType(ValueType.AGENT_HISTORY)
              .withValue(
                  ImmutableAgentHistoryRecordValue.builder()
                      .withAgentInstanceKey(1L)
                      .withElementInstanceKey(2L)
                      .withJobKey(3L)
                      .withRole(AgentHistoryRole.TOOL_RESULT)
                      .withCommitStatus(AgentHistoryCommitStatus.COMMITTED)
                      .withJobLease("1")
                      .withIteration(3)
                      .addContent(
                          ImmutableAgentHistoryMessageContentValue.builder()
                              .withContentType(AgentHistoryContentType.OBJECT)
                              .withObject(Map.of("orderId", "12345"))
                              .build())
                      .build())
              .build();

      // when
      final String result = logger.summarizeAgentHistory(record);

      // then
      assertThat(result)
          .isEqualTo(
              """
              K1#3 TOOL_RESULT COMMITTED @K2 K3#1
                     {orderId=12345}""");
    }
  }
}
