/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.validator;

import static io.camunda.gateway.mapping.http.validator.RequestValidator.validate;
import static io.camunda.security.validation.ErrorMessages.ERROR_MESSAGE_EMPTY_ATTRIBUTE;

import io.camunda.gateway.protocol.model.CreateGlobalExecutionListenerRequest;
import io.camunda.gateway.protocol.model.CreateGlobalTaskListenerRequest;
import io.camunda.gateway.protocol.model.GlobalExecutionListenerCategoryEnum;
import io.camunda.gateway.protocol.model.GlobalExecutionListenerElementTypeEnum;
import io.camunda.gateway.protocol.model.GlobalExecutionListenerEventTypeEnum;
import io.camunda.gateway.protocol.model.UpdateGlobalExecutionListenerRequest;
import io.camunda.gateway.protocol.model.UpdateGlobalTaskListenerRequest;
import io.camunda.security.validation.IdentifierValidator;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.ProblemDetail;

public class GlobalListenerRequestValidator {

  private static final Map<
          GlobalExecutionListenerElementTypeEnum, Set<GlobalExecutionListenerEventTypeEnum>>
      SUPPORTED_EVENTS =
          Map.ofEntries(
              entry(
                  GlobalExecutionListenerElementTypeEnum.PROCESS,
                  EnumSet.of(
                      GlobalExecutionListenerEventTypeEnum.START,
                      GlobalExecutionListenerEventTypeEnum.END,
                      GlobalExecutionListenerEventTypeEnum.CANCEL)),
              entry(
                  GlobalExecutionListenerElementTypeEnum.SUBPROCESS,
                  EnumSet.of(
                      GlobalExecutionListenerEventTypeEnum.START,
                      GlobalExecutionListenerEventTypeEnum.END)),
              entry(
                  GlobalExecutionListenerElementTypeEnum.EVENT_SUBPROCESS,
                  EnumSet.of(
                      GlobalExecutionListenerEventTypeEnum.START,
                      GlobalExecutionListenerEventTypeEnum.END)),
              entry(
                  GlobalExecutionListenerElementTypeEnum.SERVICE_TASK,
                  EnumSet.of(
                      GlobalExecutionListenerEventTypeEnum.START,
                      GlobalExecutionListenerEventTypeEnum.END)),
              entry(
                  GlobalExecutionListenerElementTypeEnum.USER_TASK,
                  EnumSet.of(
                      GlobalExecutionListenerEventTypeEnum.START,
                      GlobalExecutionListenerEventTypeEnum.END)),
              entry(
                  GlobalExecutionListenerElementTypeEnum.SEND_TASK,
                  EnumSet.of(
                      GlobalExecutionListenerEventTypeEnum.START,
                      GlobalExecutionListenerEventTypeEnum.END)),
              entry(
                  GlobalExecutionListenerElementTypeEnum.RECEIVE_TASK,
                  EnumSet.of(
                      GlobalExecutionListenerEventTypeEnum.START,
                      GlobalExecutionListenerEventTypeEnum.END)),
              entry(
                  GlobalExecutionListenerElementTypeEnum.SCRIPT_TASK,
                  EnumSet.of(
                      GlobalExecutionListenerEventTypeEnum.START,
                      GlobalExecutionListenerEventTypeEnum.END)),
              entry(
                  GlobalExecutionListenerElementTypeEnum.BUSINESS_RULE_TASK,
                  EnumSet.of(
                      GlobalExecutionListenerEventTypeEnum.START,
                      GlobalExecutionListenerEventTypeEnum.END)),
              entry(
                  GlobalExecutionListenerElementTypeEnum.CALL_ACTIVITY,
                  EnumSet.of(
                      GlobalExecutionListenerEventTypeEnum.START,
                      GlobalExecutionListenerEventTypeEnum.END)),
              entry(
                  GlobalExecutionListenerElementTypeEnum.MULTI_INSTANCE_BODY,
                  EnumSet.of(
                      GlobalExecutionListenerEventTypeEnum.START,
                      GlobalExecutionListenerEventTypeEnum.END)),
              entry(
                  GlobalExecutionListenerElementTypeEnum.EXCLUSIVE_GATEWAY,
                  EnumSet.of(GlobalExecutionListenerEventTypeEnum.START)),
              entry(
                  GlobalExecutionListenerElementTypeEnum.PARALLEL_GATEWAY,
                  EnumSet.of(GlobalExecutionListenerEventTypeEnum.START)),
              entry(
                  GlobalExecutionListenerElementTypeEnum.INCLUSIVE_GATEWAY,
                  EnumSet.of(GlobalExecutionListenerEventTypeEnum.START)),
              entry(
                  GlobalExecutionListenerElementTypeEnum.EVENT_BASED_GATEWAY,
                  EnumSet.of(GlobalExecutionListenerEventTypeEnum.START)),
              entry(
                  GlobalExecutionListenerElementTypeEnum.START_EVENT,
                  EnumSet.of(GlobalExecutionListenerEventTypeEnum.END)),
              entry(
                  GlobalExecutionListenerElementTypeEnum.END_EVENT,
                  EnumSet.of(GlobalExecutionListenerEventTypeEnum.START)),
              entry(
                  GlobalExecutionListenerElementTypeEnum.INTERMEDIATE_CATCH_EVENT,
                  EnumSet.of(
                      GlobalExecutionListenerEventTypeEnum.START,
                      GlobalExecutionListenerEventTypeEnum.END)),
              entry(
                  GlobalExecutionListenerElementTypeEnum.INTERMEDIATE_THROW_EVENT,
                  EnumSet.of(
                      GlobalExecutionListenerEventTypeEnum.START,
                      GlobalExecutionListenerEventTypeEnum.END)),
              entry(
                  GlobalExecutionListenerElementTypeEnum.BOUNDARY_EVENT,
                  EnumSet.of(GlobalExecutionListenerEventTypeEnum.END)));

  private static final Map<
          GlobalExecutionListenerCategoryEnum, List<GlobalExecutionListenerElementTypeEnum>>
      CATEGORY_EXPANSIONS =
          Map.of(
              GlobalExecutionListenerCategoryEnum.TASKS,
              List.of(
                  GlobalExecutionListenerElementTypeEnum.SERVICE_TASK,
                  GlobalExecutionListenerElementTypeEnum.USER_TASK,
                  GlobalExecutionListenerElementTypeEnum.SEND_TASK,
                  GlobalExecutionListenerElementTypeEnum.RECEIVE_TASK,
                  GlobalExecutionListenerElementTypeEnum.SCRIPT_TASK,
                  GlobalExecutionListenerElementTypeEnum.BUSINESS_RULE_TASK),
              GlobalExecutionListenerCategoryEnum.GATEWAYS,
              List.of(
                  GlobalExecutionListenerElementTypeEnum.EXCLUSIVE_GATEWAY,
                  GlobalExecutionListenerElementTypeEnum.PARALLEL_GATEWAY,
                  GlobalExecutionListenerElementTypeEnum.INCLUSIVE_GATEWAY,
                  GlobalExecutionListenerElementTypeEnum.EVENT_BASED_GATEWAY),
              GlobalExecutionListenerCategoryEnum.EVENTS,
              List.of(
                  GlobalExecutionListenerElementTypeEnum.START_EVENT,
                  GlobalExecutionListenerElementTypeEnum.END_EVENT,
                  GlobalExecutionListenerElementTypeEnum.INTERMEDIATE_CATCH_EVENT,
                  GlobalExecutionListenerElementTypeEnum.INTERMEDIATE_THROW_EVENT,
                  GlobalExecutionListenerElementTypeEnum.BOUNDARY_EVENT));

  private static <K, V> Map.Entry<K, V> entry(final K key, final V value) {
    return Map.entry(key, value);
  }

  private final IdentifierValidator identifierValidator;

  public GlobalListenerRequestValidator(final IdentifierValidator identifierValidator) {
    this.identifierValidator = identifierValidator;
  }

  public Optional<ProblemDetail> validateCreateRequest(
      final CreateGlobalTaskListenerRequest request) {
    return validate(
        () -> {
          final List<String> violations = new ArrayList<>();
          identifierValidator.validateId(request.getId(), "id", violations);
          identifierValidator.validateId(request.getType(), "type", violations);
          if (request.getEventTypes() == null || request.getEventTypes().isEmpty()) {
            violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("eventTypes"));
          }
          return violations;
        });
  }

  public Optional<ProblemDetail> validateGetRequest(final String id) {
    return validate(
        () -> {
          final List<String> violations = new ArrayList<>();
          identifierValidator.validateId(id, "id", violations);
          return violations;
        });
  }

  public Optional<ProblemDetail> validateUpdateRequest(
      final String id, final UpdateGlobalTaskListenerRequest request) {
    return validate(
        () -> {
          final List<String> violations = new ArrayList<>();
          identifierValidator.validateId(id, "id", violations);
          identifierValidator.validateId(request.getType(), "type", violations);
          if (request.getEventTypes() == null || request.getEventTypes().isEmpty()) {
            violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("eventTypes"));
          }
          return violations;
        });
  }

  public Optional<ProblemDetail> validateDeleteRequest(final String id) {
    return validate(
        () -> {
          final List<String> violations = new ArrayList<>();
          identifierValidator.validateId(id, "id", violations);
          return violations;
        });
  }

  public Optional<ProblemDetail> validateExecutionListenerCreateRequest(
      final CreateGlobalExecutionListenerRequest request) {
    return validate(
        () -> {
          final List<String> violations = new ArrayList<>();
          identifierValidator.validateId(request.getId(), "id", violations);
          identifierValidator.validateId(request.getType(), "type", violations);
          if (request.getEventTypes() == null || request.getEventTypes().isEmpty()) {
            violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("eventTypes"));
          }
          validateEventElementCompatibility(
              request.getEventTypes(),
              request.getElementTypes(),
              request.getCategories(),
              violations);
          return violations;
        });
  }

  public Optional<ProblemDetail> validateExecutionListenerUpdateRequest(
      final String id, final UpdateGlobalExecutionListenerRequest request) {
    return validate(
        () -> {
          final List<String> violations = new ArrayList<>();
          identifierValidator.validateId(id, "id", violations);
          identifierValidator.validateId(request.getType(), "type", violations);
          if (request.getEventTypes() == null || request.getEventTypes().isEmpty()) {
            violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("eventTypes"));
          }
          validateEventElementCompatibility(
              request.getEventTypes(),
              request.getElementTypes(),
              request.getCategories(),
              violations);
          return violations;
        });
  }

  private void validateEventElementCompatibility(
      final List<GlobalExecutionListenerEventTypeEnum> eventTypes,
      final List<GlobalExecutionListenerElementTypeEnum> elementTypes,
      final List<GlobalExecutionListenerCategoryEnum> categories,
      final List<String> violations) {
    if (eventTypes == null || eventTypes.isEmpty()) {
      return;
    }

    final Set<GlobalExecutionListenerElementTypeEnum> resolvedElementTypes =
        resolveElementTypes(elementTypes, categories);

    // If no element types resolved (no categories, no elementTypes), all element types apply
    if (resolvedElementTypes.isEmpty()) {
      return;
    }

    for (final var eventType : eventTypes) {
      for (final var elementType : resolvedElementTypes) {
        final var supported = SUPPORTED_EVENTS.get(elementType);
        if (supported != null && !supported.contains(eventType)) {
          violations.add(
              "Element type '%s' does not support event type '%s'."
                  .formatted(elementType.getValue(), eventType.getValue()));
        }
      }
    }
  }

  private Set<GlobalExecutionListenerElementTypeEnum> resolveElementTypes(
      final List<GlobalExecutionListenerElementTypeEnum> elementTypes,
      final List<GlobalExecutionListenerCategoryEnum> categories) {
    final Set<GlobalExecutionListenerElementTypeEnum> resolved = new HashSet<>();

    if (elementTypes != null) {
      resolved.addAll(elementTypes);
    }

    if (categories != null) {
      for (final var category : categories) {
        if (category == GlobalExecutionListenerCategoryEnum.ALL) {
          resolved.addAll(SUPPORTED_EVENTS.keySet());
        } else {
          final var expansion = CATEGORY_EXPANSIONS.get(category);
          if (expansion != null) {
            resolved.addAll(expansion);
          }
        }
      }
    }

    return resolved;
  }
}
