/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.validator;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.gateway.protocol.model.AgentInstanceHistoryItem;
import io.camunda.gateway.protocol.model.AgentInstanceHistoryRoleEnum;
import io.camunda.gateway.protocol.model.AgentInstanceMessageContent;
import io.camunda.gateway.protocol.model.AgentInstanceMetricsDelta;
import io.camunda.gateway.protocol.model.AgentInstanceTextContent;
import io.camunda.gateway.protocol.model.AgentInstanceUpdateRequest;
import io.camunda.gateway.protocol.model.AgentTool;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;

@DisplayName("AgentInstanceRequestValidator Tests")
class AgentInstanceRequestValidatorTest {

  private static final String AGENT_INSTANCE_KEY = "9007199254741017";
  private static final String ELEMENT_INSTANCE_KEY = "2251799813685248";
  private static final String JOB_KEY = "2251799813685249";

  private final AgentInstanceRequestValidator validator = new AgentInstanceRequestValidator();

  @Nested
  @DisplayName("Existing update rules")
  class ExistingUpdateRuleTest {

    @Test
    @DisplayName("Should accept a request with only elementInstanceKey")
    void shouldAcceptValidUpdateRequest() {
      final var request =
          AgentInstanceUpdateRequest.Builder.create()
              .elementInstanceKey(ELEMENT_INSTANCE_KEY)
              .build();

      final Optional<ProblemDetail> result =
          validator.validateUpdateRequest(AGENT_INSTANCE_KEY, request);

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should reject missing elementInstanceKey")
    void shouldRejectMissingElementInstanceKey() {
      final var request =
          AgentInstanceUpdateRequest.Builder.create().elementInstanceKey(null).build();

      final Optional<ProblemDetail> result =
          validator.validateUpdateRequest(AGENT_INSTANCE_KEY, request);

      assertThat(result).isPresent();
      assertThat(result.get().getDetail()).isEqualTo("No elementInstanceKey provided.");
    }

    @Test
    @DisplayName("Should reject a negative metrics delta")
    void shouldRejectNegativeMetricsDelta() {
      final var request =
          AgentInstanceUpdateRequest.Builder.create()
              .elementInstanceKey(ELEMENT_INSTANCE_KEY)
              .build();
      request.setMetrics(AgentInstanceMetricsDelta.Builder.create().build());
      request.getMetrics().setInputTokens(-1L);

      final Optional<ProblemDetail> result =
          validator.validateUpdateRequest(AGENT_INSTANCE_KEY, request);

      assertThat(result).isPresent();
      assertThat(result.get().getDetail())
          .isEqualTo("The value for metrics.inputTokens is '-1' but must be >= 0.");
    }

    @Test
    @DisplayName("Should reject a tool without a name")
    void shouldRejectToolWithoutName() {
      final var request =
          AgentInstanceUpdateRequest.Builder.create()
              .elementInstanceKey(ELEMENT_INSTANCE_KEY)
              .build();
      request.setTools(
          List.of(AgentTool.Builder.create().name("").description("d").elementId(null).build()));

      final Optional<ProblemDetail> result =
          validator.validateUpdateRequest(AGENT_INSTANCE_KEY, request);

      assertThat(result).isPresent();
      assertThat(result.get().getDetail()).isEqualTo("No tools[0].name provided.");
    }
  }

  @Nested
  @DisplayName("History batch rules")
  class HistoryBatchRuleTest {

    @Test
    @DisplayName("Should accept a batch item with a non-blank historyItemId")
    void shouldAcceptHistoryItemWithHistoryItemId() {
      final var request =
          AgentInstanceUpdateRequest.Builder.create()
              .elementInstanceKey(ELEMENT_INSTANCE_KEY)
              .build();
      request.setJobKey(JOB_KEY);
      request.setHistory(List.of(historyItem("item-1")));

      final Optional<ProblemDetail> result =
          validator.validateUpdateRequest(AGENT_INSTANCE_KEY, request);

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should reject a batch item with a missing (null) historyItemId")
    void shouldRejectHistoryItemWithNullHistoryItemId() {
      final var request =
          AgentInstanceUpdateRequest.Builder.create()
              .elementInstanceKey(ELEMENT_INSTANCE_KEY)
              .build();
      request.setJobKey(JOB_KEY);
      request.setHistory(List.of(historyItem(null)));

      final Optional<ProblemDetail> result =
          validator.validateUpdateRequest(AGENT_INSTANCE_KEY, request);

      assertThat(result).isPresent();
      assertThat(result.get().getDetail()).isEqualTo("No history[0].historyItemId provided.");
    }

    @Test
    @DisplayName("Should reject a batch item with a blank historyItemId")
    void shouldRejectHistoryItemWithBlankHistoryItemId() {
      final var request =
          AgentInstanceUpdateRequest.Builder.create()
              .elementInstanceKey(ELEMENT_INSTANCE_KEY)
              .build();
      request.setJobKey(JOB_KEY);
      request.setHistory(List.of(historyItem("  ")));

      final Optional<ProblemDetail> result =
          validator.validateUpdateRequest(AGENT_INSTANCE_KEY, request);

      assertThat(result).isPresent();
      assertThat(result.get().getDetail()).isEqualTo("No history[0].historyItemId provided.");
    }

    @Test
    @DisplayName("Should report the correct index for a batch item beyond the first")
    void shouldReportCorrectIndexForSecondHistoryItem() {
      final var request =
          AgentInstanceUpdateRequest.Builder.create()
              .elementInstanceKey(ELEMENT_INSTANCE_KEY)
              .build();
      request.setJobKey(JOB_KEY);
      request.setHistory(List.of(historyItem("item-1"), historyItem(null)));

      final Optional<ProblemDetail> result =
          validator.validateUpdateRequest(AGENT_INSTANCE_KEY, request);

      assertThat(result).isPresent();
      assertThat(result.get().getDetail()).isEqualTo("No history[1].historyItemId provided.");
    }

    @Test
    @DisplayName("Should reject a batch item with a missing loopIteration")
    void shouldRejectHistoryItemWithMissingLoopIteration() {
      final var request =
          AgentInstanceUpdateRequest.Builder.create()
              .elementInstanceKey(ELEMENT_INSTANCE_KEY)
              .build();
      request.setJobKey(JOB_KEY);
      request.setHistory(List.of(historyItemWithoutLoopIteration("item-1")));

      final Optional<ProblemDetail> result =
          validator.validateUpdateRequest(AGENT_INSTANCE_KEY, request);

      assertThat(result).isPresent();
      assertThat(result.get().getDetail()).isEqualTo("No history[0].loopIteration provided.");
    }

    @Test
    @DisplayName("Should reject a batch with history but no jobKey")
    void shouldRejectHistoryBatchWithoutJobKey() {
      final var request =
          AgentInstanceUpdateRequest.Builder.create()
              .elementInstanceKey(ELEMENT_INSTANCE_KEY)
              .build();
      request.setHistory(List.of(historyItem("item-1")));

      final Optional<ProblemDetail> result =
          validator.validateUpdateRequest(AGENT_INSTANCE_KEY, request);

      assertThat(result).isPresent();
      assertThat(result.get().getDetail()).isEqualTo("No jobKey provided.");
    }
  }

  private static AgentInstanceHistoryItem historyItem(final String historyItemId) {
    final AgentInstanceMessageContent content =
        AgentInstanceTextContent.Builder.create().contentType("TEXT").text("hello").build();
    return AgentInstanceHistoryItem.Builder.create()
        .historyItemId(historyItemId)
        .loopIteration(1)
        .role(AgentInstanceHistoryRoleEnum.USER)
        .content(List.of(content))
        .producedAt("2025-06-01T12:00:00Z")
        .build();
  }

  private static AgentInstanceHistoryItem historyItemWithoutLoopIteration(
      final String historyItemId) {
    final AgentInstanceMessageContent content =
        AgentInstanceTextContent.Builder.create().contentType("TEXT").text("hello").build();
    return AgentInstanceHistoryItem.Builder.create()
        .historyItemId(historyItemId)
        .loopIteration(null)
        .role(AgentInstanceHistoryRoleEnum.USER)
        .content(List.of(content))
        .producedAt("2025-06-01T12:00:00Z")
        .build();
  }
}
