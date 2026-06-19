/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.variable;

import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.msgpack.spec.MsgPackReader;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.util.Either;
import java.util.ArrayDeque;
import java.util.Deque;
import org.agrona.DirectBuffer;

/**
 * Validates that a msgpack variable document does not exceed a configurable maximum nesting depth.
 *
 * <p>Deep nesting (beyond the Jackson {@code StreamWriteConstraints} default of 1000 levels) would
 * break the Elasticsearch/OpenSearch exporter when it serializes the document to JSON, causing
 * partition-level export failures with no recovery path.
 *
 * <p>The depth walk is <em>iterative</em> (uses an explicit stack via {@link ArrayDeque}) and
 * therefore cannot itself overflow the call stack on deeply-nested input.
 */
public final class VariableNestingDepthValidator {

  static final String NESTING_DEPTH_EXCEEDED_ERROR_MESSAGE =
      "Expected variable document to be nested at most %d levels deep, but it exceeds that limit";
  private static final String INVALID_VARIABLES_MSGPACK_ERROR_MESSAGE =
      "Expected variables to be valid msgpack, but it could not be read: '%s'";

  private VariableNestingDepthValidator() {}

  /**
   * Validates that the given msgpack document does not exceed {@code maxNestingDepth} levels of
   * container nesting.
   *
   * @param variablesBuffer the msgpack document to validate
   * @param maxNestingDepth the maximum allowed nesting depth (inclusive)
   * @return {@link Either#right(Object)} when within the limit; {@link Either#left(Object)} with a
   *     {@link RejectionType#INVALID_ARGUMENT} rejection when the limit is exceeded
   */
  public static Either<Rejection, Void> validate(
      final DirectBuffer variablesBuffer, final int maxNestingDepth) {
    if (isEmpty(variablesBuffer)) {
      return Either.right(null);
    }
    try {
      if (exceedsMaxDepth(variablesBuffer, maxNestingDepth)) {
        return Either.left(
            new Rejection(
                RejectionType.INVALID_ARGUMENT,
                NESTING_DEPTH_EXCEEDED_ERROR_MESSAGE.formatted(maxNestingDepth)));
      }
    } catch (final RuntimeException exception) {
      return Either.left(
          new Rejection(
              RejectionType.INVALID_ARGUMENT,
              INVALID_VARIABLES_MSGPACK_ERROR_MESSAGE.formatted(exception.getMessage())));
    }
    return Either.right(null);
  }

  /**
   * Returns {@code true} if the msgpack buffer's container nesting depth exceeds {@code
   * maxNestingDepth}; {@code false} otherwise. Short-circuits as soon as the limit is exceeded.
   *
   * <p>Uses an iterative algorithm with an explicit stack to avoid {@link StackOverflowError} on
   * deeply nested input.
   */
  static boolean exceedsMaxDepth(final DirectBuffer buffer, final int maxNestingDepth) {
    final var reader = new MsgPackReader();
    reader.wrap(buffer, 0, buffer.capacity());

    // Each stack entry holds the number of tokens that still remain at the parent depth level once
    // we finish the current container. We track this per-level so that ascending back to the parent
    // restores the correct remaining count.
    final Deque<Long> stack = new ArrayDeque<>();
    long remaining = 1; // one root token to read
    int depth = 0;

    while (true) {
      // Ascend past any fully-consumed levels.
      while (remaining == 0) {
        if (stack.isEmpty()) {
          return false;
        }
        remaining = stack.pop();
        depth--;
      }

      remaining--;
      final var token = reader.readToken();

      switch (token.getType()) {
        case MAP -> {
          depth++;
          if (depth > maxNestingDepth) {
            return true;
          }
          stack.push(remaining);
          // A map with N entries contains N key tokens + N value tokens.
          remaining = (long) token.getSize() * 2;
        }
        case ARRAY -> {
          depth++;
          if (depth > maxNestingDepth) {
            return true;
          }
          stack.push(remaining);
          remaining = token.getSize();
        }
        default -> {
          // Scalar value: nothing to descend into.
        }
      }
    }
  }

  private static boolean isEmpty(final DirectBuffer buffer) {
    return buffer == null || buffer.capacity() == 0;
  }
}
