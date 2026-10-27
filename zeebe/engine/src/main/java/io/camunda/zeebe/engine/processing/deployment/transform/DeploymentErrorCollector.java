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
  private static final String OMITTED_SUFFIX_FORMAT = "\n... (%d more errors omitted)";
  // StringUtil.limitString appends this literal "..." after truncating, so the length passed to
  // it must leave room for it - otherwise a stored entry could exceed maxOutputSize by itself.
  private static final int ELLIPSIS_LENGTH = 3;

  private final int maxOutputSize;
  private final List<String> errors = new ArrayList<>();

  public DeploymentErrorCollector(final int maxOutputSize) {
    // clamp defensively: a negative value (e.g. an operator using the "-1 disables it" convention
    // seen elsewhere in this config, unaware it doesn't apply here) would otherwise crash the
    // first call to add() via StringUtil.limitString's substring(0, negative)
    this.maxOutputSize = Math.max(maxOutputSize, 0);
  }

  public void add(final String message) {
    final var perMessageLimit = Math.max(maxOutputSize - ELLIPSIS_LENGTH, 0);
    errors.add(StringUtil.limitString(message, perMessageLimit));
  }

  public void add(final String format, final Object... args) {
    add(String.format(format, args));
  }

  public boolean hasErrors() {
    return !errors.isEmpty();
  }

  /**
   * Formats the collected errors, hard-capping the result at {@code maxOutputSize} characters -
   * this is an actual upper bound, never just approximate, since the caller relies on it to keep
   * the gRPC rejection message under proxy/ingress header-buffer limits (see
   * https://github.com/camunda/camunda/issues/61996). Errors are included whole (never cut
   * mid-line) up to the cap where possible, and any remaining errors are counted and reported as
   * omitted; the budget reserves room for that omitted-count suffix up front so it isn't itself
   * truncated in the common case. A final truncation pass guarantees the bound even in the
   * pathological case where a single forced-in error alone exceeds {@code maxOutputSize}.
   */
  public String formatMessage() {
    final var builder = new StringBuilder(DEFAULT_PREFIX);
    final var worstCaseSuffixLength = String.format(OMITTED_SUFFIX_FORMAT, errors.size()).length();
    final var budgetForErrors = maxOutputSize - worstCaseSuffixLength;

    var includedCount = 0;
    for (final var error : errors) {
      final var withSeparator = "\n" + error;
      if (builder.length() + withSeparator.length() > budgetForErrors && includedCount > 0) {
        break;
      }
      builder.append(withSeparator);
      includedCount++;
    }

    final var omittedCount = errors.size() - includedCount;
    if (omittedCount > 0) {
      builder.append(String.format(OMITTED_SUFFIX_FORMAT, omittedCount));
    }

    return builder.length() > maxOutputSize
        ? builder.substring(0, maxOutputSize)
        : builder.toString();
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
