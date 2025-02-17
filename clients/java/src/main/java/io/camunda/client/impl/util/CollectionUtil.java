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
import java.util.List;
import java.util.Objects;

public final class CollectionUtil {

  private CollectionUtil() {}

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
