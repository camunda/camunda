/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.test.util;

import io.zeebe.util.buffer.BufferReader;
import io.zeebe.util.buffer.BufferWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.agrona.concurrent.UnsafeBuffer;
import org.hamcrest.Matcher;
import org.mockito.ArgumentMatcher;

/**
 * Note: this matcher does not work when a {@link BufferWriter} is reused throughout a test. Mockito
 * only captures the reference, so after the test the {@link BufferWriter} contains the latest
 * state.
 *
 * @author Lindhauer
 */
public final class BufferWriterMatcher<T extends BufferReader>
    implements ArgumentMatcher<BufferWriter> {
  protected final T reader;

  protected final List<BufferReaderMatch<T>> propertyMatchers = new ArrayList<>();

  public BufferWriterMatcher(final T reader) {
    this.reader = reader;
  }

  @Override
  public boolean matches(final BufferWriter argument) {
    if (argument == null) {
      return false;
    }

    final UnsafeBuffer buffer = new UnsafeBuffer(new byte[argument.getLength()]);
    argument.write(buffer, 0);

    reader.wrap(buffer, 0, buffer.capacity());

    for (final BufferReaderMatch<T> matcher : propertyMatchers) {
      if (!matcher.matches(reader)) {
        return false;
      }
    }

    return true;
  }

  public BufferWriterMatcher<T> matching(
      final Function<T, Object> actualProperty, final Object expectedValue) {
    final BufferReaderMatch<T> match = new BufferReaderMatch<>();
    match.propertyExtractor = actualProperty;

    if (expectedValue instanceof Matcher) {
      match.expectedValueMatcher = (Matcher<?>) expectedValue;
    } else {
      match.expectedValue = expectedValue;
    }

    propertyMatchers.add(match);

    return this;
  }

  public static <T extends BufferReader> BufferWriterMatcher<T> writesProperties(
      final Class<T> readerClass) {
    try {
      final BufferWriterMatcher<T> matcher = new BufferWriterMatcher<>(readerClass.newInstance());

      return matcher;
    } catch (final Exception e) {
      throw new RuntimeException("Could not construct matcher", e);
    }
  }
}
