/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.test.util;

import io.zeebe.util.buffer.BufferReader;
import java.util.function.Function;
import org.hamcrest.Matcher;

public final class BufferReaderMatch<T extends BufferReader> {
  protected Function<T, Object> propertyExtractor;
  protected Object expectedValue;
  protected Matcher<?> expectedValueMatcher;

  boolean matches(final T reader) {
    final Object actualValue = propertyExtractor.apply(reader);
    if (expectedValue != null) {
      return expectedValue.equals(actualValue);
    } else {
      return expectedValueMatcher.matches(actualValue);
    }
  }
}
