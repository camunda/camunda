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
package io.camunda.client.impl.worker.probe;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Removes the values that the cluster injected into a probe's input, at the leaves named by its
 * secret pointers (JSON pointers, RFC 6901). A leaf that mixes a secret with other text is removed
 * whole; a text that holds only part of such a leaf is not caught.
 */
final class SecretRedaction {

  static final String REDACTED = "***";

  private final Map<String, Object> input;
  private final List<String> pointers;
  private final List<String> values;

  SecretRedaction(final Map<String, Object> input, final List<String> pointers) {
    this.input = input;
    this.pointers = pointers;
    values = injectedValues(input, pointers);
  }

  /** Replaces every injected value in the text, longest first, so no part of one is left. */
  String removeFrom(final String text) {
    String redacted = text;
    for (final String value : values) {
      redacted = redacted.replace(value, REDACTED);
    }
    return redacted;
  }

  /**
   * @return a copy of the input with the leaves named by the pointers replaced
   */
  Object redactedInput() {
    Object copy = deepCopy(input);
    for (final String pointer : pointers) {
      copy = replaceAt(copy, tokens(pointer), 0);
    }
    return copy;
  }

  private static List<String> injectedValues(
      final Map<String, Object> input, final List<String> pointers) {
    final Set<String> values = new LinkedHashSet<>();
    for (final String pointer : pointers) {
      collectLeaves(valueAt(input, tokens(pointer)), values);
    }
    values.remove("");
    final List<String> sorted = new ArrayList<>(values);
    sorted.sort(Comparator.comparingInt(String::length).reversed());
    return sorted;
  }

  private static Object valueAt(final Object document, final List<String> tokens) {
    Object node = document;
    for (final String token : tokens) {
      node = child(node, token);
      if (node == null) {
        return null;
      }
    }
    return node;
  }

  private static Object child(final Object node, final String token) {
    if (node instanceof Map) {
      return ((Map<?, ?>) node).get(token);
    }
    if (node instanceof List) {
      final int index = indexOf(token);
      final List<?> list = (List<?>) node;
      return index >= 0 && index < list.size() ? list.get(index) : null;
    }
    return null;
  }

  private static void collectLeaves(final Object node, final Set<String> values) {
    if (node == null) {
      return;
    }
    if (node instanceof Map) {
      ((Map<?, ?>) node).values().forEach(value -> collectLeaves(value, values));
    } else if (node instanceof Collection) {
      ((Collection<?>) node).forEach(value -> collectLeaves(value, values));
    } else {
      values.add(String.valueOf(node));
    }
  }

  @SuppressWarnings("unchecked")
  private static Object replaceAt(final Object node, final List<String> tokens, final int depth) {
    if (depth == tokens.size()) {
      return REDACTED;
    }
    final String token = tokens.get(depth);
    if (node instanceof Map) {
      final Map<String, Object> map = (Map<String, Object>) node;
      if (map.containsKey(token)) {
        map.put(token, replaceAt(map.get(token), tokens, depth + 1));
      }
    } else if (node instanceof List) {
      final List<Object> list = (List<Object>) node;
      final int index = indexOf(token);
      if (index >= 0 && index < list.size()) {
        list.set(index, replaceAt(list.get(index), tokens, depth + 1));
      }
    }
    return node;
  }

  private static Object deepCopy(final Object node) {
    if (node instanceof Map) {
      final Map<String, Object> copy = new LinkedHashMap<>();
      ((Map<?, ?>) node).forEach((key, value) -> copy.put(String.valueOf(key), deepCopy(value)));
      return copy;
    }
    if (node instanceof Collection) {
      final List<Object> copy = new ArrayList<>();
      ((Collection<?>) node).forEach(value -> copy.add(deepCopy(value)));
      return copy;
    }
    return node;
  }

  /** Splits a JSON pointer into its reference tokens, unescaped; "" is the whole document. */
  private static List<String> tokens(final String pointer) {
    final List<String> tokens = new ArrayList<>();
    if (pointer == null || pointer.isEmpty()) {
      return tokens;
    }
    final String[] parts = pointer.substring(pointer.startsWith("/") ? 1 : 0).split("/", -1);
    for (final String part : parts) {
      tokens.add(part.replace("~1", "/").replace("~0", "~"));
    }
    return tokens;
  }

  private static int indexOf(final String token) {
    try {
      return Integer.parseInt(token);
    } catch (final NumberFormatException e) {
      return -1;
    }
  }
}
