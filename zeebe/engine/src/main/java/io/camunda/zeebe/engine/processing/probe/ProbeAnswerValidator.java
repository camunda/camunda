/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.probe;

import io.camunda.zeebe.protocol.impl.record.value.probe.ProbeRecord;
import io.camunda.zeebe.protocol.record.value.ProbeOperation;
import io.camunda.zeebe.protocol.record.value.ProbeOutcome;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Checks the body of an answer against the envelope the client receives. For {@code
 * VALIDATE_CONFIGURATION}, the data of an OK answer is the connector SDK's {@code
 * ConfigurationValidationResult}, and nothing else.
 */
final class ProbeAnswerValidator {

  static final int MAX_MESSAGE_LENGTH = 500;

  private static final Set<String> VALIDATION_RESULT_FIELDS = Set.of("status", "code", "message");
  private static final Set<String> VALIDATION_STATUSES =
      Set.of("SUCCESS", "FAILURE", "UNSUPPORTED");

  private ProbeAnswerValidator() {}

  /** Returns why the answer is invalid, or empty if it is valid. */
  static Optional<String> validate(final ProbeOperation operation, final ProbeRecord answer) {
    final var outcome = answer.getOutcome();
    if (outcome == ProbeOutcome.UNSPECIFIED) {
      return Optional.of(
          "Expected the outcome of the answer to be one of [OK, UNSUPPORTED, ERROR], but none was given.");
    }
    if (answer.getMessage().length() > MAX_MESSAGE_LENGTH) {
      return Optional.of(
          "Expected the message of the answer to be at most %d characters, but it was %d."
              .formatted(MAX_MESSAGE_LENGTH, answer.getMessage().length()));
    }
    if (outcome != ProbeOutcome.OK) {
      return Optional.empty();
    }
    return switch (operation) {
      case VALIDATE_CONFIGURATION -> validateConfigurationResult(answer.getData());
    };
  }

  private static Optional<String> validateConfigurationResult(final Map<String, Object> data) {
    if (data.isEmpty()) {
      return Optional.of(
          "Expected an OK answer to carry the validation result as data, but it had none.");
    }
    if (!VALIDATION_RESULT_FIELDS.containsAll(data.keySet())) {
      return Optional.of(
          "Expected the validation result to have only the fields %s, but it had %s."
              .formatted(VALIDATION_RESULT_FIELDS, data.keySet()));
    }
    if (!VALIDATION_STATUSES.contains(String.valueOf(data.get("status")))) {
      return Optional.of(
          "Expected the status of the validation result to be one of %s, but it was '%s'."
              .formatted(VALIDATION_STATUSES, data.get("status")));
    }
    return Optional.empty();
  }
}
