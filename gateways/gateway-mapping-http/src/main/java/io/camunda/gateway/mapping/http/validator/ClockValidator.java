/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.validator;

import static io.camunda.gateway.mapping.http.validator.ErrorMessages.ERROR_MESSAGE_EMPTY_ATTRIBUTE;
import static io.camunda.gateway.mapping.http.validator.ErrorMessages.ERROR_MESSAGE_INVALID_ATTRIBUTE_VALUE;
import static io.camunda.gateway.mapping.http.validator.RequestValidator.validate;
import static io.camunda.gateway.mapping.http.validator.RequestValidator.validateDuration;

import io.camunda.gateway.protocol.model.ClockAddRequest;
import io.camunda.gateway.protocol.model.ClockPinRequest;
import java.time.Duration;
import java.util.Optional;
import org.springframework.http.ProblemDetail;

public class ClockValidator {

  private static final String OFFSET = "offset";

  public static Optional<ProblemDetail> validateClockAddRequest(final ClockAddRequest addRequest) {
    return validate(
        violations -> {
          final String offset = addRequest.getOffset();
          if (offset == null || offset.isBlank()) {
            violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted(OFFSET));
            return;
          }

          final Duration duration = validateDuration(offset, OFFSET, violations);
          if (duration != null && !fitsIntoMillis(duration)) {
            violations.add(
                ERROR_MESSAGE_INVALID_ATTRIBUTE_VALUE.formatted(
                    OFFSET, offset, "small enough to fit into a 64-bit number of milliseconds"));
          }
        });
  }

  public static Optional<ProblemDetail> validateClockPinRequest(final ClockPinRequest pinRequest) {

    return validate(
        violations ->
            Optional.ofNullable(pinRequest.getTimestamp())
                .ifPresentOrElse(
                    timestamp -> {
                      if (timestamp < 0) {
                        violations.add(
                            ERROR_MESSAGE_INVALID_ATTRIBUTE_VALUE.formatted(
                                "timestamp", timestamp, "not negative"));
                      }
                    },
                    () -> violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("timestamp"))));
  }

  private static boolean fitsIntoMillis(final Duration duration) {
    try {
      final var unused = duration.toMillis();
      return true;
    } catch (final ArithmeticException e) {
      return false;
    }
  }
}
