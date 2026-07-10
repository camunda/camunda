/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.util.validation;

import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.msgpack.spec.MsgPackReader;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.util.Either;
import java.util.ArrayDeque;
import java.util.Deque;
import org.agrona.DirectBuffer;

/**
 * Validates that a msgpack document does not exceed a configurable maximum nesting depth.
 *
 * <p>Deep nesting (beyond the Jackson {@code StreamWriteConstraints} default of 1000 levels) would
 * break the Elasticsearch/OpenSearch exporter when it serializes the document to JSON, causing
 * partition-level export failures with no recovery path.
 *
 * <p>The depth walk is <em>iterative</em> (uses an explicit stack via {@link ArrayDeque}) and
 * therefore cannot itself overflow the call stack on deeply-nested input.
 */
public final class NestingDepthValidator {

  static final String NESTING_DEPTH_EXCEEDED_ERROR_MESSAGE =
      "Expected document to be nested at most %d levels deep, but it exceeds that limit";
  private static final String INVALID_MSGPACK_ERROR_MESSAGE =
      "Expected to be valid msgpack, but it could not be read: '%s'";

  private NestingDepthValidator() {}

  /**
   * Validates that the given msgpack document does not exceed {@code maxNestingDepth} levels of
   * container nesting.
   *
   * @param buffer the msgpack document to validate
   * @param maxNestingDepth the maximum allowed nesting depth (inclusive)
   * @return {@link Either#right(Object)} when within the limit; {@link Either#left(Object)} with a
   *     {@link Failure} when the limit is exceeded
   */
  public static Either<Failure, Void> validate(
      final DirectBuffer buffer, final int maxNestingDepth) {
    if (isEmpty(buffer)) {
      return Either.right(null);
    }
    try {
      if (exceedsMaxDepth(buffer, maxNestingDepth)) {
        return Either.left(
            new Failure(
                NESTING_DEPTH_EXCEEDED_ERROR_MESSAGE.formatted(maxNestingDepth),
                ErrorType.IO_MAPPING_ERROR));
      }
    } catch (final RuntimeException exception) {
      return Either.left(
          new Failure(
              INVALID_MSGPACK_ERROR_MESSAGE.formatted(exception.getMessage()),
              ErrorType.IO_MAPPING_ERROR));
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

    // Stack entries save the parent's remaining token count so we can restore it when ascending.
    final Deque<Long> stack = new ArrayDeque<>();
    long remaining = 1; // one root token to read
    int depth = 0;

    while (remaining > 0 || !stack.isEmpty()) {
      if (remaining == 0) {
        // Ascend one level; outer condition re-checks until a non-empty level is found.
        remaining = stack.pop();
        depth--;
        continue;
      }

      remaining--;
      final var token = reader.readToken();

      final long childTokenCount;
      switch (token.getType()) {
        // A map with N entries contains N key tokens + N value tokens.
        case MAP -> childTokenCount = (long) token.getSize() * 2;
        case ARRAY -> childTokenCount = token.getSize();
        default -> childTokenCount = -1; // scalar value: nothing to descend into
      }

      if (childTokenCount >= 0) {
        depth++;
        if (depth > maxNestingDepth) {
          return true;
        }
        stack.push(remaining);
        remaining = childTokenCount;
      }
    }
    return false;
  }

  private static boolean isEmpty(final DirectBuffer buffer) {
    return buffer == null || buffer.capacity() == 0;
  }
}
