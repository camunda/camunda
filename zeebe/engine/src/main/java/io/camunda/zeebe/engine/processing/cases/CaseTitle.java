/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.cases;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import java.util.Optional;
import java.util.function.Supplier;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;

/**
 * Determines the title of a case: the value of a top-level variable, or a fallback if that variable
 * does not hold a usable title. A string, number or boolean counts as a title, as long as its text
 * is not blank; a missing variable, a {@code null}, an object or a list all lead to the fallback.
 * Variables nested in other variables are not considered.
 *
 * <p>The result, whichever source it comes from, is truncated to {@link #MAX_LENGTH} characters.
 */
@NullMarked
public final class CaseTitle {

  public static final int MAX_LENGTH = 256;

  private CaseTitle() {}

  /**
   * @param titleVariable the name of the variable that holds the title
   * @param variables a msgpack document with the variables to look the title up in
   * @param fallback provides the title if no usable variable is found; only called in that case
   * @return the title, at most {@link #MAX_LENGTH} characters long
   */
  public static String of(
      final DirectBuffer titleVariable,
      final DirectBuffer variables,
      final Supplier<String> fallback) {
    final var title = findTitle(titleVariable, variables).orElseGet(fallback);
    return title.substring(0, Math.min(title.length(), MAX_LENGTH));
  }

  private static Optional<String> findTitle(final DirectBuffer name, final DirectBuffer document) {
    if (document.capacity() == 0) {
      return Optional.empty();
    }

    final var value = MsgPackConverter.convertToMap(document).get(bufferAsString(name));
    if (value instanceof String || value instanceof Number || value instanceof Boolean) {
      return Optional.of(String.valueOf(value)).filter(title -> !title.isBlank());
    }
    return Optional.empty();
  }
}
