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
package io.camunda.client.impl.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class CollectionUtil {

  private CollectionUtil() {}

  /**
   * Copies a list that may be absent into an unmodifiable one, mapping every element. An absent
   * list becomes an empty list, so that a response getter never hands a caller null.
   */
  public static <S, T> List<T> mapToUnmodifiableList(
      final List<S> source, final Function<S, T> mapper) {
    return source == null
        ? Collections.emptyList()
        : Collections.unmodifiableList(source.stream().map(mapper).collect(Collectors.toList()));
  }

  /**
   * Copies a list that may be absent into an unmodifiable one. An absent list becomes an empty
   * list, so that a response getter never hands a caller null.
   */
  public static <T> List<T> toUnmodifiableList(final List<T> source) {
    return mapToUnmodifiableList(source, Function.identity());
  }

  public static <T> List<T> addValuesToList(final List<T> list, final List<T> values) {
    final List<T> result;
    if (list == null) {
      result = Objects.requireNonNull(values);
    } else {
      result = new ArrayList<>(list);
      result.addAll(values);
    }
    return result;
  }

  public static <T> List<T> toList(final T... values) {
    final List<T> collectedValues = new ArrayList<>();
    if (values != null && values.length > 0) {
      collectedValues.addAll(Arrays.asList(values));
    }
    return collectedValues;
  }
}
