/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.globallistener;

import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.state.globallistener.GlobalListenersState;
import io.camunda.zeebe.protocol.impl.record.value.globallistener.GlobalListenerRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.value.GlobalListenerType;
import io.camunda.zeebe.util.Either;
import java.util.Set;
import java.util.stream.Collectors;

public class GlobalListenerValidator {
  private static final String LISTENER_NOT_EXISTS_ERROR_MESSAGE =
      "Expected a global %s listener with id '%s' to exist, but it was not found";
  private static final String LISTENER_EXISTS_ERROR_MESSAGE =
      "Expected a global %s listener with id '%s' to not exist, but a listener with the same id was found";
  private static final String MISSING_ID_ERROR_MESSAGE =
      "Missing id for the provided global %s listener";
  private static final String MISSING_TYPE_ERROR_MESSAGE =
      "Missing type for the provided global %s listener with id '%s'";
  private static final String MISSING_EVENT_TYPES_ERROR_MESSAGE =
      "Missing event types for the provided global %s listener with id '%s'";
  private static final String INVALID_EVENT_TYPE_ERROR_MESSAGE =
      "Invalid event types for the provided global %s listener with id '%s': %s";
  private static final String INVALID_ELEMENT_TYPES_ERROR_MESSAGE =
      "Invalid element types for the provided global %s listener with id '%s': %s";
  private static final String INVALID_CATEGORIES_ERROR_MESSAGE =
      "Invalid categories for the provided global %s listener with id '%s': %s";
  private static final String MISSING_ELEMENT_SCOPE_ERROR_MESSAGE =
      "At least one element type or category must be specified for the provided global %s listener with id '%s'";

  public GlobalListenerValidator() {}

  public Either<Rejection, GlobalListenerRecord> resolveExistingListener(
      final GlobalListenerRecord record, final GlobalListenersState globalListenersState) {
    final var existingListener =
        globalListenersState.getGlobalListener(record.getListenerType(), record.getId());
    if (existingListener == null) {
      final var message =
          LISTENER_NOT_EXISTS_ERROR_MESSAGE.formatted(record.getListenerType(), record.getId());
      return Either.left(new Rejection(RejectionType.NOT_FOUND, message));
    }
    // Set the global listener key in the record from the information in the state
    record.setGlobalListenerKey(existingListener.getGlobalListenerKey());
    return Either.right(record);
  }

  public Either<Rejection, GlobalListenerRecord> listenerDoesNotExist(
      final GlobalListenerRecord record, final GlobalListenersState globalListenersState) {
    final var existingListener =
        globalListenersState.getGlobalListener(record.getListenerType(), record.getId());
    if (existingListener != null) {
      final var message =
          LISTENER_EXISTS_ERROR_MESSAGE.formatted(record.getListenerType(), record.getId());
      return Either.left(new Rejection(RejectionType.ALREADY_EXISTS, message));
    }
    return Either.right(record);
  }

  public Either<Rejection, GlobalListenerRecord> idProvided(final GlobalListenerRecord record) {
    if (record.getId().isBlank()) {
      final var message = MISSING_ID_ERROR_MESSAGE.formatted(record.getListenerType());
      return Either.left(new Rejection(RejectionType.INVALID_ARGUMENT, message));
    }
    return Either.right(record);
  }

  public Either<Rejection, GlobalListenerRecord> typeProvided(final GlobalListenerRecord record) {
    if (record.getType().isBlank()) {
      final var message =
          MISSING_TYPE_ERROR_MESSAGE.formatted(record.getListenerType(), record.getId());
      return Either.left(new Rejection(RejectionType.INVALID_ARGUMENT, message));
    }
    return Either.right(record);
  }

  public Either<Rejection, GlobalListenerRecord> eventTypesProvided(
      final GlobalListenerRecord record) {
    if (record.getEventTypes() == null || record.getEventTypes().isEmpty()) {
      final var message =
          MISSING_EVENT_TYPES_ERROR_MESSAGE.formatted(record.getListenerType(), record.getId());
      return Either.left(new Rejection(RejectionType.INVALID_ARGUMENT, message));
    }
    return Either.right(record);
  }

  public Either<Rejection, GlobalListenerRecord> validEventTypes(
      final GlobalListenerRecord record) {
    final Set<String> invalidEventTypes =
        record.getEventTypes().stream()
            .filter(eventType -> !isValidEventType(record, eventType))
            .collect(Collectors.toSet());
    if (!invalidEventTypes.isEmpty()) {
      final var message =
          INVALID_EVENT_TYPE_ERROR_MESSAGE.formatted(
              record.getListenerType(),
              record.getId(),
              invalidEventTypes.stream().collect(Collectors.joining("', '", "'", "'")));
      return Either.left(new Rejection(RejectionType.INVALID_ARGUMENT, message));
    }
    return Either.right(record);
  }

  public boolean isValidEventType(final GlobalListenerRecord record, final String eventType) {
    if (GlobalListenerRecord.ALL_EVENT_TYPES.equals(eventType)) {
      return true;
    }
    if (record.getListenerType() == GlobalListenerType.EXECUTION_LISTENER) {
      return GlobalListenerRecord.EXECUTION_LISTENER_EVENT_TYPES.contains(eventType);
    }
    return GlobalListenerRecord.TASK_LISTENER_EVENT_TYPES.contains(eventType);
  }

  public Either<Rejection, GlobalListenerRecord> validElementTypes(
      final GlobalListenerRecord record) {
    if (record.getListenerType() != GlobalListenerType.EXECUTION_LISTENER) {
      return Either.right(record);
    }
    final var elementTypes = record.getElementTypes();
    if (elementTypes == null || elementTypes.isEmpty()) {
      return Either.right(record);
    }
    final Set<String> invalidTypes =
        elementTypes.stream()
            .filter(
                et ->
                    !GlobalListenerRecord.ALL_EVENT_TYPES.equals(et)
                        && !GlobalListenerRecord.VALID_ELEMENT_TYPES.contains(et))
            .collect(Collectors.toSet());
    if (!invalidTypes.isEmpty()) {
      final var message =
          INVALID_ELEMENT_TYPES_ERROR_MESSAGE.formatted(
              record.getListenerType(),
              record.getId(),
              invalidTypes.stream().collect(Collectors.joining("', '", "'", "'")));
      return Either.left(new Rejection(RejectionType.INVALID_ARGUMENT, message));
    }
    return Either.right(record);
  }

  public Either<Rejection, GlobalListenerRecord> validCategories(
      final GlobalListenerRecord record) {
    if (record.getListenerType() != GlobalListenerType.EXECUTION_LISTENER) {
      return Either.right(record);
    }
    final var categories = record.getCategories();
    if (categories == null || categories.isEmpty()) {
      return Either.right(record);
    }
    final Set<String> invalidCategories =
        categories.stream()
            .filter(cat -> !GlobalListenerRecord.VALID_CATEGORIES.contains(cat))
            .collect(Collectors.toSet());
    if (!invalidCategories.isEmpty()) {
      final var message =
          INVALID_CATEGORIES_ERROR_MESSAGE.formatted(
              record.getListenerType(),
              record.getId(),
              invalidCategories.stream().collect(Collectors.joining("', '", "'", "'")));
      return Either.left(new Rejection(RejectionType.INVALID_ARGUMENT, message));
    }
    return Either.right(record);
  }

  public Either<Rejection, GlobalListenerRecord> elementScopeProvided(
      final GlobalListenerRecord record) {
    if (record.getListenerType() != GlobalListenerType.EXECUTION_LISTENER) {
      return Either.right(record);
    }
    final var elementTypes = record.getElementTypes();
    final var categories = record.getCategories();
    final boolean hasElementTypes = elementTypes != null && !elementTypes.isEmpty();
    final boolean hasCategories = categories != null && !categories.isEmpty();
    if (!hasElementTypes && !hasCategories) {
      final var message =
          MISSING_ELEMENT_SCOPE_ERROR_MESSAGE.formatted(record.getListenerType(), record.getId());
      return Either.left(new Rejection(RejectionType.INVALID_ARGUMENT, message));
    }
    return Either.right(record);
  }
}
