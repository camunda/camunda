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
import static io.camunda.gateway.mapping.http.validator.ErrorMessages.ERROR_MESSAGE_TOO_MANY_CHARACTERS;
import static io.camunda.gateway.mapping.http.validator.RequestValidator.validate;

import io.camunda.gateway.protocol.model.ProbeActivationRequest;
import io.camunda.gateway.protocol.model.ProbeAnswerRequest;
import io.camunda.gateway.protocol.model.ProbeCreationRequest;
import io.camunda.gateway.protocol.model.ProbeErrorAnswerRequest;
import io.camunda.gateway.protocol.model.ProbeOkAnswerRequest;
import io.camunda.service.ProbeServices;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ProblemDetail;

/**
 * Rejects a malformed probe request before it reaches the engine (spec section 4). The rules on the
 * probe ID and the request timeout are the engine's: the engine checks them again, so a request
 * that passes here is never rejected for them there.
 */
@NullMarked
public final class ProbeRequestValidator {

  /** The longest probe ID (D54). */
  public static final int MAX_PROBE_ID_LENGTH = 256;

  /** The most probe IDs one poll lists (D54). */
  public static final int MAX_PROBE_IDS = 100;

  /** The longest message of an {@code ERROR} answer (D30). */
  public static final int MAX_MESSAGE_LENGTH = 500;

  private static final String VALID_PROBE_ID =
      "1 to 256 characters, without whitespace, and not '*'";

  private ProbeRequestValidator() {}

  public static Optional<ProblemDetail> validateProbeCreationRequest(
      final ProbeCreationRequest request) {
    return validate(
        violations -> {
          final @Nullable String probeId = request.getProbeId();
          if (probeId == null || probeId.isEmpty()) {
            violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("probeId"));
          } else if (!isValidProbeId(probeId)) {
            violations.add(
                ERROR_MESSAGE_INVALID_ATTRIBUTE_VALUE.formatted(
                    "probeId", probeId, VALID_PROBE_ID));
          }
          final @Nullable String input = request.getInput();
          if (input == null || input.isBlank()) {
            violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("input"));
          }
          final @Nullable Long requestTimeout = request.getRequestTimeout();
          final long maxRequestTimeout = ProbeServices.MAX_REQUEST_TIMEOUT.toMillis();
          if (requestTimeout != null
              && (requestTimeout < 0 || requestTimeout > maxRequestTimeout)) {
            violations.add(
                ERROR_MESSAGE_INVALID_ATTRIBUTE_VALUE.formatted(
                    "requestTimeout",
                    requestTimeout,
                    "between 0 and %d".formatted(maxRequestTimeout)));
          }
        });
  }

  public static Optional<ProblemDetail> validateProbeActivationRequest(
      final ProbeActivationRequest request) {
    return validate(
        violations -> {
          final @Nullable List<String> probeIds = request.getProbeIds();
          if (probeIds == null || probeIds.isEmpty()) {
            violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("probeIds"));
          } else {
            if (probeIds.size() > MAX_PROBE_IDS) {
              violations.add(
                  ERROR_MESSAGE_INVALID_ATTRIBUTE_VALUE.formatted(
                      "probeIds",
                      "%d values".formatted(probeIds.size()),
                      "at most %d values".formatted(MAX_PROBE_IDS)));
            }
            final var seen = new HashSet<String>();
            for (int i = 0; i < probeIds.size(); i++) {
              final @Nullable String probeId = probeIds.get(i);
              if (probeId == null || !isValidProbeId(probeId)) {
                violations.add(
                    ERROR_MESSAGE_INVALID_ATTRIBUTE_VALUE.formatted(
                        "probeIds[%d]".formatted(i), probeId, VALID_PROBE_ID));
              } else if (!seen.add(probeId)) {
                violations.add(
                    ERROR_MESSAGE_INVALID_ATTRIBUTE_VALUE.formatted(
                        "probeIds[%d]".formatted(i), probeId, "distinct from the other probe IDs"));
              }
            }
          }
          final @Nullable Integer maxProbesToActivate = request.getMaxProbesToActivate();
          if (maxProbesToActivate == null) {
            violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("maxProbesToActivate"));
          } else if (maxProbesToActivate < 1) {
            violations.add(
                ERROR_MESSAGE_INVALID_ATTRIBUTE_VALUE.formatted(
                    "maxProbesToActivate", maxProbesToActivate, "greater than 0"));
          }
        });
  }

  /**
   * Checks the field that the outcome requires (D59). The fields that do not fit the outcome never
   * get here: the body is read into the shape of its outcome, which has no such field, so it is
   * rejected as an unknown property.
   */
  public static Optional<ProblemDetail> validateProbeAnswerRequest(
      final ProbeAnswerRequest request) {
    return validate(
        violations -> {
          if (request instanceof final ProbeOkAnswerRequest ok && ok.getData() == null) {
            // an empty object is a valid answer: only the worker knows what its data means (D48)
            violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("data"));
          } else if (request instanceof final ProbeErrorAnswerRequest error) {
            final @Nullable String message = error.getMessage();
            if (message == null || message.isEmpty()) {
              violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("message"));
            } else if (message.length() > MAX_MESSAGE_LENGTH) {
              violations.add(
                  ERROR_MESSAGE_TOO_MANY_CHARACTERS.formatted("message", MAX_MESSAGE_LENGTH));
            }
          }
        });
  }

  /**
   * A probe ID the engine accepts (D54): 1 to {@link #MAX_PROBE_ID_LENGTH} characters, none of them
   * whitespace by {@link Character#isWhitespace}, and not {@code *}, the authorization wildcard.
   */
  private static boolean isValidProbeId(final String probeId) {
    return !probeId.isEmpty()
        && probeId.length() <= MAX_PROBE_ID_LENGTH
        && probeId.chars().noneMatch(Character::isWhitespace)
        && !"*".equals(probeId);
  }
}
