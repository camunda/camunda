/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.test.util;

import io.zeebe.util.buffer.BufferReader;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.hamcrest.Matcher;
import org.mockito.ArgumentMatcher;

/**
 * Note: This matcher does not behave as expected when the BufferReader is reused; this would
 * require us to clone the buffer reader's state at the time of invocation
 *
 * @author Lindhauer
 * @param <T>
 */
public final class BufferReaderMatcher<T extends BufferReader> implements ArgumentMatcher<T> {
  protected final List<BufferReaderMatch<T>> propertyMatchers = new ArrayList<>();

  @Override
  @SuppressWarnings("unchecked")
  public boolean matches(final T argument) {
    if (argument == null) {
      return false;
    }

    for (final BufferReaderMatch<T> matcher : propertyMatchers) {
      if (!matcher.matches(argument)) {
        return false;
      }
    }

    return true;
  }

  public BufferReaderMatcher<T> matching(
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

  public static <T extends BufferReader> BufferReaderMatcher<T> readsProperties() {
    return new BufferReaderMatcher<>();
  }
}
