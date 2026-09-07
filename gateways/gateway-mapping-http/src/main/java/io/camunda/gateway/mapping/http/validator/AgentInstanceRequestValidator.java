/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.validator;

import static io.camunda.gateway.mapping.http.validator.ErrorMessages.ERROR_MESSAGE_EMPTY_ATTRIBUTE;
import static io.camunda.gateway.mapping.http.validator.ErrorMessages.ERROR_MESSAGE_HISTORY_MISSING_CONFIGURATION_ATTRIBUTE;
import static io.camunda.gateway.mapping.http.validator.ErrorMessages.ERROR_MESSAGE_INVALID_ATTRIBUTE_VALUE;
import static io.camunda.gateway.mapping.http.validator.ErrorMessages.ERROR_MESSAGE_TOO_MANY_CHARACTERS;
import static io.camunda.gateway.mapping.http.validator.RequestValidator.validate;
import static io.camunda.gateway.mapping.http.validator.RequestValidator.validateDate;
import static io.camunda.gateway.mapping.http.validator.RequestValidator.validateKeyFormat;
import static io.camunda.gateway.mapping.http.validator.RequestValidator.validatePositiveKeyFormat;

import io.camunda.gateway.protocol.model.AgentInstanceCreationRequest;
import io.camunda.gateway.protocol.model.AgentInstanceDocumentContent;
import io.camunda.gateway.protocol.model.AgentInstanceHistoryItem;
import io.camunda.gateway.protocol.model.AgentInstanceHistoryRoleEnum;
import io.camunda.gateway.protocol.model.AgentInstanceMessageContent;
import io.camunda.gateway.protocol.model.AgentInstanceObjectContent;
import io.camunda.gateway.protocol.model.AgentInstanceTextContent;
import io.camunda.gateway.protocol.model.AgentInstanceUpdateRequest;
import io.camunda.gateway.protocol.model.AgentTool;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ProblemDetail;

@NullMarked
public class AgentInstanceRequestValidator {

  // Mirrors HistoryItemId#maxLength in identifiers.yaml.
  private static final int MAX_HISTORY_ITEM_ID_LENGTH = 256;

  // Note: even if some properties are marked @NotNull in AgentInstanceCreationRequest,
  // no validation is performed during deserialization, so it is still necessary to validate it here
  @SuppressWarnings("ConstantValue")
  public Optional<ProblemDetail> validateCreateRequest(final AgentInstanceCreationRequest request) {
    return validate(
        () -> {
          final List<String> violations = new ArrayList<>();

          if (request.getElementInstanceKey() == null) {
            violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("elementInstanceKey"));
          } else {
            validatePositiveKeyFormat(
                request.getElementInstanceKey(), "elementInstanceKey", violations);
          }

          if (request.getJobKey() == null) {
            violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("jobKey"));
          } else {
            validatePositiveKeyFormat(request.getJobKey(), "jobKey", violations);
          }

          final var history = request.getHistory();

          if (history == null || history.isEmpty()) {
            violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("history"));
          } else {
            for (int i = 0; i < history.size(); i++) {
              validateHistoryItem(i, history.get(i), violations);
            }

            validateConfigurationEstablishesDefinition(history, violations);
          }

          return violations;
        });
  }

  // Note: even if some properties are marked @NotNull in AgentInstanceUpdateRequest,
  // no validation is performed during deserialization, so it is still necessary to validate it here
  @SuppressWarnings("ConstantValue")
  public Optional<ProblemDetail> validateUpdateRequest(
      final String agentInstanceKey, final AgentInstanceUpdateRequest request) {
    return validate(
        () -> {
          final List<String> violations = new ArrayList<>();

          validatePositiveKeyFormat(agentInstanceKey, "agentInstanceKey", violations);

          if (request.getElementInstanceKey() == null) {
            violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("elementInstanceKey"));
          } else {
            validateKeyFormat(request.getElementInstanceKey(), "elementInstanceKey", violations);
          }

          if (request.getJobKey() != null) {
            validatePositiveKeyFormat(request.getJobKey(), "jobKey", violations);
          }

          if (request.getHistory() != null && !request.getHistory().isEmpty()) {
            if (request.getJobKey() == null) {
              violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("jobKey"));
            }

            for (int i = 0; i < request.getHistory().size(); i++) {
              validateHistoryItem(i, request.getHistory().get(i), violations);
            }
          }

          return violations;
        });
  }

  private void validateHistoryItem(
      final int index,
      final @Nullable AgentInstanceHistoryItem historyItem,
      final List<String> violations) {
    if (historyItem == null) {
      violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("history[" + index + "]"));
      return;
    }
    if (historyItem.getHistoryItemId() == null || historyItem.getHistoryItemId().isBlank()) {
      violations.add(
          ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("history[" + index + "].historyItemId"));
    } else if (historyItem.getHistoryItemId().length() > MAX_HISTORY_ITEM_ID_LENGTH) {
      violations.add(
          ERROR_MESSAGE_TOO_MANY_CHARACTERS.formatted(
              "history[" + index + "].historyItemId", MAX_HISTORY_ITEM_ID_LENGTH));
    }
    if (historyItem.getLoopIteration() == null) {
      violations.add(
          ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("history[" + index + "].loopIteration"));
    } else if (historyItem.getLoopIteration() < 1) {
      violations.add(
          ERROR_MESSAGE_INVALID_ATTRIBUTE_VALUE.formatted(
              "history[" + index + "].loopIteration", historyItem.getLoopIteration(), "> 0"));
    }
    if (historyItem.getRole() == null) {
      violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("history[" + index + "].role"));
    }
    if (historyItem.getContent() == null) {
      violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("history[" + index + "].content"));
    } else {
      for (int i = 0; i < historyItem.getContent().size(); i++) {
        validateContentItem(
            "history[" + index + "].content[" + i + "]",
            historyItem.getContent().get(i),
            violations);
      }
    }
    if (historyItem.getProducedAt() == null || historyItem.getProducedAt().isBlank()) {
      violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("history[" + index + "].producedAt"));
    } else {
      validateDate(historyItem.getProducedAt(), "history[" + index + "].producedAt", violations);
    }
    if (historyItem.getTools() != null) {
      validateTools("history[" + index + "].tools", historyItem.getTools(), violations);
    }
    if (historyItem.getModel() != null && historyItem.getModel().isBlank()) {
      violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("history[" + index + "].model"));
    }
    if (historyItem.getProvider() != null && historyItem.getProvider().isBlank()) {
      violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("history[" + index + "].provider"));
    }
    if (historyItem.getLimits() != null) {
      final var limits = historyItem.getLimits();
      violations.addAll(
          validateLimit("history[" + index + "].limits.maxTokens", limits.getMaxTokens()));
      violations.addAll(
          validateLimit("history[" + index + "].limits.maxModelCalls", limits.getMaxModelCalls()));
      violations.addAll(
          validateLimit("history[" + index + "].limits.maxToolCalls", limits.getMaxToolCalls()));
    }
    if (historyItem.getSystemPrompt() != null) {
      if (historyItem.getSystemPrompt().isEmpty()) {
        violations.add(
            ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("history[" + index + "].systemPrompt"));
      } else {
        for (int i = 0; i < historyItem.getSystemPrompt().size(); i++) {
          validateContentItem(
              "history[" + index + "].systemPrompt[" + i + "]",
              historyItem.getSystemPrompt().get(i),
              violations);
        }
      }
    }
  }

  private void validateConfigurationEstablishesDefinition(
      final List<AgentInstanceHistoryItem> history, final List<String> violations) {
    final var configurationItems =
        history.stream()
            .filter(
                item ->
                    item != null && item.getRole() == AgentInstanceHistoryRoleEnum.CONFIGURATION)
            .toList();

    final boolean hasModel =
        configurationItems.stream()
            .anyMatch(item -> item.getModel() != null && !item.getModel().isBlank());
    final boolean hasProvider =
        configurationItems.stream()
            .anyMatch(item -> item.getProvider() != null && !item.getProvider().isBlank());
    final boolean hasSystemPrompt =
        configurationItems.stream()
            .anyMatch(item -> item.getSystemPrompt() != null && !item.getSystemPrompt().isEmpty());

    if (!hasModel) {
      violations.add(ERROR_MESSAGE_HISTORY_MISSING_CONFIGURATION_ATTRIBUTE.formatted("model"));
    }
    if (!hasProvider) {
      violations.add(ERROR_MESSAGE_HISTORY_MISSING_CONFIGURATION_ATTRIBUTE.formatted("provider"));
    }
    if (!hasSystemPrompt) {
      violations.add(
          ERROR_MESSAGE_HISTORY_MISSING_CONFIGURATION_ATTRIBUTE.formatted("systemPrompt"));
    }
  }

  private void validateContentItem(
      final String fieldPrefix,
      final AgentInstanceMessageContent content,
      final List<String> violations) {
    if (content instanceof final AgentInstanceTextContent text) {
      if (text.getText() == null || text.getText().isBlank()) {
        violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted(fieldPrefix + ".text"));
      }
    } else if (content instanceof final AgentInstanceDocumentContent doc) {
      validateDocumentContentItem(fieldPrefix, doc, violations);
    } else if (content instanceof final AgentInstanceObjectContent obj) {
      if (obj.getObject() == null) {
        violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted(fieldPrefix + ".object"));
      }
    }
  }

  private void validateDocumentContentItem(
      final String fieldPrefix,
      final AgentInstanceDocumentContent doc,
      final List<String> violations) {
    final var ref = doc.getDocumentReference();
    if (ref == null) {
      violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted(fieldPrefix + ".documentReference"));
      return;
    }
    if (ref.getDocumentId() == null || ref.getDocumentId().isBlank()) {
      violations.add(
          ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted(fieldPrefix + ".documentReference.documentId"));
      return;
    }
    if (ref.getMetadata() == null) {
      return;
    }
    final var expiresAt = ref.getMetadata().getExpiresAt();
    if (expiresAt != null && !expiresAt.isBlank()) {
      validateDate(expiresAt, fieldPrefix + ".documentReference.metadata.expiresAt", violations);
    }
  }

  private void validateTools(
      final String fieldPrefix, final List<AgentTool> tools, final List<String> violations) {
    for (int i = 0; i < tools.size(); i++) {
      final var tool = tools.get(i);
      if (tool == null) {
        violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted(fieldPrefix + "[" + i + "]"));
        continue;
      }
      if (tool.getName() == null || tool.getName().isBlank()) {
        violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted(fieldPrefix + "[" + i + "].name"));
      }
    }
  }

  private List<String> validateLimit(final String limitName, final Number limit) {
    if (limit != null && limit.longValue() < -1) {
      return List.of(ERROR_MESSAGE_INVALID_ATTRIBUTE_VALUE.formatted(limitName, limit, ">= -1"));
    }
    return Collections.emptyList();
  }
}
