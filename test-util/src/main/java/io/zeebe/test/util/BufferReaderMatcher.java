/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
public class BufferReaderMatcher<T extends BufferReader> implements ArgumentMatcher<T> {
  protected List<BufferReaderMatch<T>> propertyMatchers = new ArrayList<>();

  @Override
  @SuppressWarnings("unchecked")
  public boolean matches(T argument) {
    if (argument == null) {
      return false;
    }

    for (BufferReaderMatch<T> matcher : propertyMatchers) {
      if (!matcher.matches(argument)) {
        return false;
      }
    }

    return true;
  }

  public BufferReaderMatcher<T> matching(Function<T, Object> actualProperty, Object expectedValue) {
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
