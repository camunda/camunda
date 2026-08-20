/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.transform;

import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.util.Either;

/** Collects deployment errors and converts them into a {@link Failure}. */
public final class DeploymentErrorCollector {

  private static final String DEFAULT_PREFIX =
      "Expected to deploy new resources, but encountered the following errors:";

  private final StringBuilder errors = new StringBuilder();

  public void add(final String message) {
    errors.append("\n").append(message);
  }

  public void add(final String format, final Object... args) {
    errors.append("\n").append(String.format(format, args));
  }

  public boolean hasErrors() {
    return !errors.isEmpty();
  }

  public String formatMessage() {
    return DEFAULT_PREFIX + errors;
  }

  public <T> Either<Failure, T> toEither(final T value) {
    if (hasErrors()) {
      return Either.left(new Failure(formatMessage()));
    }
    return Either.right(value);
  }

  public Either<Failure, Void> toEither() {
    if (hasErrors()) {
      return Either.left(new Failure(formatMessage()));
    }
    return Either.right(null);
  }
}
