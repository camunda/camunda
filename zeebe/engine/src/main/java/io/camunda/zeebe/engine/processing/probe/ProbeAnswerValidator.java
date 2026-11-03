/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.probe;

import io.camunda.zeebe.protocol.impl.record.value.probe.ProbeRecord;
import io.camunda.zeebe.protocol.record.value.ProbeOutcome;
import java.util.Optional;

/**
 * Checks the body of an answer against the envelope the client receives. The data is opaque to the
 * engine: what it holds is agreed between the client and the worker of the probe ID.
 */
final class ProbeAnswerValidator {

  static final int MAX_MESSAGE_LENGTH = 500;

  private ProbeAnswerValidator() {}

  /** Returns why the answer is invalid, or empty if it is valid. */
  static Optional<String> validate(final ProbeRecord answer) {
    if (answer.getOutcome() == ProbeOutcome.UNSPECIFIED) {
      return Optional.of(
          "Expected the outcome of the answer to be one of [OK, UNSUPPORTED, ERROR], but none was given.");
    }
    if (answer.getMessage().length() > MAX_MESSAGE_LENGTH) {
      return Optional.of(
          "Expected the message of the answer to be at most %d characters, but it was %d."
              .formatted(MAX_MESSAGE_LENGTH, answer.getMessage().length()));
    }
    return Optional.empty();
  }
}
