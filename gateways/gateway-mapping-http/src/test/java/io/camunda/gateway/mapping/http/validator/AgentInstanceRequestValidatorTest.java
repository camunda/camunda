/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.validator;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.gateway.protocol.model.AgentInstanceCreationRequest;
import io.camunda.gateway.protocol.model.AgentInstanceDocumentContent;
import io.camunda.gateway.protocol.model.AgentInstanceHistoryItem;
import io.camunda.gateway.protocol.model.AgentInstanceHistoryRoleEnum;
import io.camunda.gateway.protocol.model.AgentInstanceLimits;
import io.camunda.gateway.protocol.model.AgentInstanceMessageContent;
import io.camunda.gateway.protocol.model.AgentInstanceObjectContent;
import io.camunda.gateway.protocol.model.AgentInstanceTextContent;
import io.camunda.gateway.protocol.model.AgentInstanceUpdateRequest;
import io.camunda.gateway.protocol.model.AgentTool;
import java.util.ArrayList;
import java.util.Arrays;
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
  private static final String JOB_LEASE = "lease-token-1";

  private final AgentInstanceRequestValidator validator = new AgentInstanceRequestValidator();

  @Nested
  // @DisplayName can't be used on @Nested classes with this Surefire version, see AGENTS.md
  // @DisplayName("Existing update rules")
  class ExistingUpdateRuleTest {

    @Test
    @DisplayName("Should accept a request with only elementInstanceKey and jobKey")
    void shouldAcceptValidUpdateRequest() {
      final var request =
          AgentInstanceUpdateRequest.Builder.create()
              .elementInstanceKey(ELEMENT_INSTANCE_KEY)
              .jobKey(JOB_KEY)
              .jobLease(JOB_LEASE)
              .build();

      final Optional<ProblemDetail> result =
          validator.validateUpdateRequest(AGENT_INSTANCE_KEY, request);

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should reject missing elementInstanceKey")
    void shouldRejectMissingElementInstanceKey() {
      final var request =
          AgentInstanceUpdateRequest.Builder.create()
              .elementInstanceKey(null)
              .jobKey(JOB_KEY)
              .jobLease(JOB_LEASE)
              .build();

      final Optional<ProblemDetail> result =
          validator.validateUpdateRequest(AGENT_INSTANCE_KEY, request);

      assertThat(result).isPresent();
      assertThat(result.get().getDetail()).isEqualTo("No elementInstanceKey provided.");
    }

    @Test
    @DisplayName(
        "Should accept a missing jobKey when no history is provided (enforcement deferred to #60864)")
    void shouldAcceptMissingJobKeyOnUpdateForNow() {
      final var request =
          AgentInstanceUpdateRequest.Builder.create()
              .elementInstanceKey(ELEMENT_INSTANCE_KEY)
              .jobKey(null)
              .jobLease(JOB_LEASE)
              .build();

      final Optional<ProblemDetail> result =
          validator.validateUpdateRequest(AGENT_INSTANCE_KEY, request);

      assertThat(result).isEmpty();
    }
  }

  @Nested
  // @DisplayName can't be used on @Nested classes with this Surefire version, see AGENTS.md
  // @DisplayName("History batch rules")
  class HistoryBatchRuleTest {

    @Test
    @DisplayName("Should accept a batch item with a non-blank historyItemId")
    void shouldAcceptHistoryItemWithHistoryItemId() {
      final var request =
          AgentInstanceUpdateRequest.Builder.create()
              .elementInstanceKey(ELEMENT_INSTANCE_KEY)
              .jobKey(JOB_KEY)
              .jobLease(JOB_LEASE)
              .build();
      request.setHistory(
          List.of(
              AgentInstanceHistoryItem.Builder.create()
                  .historyItemId("item-1")
                  .loopIteration(1)
                  .role(AgentInstanceHistoryRoleEnum.USER)
                  .content(
                      List.of(
                          AgentInstanceTextContent.Builder.create()
                              .contentType("TEXT")
                              .text("hello")
                              .build()))
                  .producedAt("2025-06-01T12:00:00Z")
                  .build()));

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
              .jobKey(JOB_KEY)
              .jobLease(JOB_LEASE)
              .build();
      request.setHistory(
          List.of(
              AgentInstanceHistoryItem.Builder.create()
                  .historyItemId(null)
                  .loopIteration(1)
                  .role(AgentInstanceHistoryRoleEnum.USER)
                  .content(
                      List.of(
                          AgentInstanceTextContent.Builder.create()
                              .contentType("TEXT")
                              .text("hello")
                              .build()))
                  .producedAt("2025-06-01T12:00:00Z")
                  .build()));

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
              .jobKey(JOB_KEY)
              .jobLease(JOB_LEASE)
              .build();
      request.setHistory(
          List.of(
              AgentInstanceHistoryItem.Builder.create()
                  .historyItemId("  ")
                  .loopIteration(1)
                  .role(AgentInstanceHistoryRoleEnum.USER)
                  .content(
                      List.of(
                          AgentInstanceTextContent.Builder.create()
                              .contentType("TEXT")
                              .text("hello")
                              .build()))
                  .producedAt("2025-06-01T12:00:00Z")
                  .build()));

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
              .jobKey(JOB_KEY)
              .jobLease(JOB_LEASE)
              .build();
      request.setHistory(
          List.of(
              AgentInstanceHistoryItem.Builder.create()
                  .historyItemId("item-1")
                  .loopIteration(1)
                  .role(AgentInstanceHistoryRoleEnum.USER)
                  .content(
                      List.of(
                          AgentInstanceTextContent.Builder.create()
                              .contentType("TEXT")
                              .text("hello")
                              .build()))
                  .producedAt("2025-06-01T12:00:00Z")
                  .build(),
              AgentInstanceHistoryItem.Builder.create()
                  .historyItemId(null)
                  .loopIteration(1)
                  .role(AgentInstanceHistoryRoleEnum.USER)
                  .content(
                      List.of(
                          AgentInstanceTextContent.Builder.create()
                              .contentType("TEXT")
                              .text("hello")
                              .build()))
                  .producedAt("2025-06-01T12:00:00Z")
                  .build()));

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
              .jobKey(JOB_KEY)
              .jobLease(JOB_LEASE)
              .build();
      request.setHistory(
          List.of(
              AgentInstanceHistoryItem.Builder.create()
                  .historyItemId("item-1")
                  .loopIteration(null)
                  .role(AgentInstanceHistoryRoleEnum.USER)
                  .content(
                      List.of(
                          AgentInstanceTextContent.Builder.create()
                              .contentType("TEXT")
                              .text("hello")
                              .build()))
                  .producedAt("2025-06-01T12:00:00Z")
                  .build()));

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
              .jobKey(null)
              .jobLease(JOB_LEASE)
              .build();
      request.setHistory(
          List.of(
              AgentInstanceHistoryItem.Builder.create()
                  .historyItemId("item-1")
                  .loopIteration(1)
                  .role(AgentInstanceHistoryRoleEnum.USER)
                  .content(
                      List.of(
                          AgentInstanceTextContent.Builder.create()
                              .contentType("TEXT")
                              .text("hello")
                              .build()))
                  .producedAt("2025-06-01T12:00:00Z")
                  .build()));

      final Optional<ProblemDetail> result =
          validator.validateUpdateRequest(AGENT_INSTANCE_KEY, request);

      assertThat(result).isPresent();
      assertThat(result.get().getDetail()).isEqualTo("No jobKey provided.");
    }

    @Test
    @DisplayName("Should reject a batch with a non-numeric jobKey")
    void shouldRejectHistoryBatchWithMalformedJobKey() {
      final var request =
          AgentInstanceUpdateRequest.Builder.create()
              .elementInstanceKey(ELEMENT_INSTANCE_KEY)
              .jobKey("not-a-number")
              .jobLease(JOB_LEASE)
              .build();
      request.setHistory(
          List.of(
              AgentInstanceHistoryItem.Builder.create()
                  .historyItemId("item-1")
                  .loopIteration(1)
                  .role(AgentInstanceHistoryRoleEnum.USER)
                  .content(
                      List.of(
                          AgentInstanceTextContent.Builder.create()
                              .contentType("TEXT")
                              .text("hello")
                              .build()))
                  .producedAt("2025-06-01T12:00:00Z")
                  .build()));

      final Optional<ProblemDetail> result =
          validator.validateUpdateRequest(AGENT_INSTANCE_KEY, request);

      assertThat(result).isPresent();
      assertThat(result.get().getDetail())
          .isEqualTo(
              "The provided jobKey 'not-a-number' is not a valid key. Expected a numeric value."
                  + " Did you pass an entity id instead of an entity key?.");
    }

    @Test
    @DisplayName("Should reject a batch item with a missing role")
    void shouldRejectHistoryItemWithMissingRole() {
      final var request =
          AgentInstanceUpdateRequest.Builder.create()
              .elementInstanceKey(ELEMENT_INSTANCE_KEY)
              .jobKey(JOB_KEY)
              .jobLease(JOB_LEASE)
              .build();
      request.setHistory(
          List.of(
              AgentInstanceHistoryItem.Builder.create()
                  .historyItemId("item-1")
                  .loopIteration(1)
                  .role(null)
                  .content(
                      List.of(
                          AgentInstanceTextContent.Builder.create()
                              .contentType("TEXT")
                              .text("hello")
                              .build()))
                  .producedAt("2025-06-01T12:00:00Z")
                  .build()));

      final Optional<ProblemDetail> result =
          validator.validateUpdateRequest(AGENT_INSTANCE_KEY, request);

      assertThat(result).isPresent();
      assertThat(result.get().getDetail()).isEqualTo("No history[0].role provided.");
    }

    @Test
    @DisplayName("Should allow a batch item with an empty content list")
    void shouldAllowHistoryItemWithEmptyContentList() {
      final var request =
          AgentInstanceUpdateRequest.Builder.create()
              .elementInstanceKey(ELEMENT_INSTANCE_KEY)
              .jobKey(JOB_KEY)
              .jobLease(JOB_LEASE)
              .build();
      request.setHistory(
          List.of(
              AgentInstanceHistoryItem.Builder.create()
                  .historyItemId("item-1")
                  .loopIteration(1)
                  .role(AgentInstanceHistoryRoleEnum.ASSISTANT)
                  .content(List.of())
                  .producedAt("2025-06-01T12:00:00Z")
                  .build()));

      final Optional<ProblemDetail> result =
          validator.validateUpdateRequest(AGENT_INSTANCE_KEY, request);

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should reject a batch item with a missing producedAt")
    void shouldRejectHistoryItemWithMissingProducedAt() {
      final var request =
          AgentInstanceUpdateRequest.Builder.create()
              .elementInstanceKey(ELEMENT_INSTANCE_KEY)
              .jobKey(JOB_KEY)
              .jobLease(JOB_LEASE)
              .build();
      request.setHistory(
          List.of(
              AgentInstanceHistoryItem.Builder.create()
                  .historyItemId("item-1")
                  .loopIteration(1)
                  .role(AgentInstanceHistoryRoleEnum.USER)
                  .content(
                      List.of(
                          AgentInstanceTextContent.Builder.create()
                              .contentType("TEXT")
                              .text("hello")
                              .build()))
                  .producedAt(null)
                  .build()));

      final Optional<ProblemDetail> result =
          validator.validateUpdateRequest(AGENT_INSTANCE_KEY, request);

      assertThat(result).isPresent();
      assertThat(result.get().getDetail()).isEqualTo("No history[0].producedAt provided.");
    }

    @Test
    @DisplayName("Should reject a batch item with a malformed producedAt")
    void shouldRejectHistoryItemWithMalformedProducedAt() {
      final var request =
          AgentInstanceUpdateRequest.Builder.create()
              .elementInstanceKey(ELEMENT_INSTANCE_KEY)
              .jobKey(JOB_KEY)
              .jobLease(JOB_LEASE)
              .build();
      request.setHistory(
          List.of(
              AgentInstanceHistoryItem.Builder.create()
                  .historyItemId("item-1")
                  .loopIteration(1)
                  .role(AgentInstanceHistoryRoleEnum.USER)
                  .content(
                      List.of(
                          AgentInstanceTextContent.Builder.create()
                              .contentType("TEXT")
                              .text("hello")
                              .build()))
                  .producedAt("not-a-date")
                  .build()));

      final Optional<ProblemDetail> result =
          validator.validateUpdateRequest(AGENT_INSTANCE_KEY, request);

      assertThat(result).isPresent();
      assertThat(result.get().getDetail())
          .isEqualTo(
              "The provided history[0].producedAt 'not-a-date' cannot be parsed as a date"
                  + " according to RFC 3339, section 5.6.");
    }

    @Test
    @DisplayName("Should reject a malformed jobKey even without a history batch")
    void shouldRejectMalformedJobKeyWithoutHistory() {
      final var request =
          AgentInstanceUpdateRequest.Builder.create()
              .elementInstanceKey(ELEMENT_INSTANCE_KEY)
              .jobKey("not-a-number")
              .jobLease(JOB_LEASE)
              .build();

      final Optional<ProblemDetail> result =
          validator.validateUpdateRequest(AGENT_INSTANCE_KEY, request);

      assertThat(result).isPresent();
      assertThat(result.get().getDetail())
          .isEqualTo(
              "The provided jobKey 'not-a-number' is not a valid key. Expected a numeric value."
                  + " Did you pass an entity id instead of an entity key?.");
    }

    @Test
    @DisplayName("Should reject a null history item in the batch")
    void shouldRejectNullHistoryItemInBatch() {
      final var request =
          AgentInstanceUpdateRequest.Builder.create()
              .elementInstanceKey(ELEMENT_INSTANCE_KEY)
              .jobKey(JOB_KEY)
              .jobLease(JOB_LEASE)
              .build();
      final var history = new ArrayList<AgentInstanceHistoryItem>();
      history.add(null);
      request.setHistory(history);

      final Optional<ProblemDetail> result =
          validator.validateUpdateRequest(AGENT_INSTANCE_KEY, request);

      assertThat(result).isPresent();
      assertThat(result.get().getDetail()).isEqualTo("No history[0] provided.");
    }

    @Test
    @DisplayName("Should reject a batch item with a zero loopIteration")
    void shouldRejectHistoryItemWithZeroLoopIteration() {
      final var request =
          AgentInstanceUpdateRequest.Builder.create()
              .elementInstanceKey(ELEMENT_INSTANCE_KEY)
              .jobKey(JOB_KEY)
              .jobLease(JOB_LEASE)
              .build();
      request.setHistory(
          List.of(
              AgentInstanceHistoryItem.Builder.create()
                  .historyItemId("item-1")
                  .loopIteration(0)
                  .role(AgentInstanceHistoryRoleEnum.USER)
                  .content(
                      List.of(
                          AgentInstanceTextContent.Builder.create()
                              .contentType("TEXT")
                              .text("hello")
                              .build()))
                  .producedAt("2025-06-01T12:00:00Z")
                  .build()));

      final Optional<ProblemDetail> result =
          validator.validateUpdateRequest(AGENT_INSTANCE_KEY, request);

      assertThat(result).isPresent();
      assertThat(result.get().getDetail())
          .isEqualTo("The value for history[0].loopIteration is '0' but must be > 0.");
    }

    @Test
    @DisplayName("Should reject a batch item with a negative loopIteration")
    void shouldRejectHistoryItemWithNegativeLoopIteration() {
      final var request =
          AgentInstanceUpdateRequest.Builder.create()
              .elementInstanceKey(ELEMENT_INSTANCE_KEY)
              .jobKey(JOB_KEY)
              .jobLease(JOB_LEASE)
              .build();
      request.setHistory(
          List.of(
              AgentInstanceHistoryItem.Builder.create()
                  .historyItemId("item-1")
                  .loopIteration(-1)
                  .role(AgentInstanceHistoryRoleEnum.USER)
                  .content(
                      List.of(
                          AgentInstanceTextContent.Builder.create()
                              .contentType("TEXT")
                              .text("hello")
                              .build()))
                  .producedAt("2025-06-01T12:00:00Z")
                  .build()));

      final Optional<ProblemDetail> result =
          validator.validateUpdateRequest(AGENT_INSTANCE_KEY, request);

      assertThat(result).isPresent();
      assertThat(result.get().getDetail())
          .isEqualTo("The value for history[0].loopIteration is '-1' but must be > 0.");
    }

    @Test
    @DisplayName("Should reject a batch item with blank text content")
    void shouldRejectHistoryItemWithBlankTextContent() {
      final var request =
          AgentInstanceUpdateRequest.Builder.create()
              .elementInstanceKey(ELEMENT_INSTANCE_KEY)
              .jobKey(JOB_KEY)
              .jobLease(JOB_LEASE)
              .build();
      request.setHistory(
          List.of(
              AgentInstanceHistoryItem.Builder.create()
                  .historyItemId("item-1")
                  .loopIteration(1)
                  .role(AgentInstanceHistoryRoleEnum.USER)
                  .content(
                      List.of(
                          (AgentInstanceMessageContent)
                              AgentInstanceTextContent.Builder.create()
                                  .contentType("TEXT")
                                  .text("  ")
                                  .build()))
                  .producedAt("2025-06-01T12:00:00Z")
                  .build()));

      final Optional<ProblemDetail> result =
          validator.validateUpdateRequest(AGENT_INSTANCE_KEY, request);

      assertThat(result).isPresent();
      assertThat(result.get().getDetail()).isEqualTo("No history[0].content[0].text provided.");
    }

    @Test
    @DisplayName("Should reject a batch item with document content missing a documentReference")
    void shouldRejectHistoryItemWithDocumentContentMissingDocumentReference() {
      final var request =
          AgentInstanceUpdateRequest.Builder.create()
              .elementInstanceKey(ELEMENT_INSTANCE_KEY)
              .jobKey(JOB_KEY)
              .jobLease(JOB_LEASE)
              .build();
      request.setHistory(
          List.of(
              AgentInstanceHistoryItem.Builder.create()
                  .historyItemId("item-1")
                  .loopIteration(1)
                  .role(AgentInstanceHistoryRoleEnum.USER)
                  .content(
                      List.of(
                          (AgentInstanceMessageContent)
                              AgentInstanceDocumentContent.Builder.create()
                                  .contentType("DOCUMENT")
                                  .documentReference(null)
                                  .build()))
                  .producedAt("2025-06-01T12:00:00Z")
                  .build()));

      final Optional<ProblemDetail> result =
          validator.validateUpdateRequest(AGENT_INSTANCE_KEY, request);

      assertThat(result).isPresent();
      assertThat(result.get().getDetail())
          .isEqualTo("No history[0].content[0].documentReference provided.");
    }

    @Test
    @DisplayName("Should reject a batch item with object content missing an object")
    void shouldRejectHistoryItemWithObjectContentMissingObject() {
      final var request =
          AgentInstanceUpdateRequest.Builder.create()
              .elementInstanceKey(ELEMENT_INSTANCE_KEY)
              .jobKey(JOB_KEY)
              .jobLease(JOB_LEASE)
              .build();
      request.setHistory(
          List.of(
              AgentInstanceHistoryItem.Builder.create()
                  .historyItemId("item-1")
                  .loopIteration(1)
                  .role(AgentInstanceHistoryRoleEnum.USER)
                  .content(
                      List.of(
                          (AgentInstanceMessageContent)
                              AgentInstanceObjectContent.Builder.create()
                                  .contentType("OBJECT")
                                  .object(null)
                                  .build()))
                  .producedAt("2025-06-01T12:00:00Z")
                  .build()));

      final Optional<ProblemDetail> result =
          validator.validateUpdateRequest(AGENT_INSTANCE_KEY, request);

      assertThat(result).isPresent();
      assertThat(result.get().getDetail()).isEqualTo("No history[0].content[0].object provided.");
    }

    @Test
    @DisplayName("Should reject a batch item tool without a name")
    void shouldRejectHistoryItemToolWithoutName() {
      final var request =
          AgentInstanceUpdateRequest.Builder.create()
              .elementInstanceKey(ELEMENT_INSTANCE_KEY)
              .jobKey(JOB_KEY)
              .jobLease(JOB_LEASE)
              .build();
      request.setHistory(
          List.of(
              AgentInstanceHistoryItem.Builder.create()
                  .historyItemId("item-1")
                  .loopIteration(1)
                  .role(AgentInstanceHistoryRoleEnum.CONFIGURATION)
                  .content(
                      List.of(
                          AgentInstanceTextContent.Builder.create()
                              .contentType("TEXT")
                              .text("hello")
                              .build()))
                  .producedAt("2025-06-01T12:00:00Z")
                  .build()
                  .tools(
                      List.of(
                          AgentTool.Builder.create()
                              .name(" ")
                              .description(null)
                              .elementId(null)
                              .build()))));

      final Optional<ProblemDetail> result =
          validator.validateUpdateRequest(AGENT_INSTANCE_KEY, request);

      assertThat(result).isPresent();
      assertThat(result.get().getDetail()).isEqualTo("No history[0].tools[0].name provided.");
    }

    @Test
    @DisplayName("Should reject a null tool element in a batch item")
    void shouldRejectNullToolInHistoryItem() {
      final var request =
          AgentInstanceUpdateRequest.Builder.create()
              .elementInstanceKey(ELEMENT_INSTANCE_KEY)
              .jobKey(JOB_KEY)
              .jobLease(JOB_LEASE)
              .build();
      request.setHistory(
          List.of(
              AgentInstanceHistoryItem.Builder.create()
                  .historyItemId("item-1")
                  .loopIteration(1)
                  .role(AgentInstanceHistoryRoleEnum.CONFIGURATION)
                  .content(
                      List.of(
                          AgentInstanceTextContent.Builder.create()
                              .contentType("TEXT")
                              .text("hello")
                              .build()))
                  .producedAt("2025-06-01T12:00:00Z")
                  .build()
                  .tools(Arrays.asList((AgentTool) null))));

      final Optional<ProblemDetail> result =
          validator.validateUpdateRequest(AGENT_INSTANCE_KEY, request);

      assertThat(result).isPresent();
      assertThat(result.get().getDetail()).isEqualTo("No history[0].tools[0] provided.");
    }

    @Test
    @DisplayName("Should report the correct index for a second batch item tool without a name")
    void shouldRejectSecondHistoryItemToolWithoutName() {
      final var request =
          AgentInstanceUpdateRequest.Builder.create()
              .elementInstanceKey(ELEMENT_INSTANCE_KEY)
              .jobKey(JOB_KEY)
              .jobLease(JOB_LEASE)
              .build();
      request.setHistory(
          List.of(
              AgentInstanceHistoryItem.Builder.create()
                  .historyItemId("item-1")
                  .loopIteration(1)
                  .role(AgentInstanceHistoryRoleEnum.CONFIGURATION)
                  .content(
                      List.of(
                          AgentInstanceTextContent.Builder.create()
                              .contentType("TEXT")
                              .text("hello")
                              .build()))
                  .producedAt("2025-06-01T12:00:00Z")
                  .build()
                  .tools(
                      List.of(
                          AgentTool.Builder.create()
                              .name("search")
                              .description(null)
                              .elementId(null)
                              .build(),
                          AgentTool.Builder.create()
                              .name(" ")
                              .description(null)
                              .elementId(null)
                              .build()))));

      final Optional<ProblemDetail> result =
          validator.validateUpdateRequest(AGENT_INSTANCE_KEY, request);

      assertThat(result).isPresent();
      assertThat(result.get().getDetail()).isEqualTo("No history[0].tools[1].name provided.");
    }

    @Test
    @DisplayName("Should reject a batch item with an out-of-range limits.maxTokens")
    void shouldRejectHistoryItemWithOutOfRangeMaxTokens() {
      final var request =
          AgentInstanceUpdateRequest.Builder.create()
              .elementInstanceKey(ELEMENT_INSTANCE_KEY)
              .jobKey(JOB_KEY)
              .jobLease(JOB_LEASE)
              .build();
      request.setHistory(
          List.of(
              AgentInstanceHistoryItem.Builder.create()
                  .historyItemId("item-1")
                  .loopIteration(1)
                  .role(AgentInstanceHistoryRoleEnum.CONFIGURATION)
                  .content(
                      List.of(
                          AgentInstanceTextContent.Builder.create()
                              .contentType("TEXT")
                              .text("hello")
                              .build()))
                  .producedAt("2025-06-01T12:00:00Z")
                  .build()
                  .limits(
                      AgentInstanceLimits.Builder.create()
                          .maxModelCalls(10)
                          .maxTokens(-2L)
                          .maxToolCalls(5)
                          .build())));

      final Optional<ProblemDetail> result =
          validator.validateUpdateRequest(AGENT_INSTANCE_KEY, request);

      assertThat(result).isPresent();
      assertThat(result.get().getDetail())
          .isEqualTo("The value for history[0].limits.maxTokens is '-2' but must be >= -1.");
    }

    @Test
    @DisplayName("Should reject a batch item with an out-of-range limits.maxModelCalls")
    void shouldRejectHistoryItemWithOutOfRangeMaxModelCalls() {
      final var request =
          AgentInstanceUpdateRequest.Builder.create()
              .elementInstanceKey(ELEMENT_INSTANCE_KEY)
              .jobKey(JOB_KEY)
              .jobLease(JOB_LEASE)
              .build();
      request.setHistory(
          List.of(
              AgentInstanceHistoryItem.Builder.create()
                  .historyItemId("item-1")
                  .loopIteration(1)
                  .role(AgentInstanceHistoryRoleEnum.CONFIGURATION)
                  .content(
                      List.of(
                          AgentInstanceTextContent.Builder.create()
                              .contentType("TEXT")
                              .text("hello")
                              .build()))
                  .producedAt("2025-06-01T12:00:00Z")
                  .build()
                  .limits(
                      AgentInstanceLimits.Builder.create()
                          .maxModelCalls(-2)
                          .maxTokens(1000L)
                          .maxToolCalls(5)
                          .build())));

      final Optional<ProblemDetail> result =
          validator.validateUpdateRequest(AGENT_INSTANCE_KEY, request);

      assertThat(result).isPresent();
      assertThat(result.get().getDetail())
          .isEqualTo("The value for history[0].limits.maxModelCalls is '-2' but must be >= -1.");
    }

    @Test
    @DisplayName("Should reject a batch item with an out-of-range limits.maxToolCalls")
    void shouldRejectHistoryItemWithOutOfRangeMaxToolCalls() {
      final var request =
          AgentInstanceUpdateRequest.Builder.create()
              .elementInstanceKey(ELEMENT_INSTANCE_KEY)
              .jobKey(JOB_KEY)
              .jobLease(JOB_LEASE)
              .build();
      request.setHistory(
          List.of(
              AgentInstanceHistoryItem.Builder.create()
                  .historyItemId("item-1")
                  .loopIteration(1)
                  .role(AgentInstanceHistoryRoleEnum.CONFIGURATION)
                  .content(
                      List.of(
                          AgentInstanceTextContent.Builder.create()
                              .contentType("TEXT")
                              .text("hello")
                              .build()))
                  .producedAt("2025-06-01T12:00:00Z")
                  .build()
                  .limits(
                      AgentInstanceLimits.Builder.create()
                          .maxModelCalls(10)
                          .maxTokens(1000L)
                          .maxToolCalls(-2)
                          .build())));

      final Optional<ProblemDetail> result =
          validator.validateUpdateRequest(AGENT_INSTANCE_KEY, request);

      assertThat(result).isPresent();
      assertThat(result.get().getDetail())
          .isEqualTo("The value for history[0].limits.maxToolCalls is '-2' but must be >= -1.");
    }

    @Test
    @DisplayName("Should reject a batch item with blank systemPrompt text content")
    void shouldRejectHistoryItemWithBlankSystemPromptTextContent() {
      final var request =
          AgentInstanceUpdateRequest.Builder.create()
              .elementInstanceKey(ELEMENT_INSTANCE_KEY)
              .jobKey(JOB_KEY)
              .jobLease(JOB_LEASE)
              .build();
      request.setHistory(
          List.of(
              AgentInstanceHistoryItem.Builder.create()
                  .historyItemId("item-1")
                  .loopIteration(1)
                  .role(AgentInstanceHistoryRoleEnum.CONFIGURATION)
                  .content(
                      List.of(
                          AgentInstanceTextContent.Builder.create()
                              .contentType("TEXT")
                              .text("hello")
                              .build()))
                  .producedAt("2025-06-01T12:00:00Z")
                  .build()
                  .systemPrompt(
                      List.of(
                          AgentInstanceTextContent.Builder.create()
                              .contentType("TEXT")
                              .text("  ")
                              .build()))));

      final Optional<ProblemDetail> result =
          validator.validateUpdateRequest(AGENT_INSTANCE_KEY, request);

      assertThat(result).isPresent();
      assertThat(result.get().getDetail())
          .isEqualTo("No history[0].systemPrompt[0].text provided.");
    }

    @Test
    @DisplayName("Should reject a batch item with an empty systemPrompt list")
    void shouldRejectHistoryItemWithEmptySystemPrompt() {
      final var request =
          AgentInstanceUpdateRequest.Builder.create()
              .elementInstanceKey(ELEMENT_INSTANCE_KEY)
              .jobKey(JOB_KEY)
              .jobLease(JOB_LEASE)
              .build();
      request.setHistory(
          List.of(
              AgentInstanceHistoryItem.Builder.create()
                  .historyItemId("item-1")
                  .loopIteration(1)
                  .role(AgentInstanceHistoryRoleEnum.CONFIGURATION)
                  .content(
                      List.of(
                          AgentInstanceTextContent.Builder.create()
                              .contentType("TEXT")
                              .text("hello")
                              .build()))
                  .producedAt("2025-06-01T12:00:00Z")
                  .build()
                  .systemPrompt(List.of())));

      final Optional<ProblemDetail> result =
          validator.validateUpdateRequest(AGENT_INSTANCE_KEY, request);

      assertThat(result).isPresent();
      assertThat(result.get().getDetail()).isEqualTo("No history[0].systemPrompt provided.");
    }

    @Test
    @DisplayName("Should reject a batch item with a blank model")
    void shouldRejectHistoryItemWithBlankModel() {
      final var request =
          AgentInstanceUpdateRequest.Builder.create()
              .elementInstanceKey(ELEMENT_INSTANCE_KEY)
              .jobKey(JOB_KEY)
              .jobLease(JOB_LEASE)
              .build();
      request.setHistory(
          List.of(
              AgentInstanceHistoryItem.Builder.create()
                  .historyItemId("item-1")
                  .loopIteration(1)
                  .role(AgentInstanceHistoryRoleEnum.CONFIGURATION)
                  .content(
                      List.of(
                          AgentInstanceTextContent.Builder.create()
                              .contentType("TEXT")
                              .text("hello")
                              .build()))
                  .producedAt("2025-06-01T12:00:00Z")
                  .build()
                  .model("")));

      final Optional<ProblemDetail> result =
          validator.validateUpdateRequest(AGENT_INSTANCE_KEY, request);

      assertThat(result).isPresent();
      assertThat(result.get().getDetail()).isEqualTo("No history[0].model provided.");
    }

    @Test
    @DisplayName("Should reject a batch item with a blank provider")
    void shouldRejectHistoryItemWithBlankProvider() {
      final var request =
          AgentInstanceUpdateRequest.Builder.create()
              .elementInstanceKey(ELEMENT_INSTANCE_KEY)
              .jobKey(JOB_KEY)
              .jobLease(JOB_LEASE)
              .build();
      request.setHistory(
          List.of(
              AgentInstanceHistoryItem.Builder.create()
                  .historyItemId("item-1")
                  .loopIteration(1)
                  .role(AgentInstanceHistoryRoleEnum.CONFIGURATION)
                  .content(
                      List.of(
                          AgentInstanceTextContent.Builder.create()
                              .contentType("TEXT")
                              .text("hello")
                              .build()))
                  .producedAt("2025-06-01T12:00:00Z")
                  .build()
                  .provider("   ")));

      final Optional<ProblemDetail> result =
          validator.validateUpdateRequest(AGENT_INSTANCE_KEY, request);

      assertThat(result).isPresent();
      assertThat(result.get().getDetail()).isEqualTo("No history[0].provider provided.");
    }
  }

  @Nested
  // @DisplayName can't be used on @Nested classes with this Surefire version, see AGENTS.md
  // @DisplayName("Create request rules")
  class CreateRequestRuleTest {

    private AgentInstanceCreationRequest validRequest(final String jobKey) {
      return bareRequest(
          jobKey,
          List.of(
              AgentInstanceHistoryItem.Builder.create()
                  .historyItemId("item-0")
                  .loopIteration(1)
                  .role(AgentInstanceHistoryRoleEnum.CONFIGURATION)
                  .content(
                      List.of(
                          AgentInstanceTextContent.Builder.create()
                              .contentType("TEXT")
                              .text("configuration")
                              .build()))
                  .producedAt("2025-06-01T12:00:00Z")
                  .build()
                  .model("gpt-4o")
                  .provider("openai")
                  .systemPrompt(
                      List.of(
                          AgentInstanceTextContent.Builder.create()
                              .contentType("TEXT")
                              .text("You are a helpful assistant.")
                              .build()))));
    }

    private AgentInstanceCreationRequest bareRequest(
        final String jobKey, final List<AgentInstanceHistoryItem> history) {
      return AgentInstanceCreationRequest.Builder.create()
          .elementInstanceKey(ELEMENT_INSTANCE_KEY)
          .jobKey(jobKey)
          .jobLease(JOB_LEASE)
          .history(history)
          .build();
    }

    @Test
    @DisplayName("Should reject missing elementInstanceKey")
    void shouldRejectMissingElementInstanceKey() {
      final var request =
          AgentInstanceCreationRequest.Builder.create()
              .elementInstanceKey(null)
              .jobKey(JOB_KEY)
              .jobLease(JOB_LEASE)
              .history(
                  List.of(
                      AgentInstanceHistoryItem.Builder.create()
                          .historyItemId("item-0")
                          .loopIteration(1)
                          .role(AgentInstanceHistoryRoleEnum.CONFIGURATION)
                          .content(
                              List.of(
                                  AgentInstanceTextContent.Builder.create()
                                      .contentType("TEXT")
                                      .text("configuration")
                                      .build()))
                          .producedAt("2025-06-01T12:00:00Z")
                          .build()
                          .model("gpt-4o")
                          .provider("openai")
                          .systemPrompt(
                              List.of(
                                  AgentInstanceTextContent.Builder.create()
                                      .contentType("TEXT")
                                      .text("You are a helpful assistant.")
                                      .build()))))
              .build();

      final Optional<ProblemDetail> result = validator.validateCreateRequest(request);

      assertThat(result).isPresent();
      assertThat(result.get().getDetail()).isEqualTo("No elementInstanceKey provided.");
    }

    @Test
    @DisplayName("Should accept a batch item with a non-blank historyItemId")
    void shouldAcceptHistoryItemWithHistoryItemId() {
      final var request =
          bareRequest(
              JOB_KEY,
              List.of(
                  AgentInstanceHistoryItem.Builder.create()
                      .historyItemId("item-1")
                      .loopIteration(1)
                      .role(AgentInstanceHistoryRoleEnum.USER)
                      .content(
                          List.of(
                              AgentInstanceTextContent.Builder.create()
                                  .contentType("TEXT")
                                  .text("hello")
                                  .build()))
                      .producedAt("2025-06-01T12:00:00Z")
                      .build(),
                  AgentInstanceHistoryItem.Builder.create()
                      .historyItemId("item-0")
                      .loopIteration(1)
                      .role(AgentInstanceHistoryRoleEnum.CONFIGURATION)
                      .content(
                          List.of(
                              AgentInstanceTextContent.Builder.create()
                                  .contentType("TEXT")
                                  .text("configuration")
                                  .build()))
                      .producedAt("2025-06-01T12:00:00Z")
                      .build()
                      .model("gpt-4o")
                      .provider("openai")
                      .systemPrompt(
                          List.of(
                              AgentInstanceTextContent.Builder.create()
                                  .contentType("TEXT")
                                  .text("You are a helpful assistant.")
                                  .build()))));

      final Optional<ProblemDetail> result = validator.validateCreateRequest(request);

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should reject a batch with history but no jobKey")
    void shouldRejectHistoryBatchWithoutJobKey() {
      final var request =
          bareRequest(
              null,
              List.of(
                  AgentInstanceHistoryItem.Builder.create()
                      .historyItemId("item-1")
                      .loopIteration(1)
                      .role(AgentInstanceHistoryRoleEnum.USER)
                      .content(
                          List.of(
                              AgentInstanceTextContent.Builder.create()
                                  .contentType("TEXT")
                                  .text("hello")
                                  .build()))
                      .producedAt("2025-06-01T12:00:00Z")
                      .build(),
                  AgentInstanceHistoryItem.Builder.create()
                      .historyItemId("item-0")
                      .loopIteration(1)
                      .role(AgentInstanceHistoryRoleEnum.CONFIGURATION)
                      .content(
                          List.of(
                              AgentInstanceTextContent.Builder.create()
                                  .contentType("TEXT")
                                  .text("configuration")
                                  .build()))
                      .producedAt("2025-06-01T12:00:00Z")
                      .build()
                      .model("gpt-4o")
                      .provider("openai")
                      .systemPrompt(
                          List.of(
                              AgentInstanceTextContent.Builder.create()
                                  .contentType("TEXT")
                                  .text("You are a helpful assistant.")
                                  .build()))));

      final Optional<ProblemDetail> result = validator.validateCreateRequest(request);

      assertThat(result).isPresent();
      assertThat(result.get().getDetail()).isEqualTo("No jobKey provided.");
    }

    @Test
    @DisplayName("Should reject missing jobKey even without a history batch")
    void shouldRejectMissingJobKeyWithoutHistory() {
      final var request = bareRequest(null, null);

      final Optional<ProblemDetail> result = validator.validateCreateRequest(request);

      assertThat(result).isPresent();
      assertThat(result.get().getDetail()).isEqualTo("No jobKey provided. No history provided.");
    }

    @Test
    @DisplayName("Should reject a non-numeric jobKey on create")
    void shouldRejectMalformedJobKeyOnCreate() {
      final var request = validRequest("not-a-number");

      final Optional<ProblemDetail> result = validator.validateCreateRequest(request);

      assertThat(result).isPresent();
      assertThat(result.get().getDetail())
          .isEqualTo(
              "The provided jobKey 'not-a-number' is not a valid key. Expected a numeric value."
                  + " Did you pass an entity id instead of an entity key?.");
    }

    @Test
    @DisplayName("Should reject a batch item with a missing (null) historyItemId")
    void shouldRejectHistoryItemWithNullHistoryItemId() {
      final var request =
          bareRequest(
              JOB_KEY,
              List.of(
                  AgentInstanceHistoryItem.Builder.create()
                      .historyItemId(null)
                      .loopIteration(1)
                      .role(AgentInstanceHistoryRoleEnum.USER)
                      .content(
                          List.of(
                              AgentInstanceTextContent.Builder.create()
                                  .contentType("TEXT")
                                  .text("hello")
                                  .build()))
                      .producedAt("2025-06-01T12:00:00Z")
                      .build(),
                  AgentInstanceHistoryItem.Builder.create()
                      .historyItemId("item-0")
                      .loopIteration(1)
                      .role(AgentInstanceHistoryRoleEnum.CONFIGURATION)
                      .content(
                          List.of(
                              AgentInstanceTextContent.Builder.create()
                                  .contentType("TEXT")
                                  .text("configuration")
                                  .build()))
                      .producedAt("2025-06-01T12:00:00Z")
                      .build()
                      .model("gpt-4o")
                      .provider("openai")
                      .systemPrompt(
                          List.of(
                              AgentInstanceTextContent.Builder.create()
                                  .contentType("TEXT")
                                  .text("You are a helpful assistant.")
                                  .build()))));

      final Optional<ProblemDetail> result = validator.validateCreateRequest(request);

      assertThat(result).isPresent();
      assertThat(result.get().getDetail()).isEqualTo("No history[0].historyItemId provided.");
    }

    @Test
    @DisplayName("Should reject history without a CONFIGURATION item establishing the definition")
    void shouldRejectHistoryWithoutConfigurationItem() {
      final var request =
          bareRequest(
              JOB_KEY,
              List.of(
                  AgentInstanceHistoryItem.Builder.create()
                      .historyItemId("item-1")
                      .loopIteration(1)
                      .role(AgentInstanceHistoryRoleEnum.USER)
                      .content(
                          List.of(
                              AgentInstanceTextContent.Builder.create()
                                  .contentType("TEXT")
                                  .text("hello")
                                  .build()))
                      .producedAt("2025-06-01T12:00:00Z")
                      .build()));

      final Optional<ProblemDetail> result = validator.validateCreateRequest(request);

      assertThat(result).isPresent();
      assertThat(result.get().getDetail())
          .isEqualTo(
              "No CONFIGURATION history item sets 'model'; add a CONFIGURATION history item that"
                  + " sets it. No CONFIGURATION history item sets 'provider'; add a CONFIGURATION"
                  + " history item that sets it. No CONFIGURATION history item sets"
                  + " 'systemPrompt'; add a CONFIGURATION history item that sets it.");
    }

    @Test
    @DisplayName(
        "Should reject a later CONFIGURATION item that clears model with a blank value, even"
            + " after an earlier CONFIGURATION item established it")
    void shouldRejectLaterConfigurationItemWithBlankModel() {
      final var request =
          bareRequest(
              JOB_KEY,
              List.of(
                  AgentInstanceHistoryItem.Builder.create()
                      .historyItemId("item-1")
                      .loopIteration(1)
                      .role(AgentInstanceHistoryRoleEnum.CONFIGURATION)
                      .content(
                          List.of(
                              AgentInstanceTextContent.Builder.create()
                                  .contentType("TEXT")
                                  .text("configuration")
                                  .build()))
                      .producedAt("2025-06-01T12:00:00Z")
                      .build()
                      .model("gpt-4o")
                      .provider("openai")
                      .systemPrompt(
                          List.of(
                              AgentInstanceTextContent.Builder.create()
                                  .contentType("TEXT")
                                  .text("be helpful")
                                  .build())),
                  AgentInstanceHistoryItem.Builder.create()
                      .historyItemId("item-2")
                      .loopIteration(2)
                      .role(AgentInstanceHistoryRoleEnum.CONFIGURATION)
                      .content(
                          List.of(
                              AgentInstanceTextContent.Builder.create()
                                  .contentType("TEXT")
                                  .text("switching model")
                                  .build()))
                      .producedAt("2025-06-01T12:01:00Z")
                      .build()
                      .model("")));

      final Optional<ProblemDetail> result = validator.validateCreateRequest(request);

      assertThat(result).isPresent();
      assertThat(result.get().getDetail()).isEqualTo("No history[1].model provided.");
    }
  }
}
