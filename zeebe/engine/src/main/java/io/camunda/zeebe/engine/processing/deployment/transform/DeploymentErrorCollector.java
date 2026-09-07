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
import io.camunda.zeebe.util.StringUtil;
import java.util.ArrayList;
import java.util.List;

/** Collects deployment errors and converts them into a {@link Failure}. */
public final class DeploymentErrorCollector {

  private static final String DEFAULT_PREFIX =
      "Expected to deploy new resources, but encountered the following errors:";

  private final int maxOutputSize;
  private final List<String> errors = new ArrayList<>();

  public DeploymentErrorCollector(final int maxOutputSize) {
    this.maxOutputSize = maxOutputSize;
  }

  public void add(final String message) {
    errors.add(StringUtil.limitString(message, maxOutputSize));
  }

  public void add(final String format, final Object... args) {
    add(String.format(format, args));
  }

  public boolean hasErrors() {
    return !errors.isEmpty();
  }

  /**
   * Formats the collected errors, capping the result at {@code maxOutputSize} characters. Each
   * individual error is itself capped at {@code maxOutputSize} when added (so one oversized
   * message, e.g. from an exception's message, can't alone blow the budget), and errors are then
   * included whole (never cut mid-line) up to the aggregate cap; any remaining errors are counted
   * and reported as omitted. The output is therefore always bounded, regardless of how many
   * resources fail at once or how large any single error message is - see
   * https://github.com/camunda/camunda/issues/61996.
   */
  public String formatMessage() {
    final var builder = new StringBuilder(DEFAULT_PREFIX);
    var includedCount = 0;

    for (final var error : errors) {
      final var withSeparator = "\n" + error;
      if (builder.length() + withSeparator.length() > maxOutputSize && includedCount > 0) {
        break;
      }
      builder.append(withSeparator);
      includedCount++;
    }

    final var omittedCount = errors.size() - includedCount;
    if (omittedCount > 0) {
      builder.append(String.format("\n... (%d more errors omitted)", omittedCount));
    }

    return builder.toString();
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
