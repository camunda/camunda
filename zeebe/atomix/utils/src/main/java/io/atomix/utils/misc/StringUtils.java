/*
 * Copyright 2019-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.utils.misc;

import com.google.common.base.MoreObjects;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/** Collection of various helper methods to manipulate strings. */
public final class StringUtils {

  private StringUtils() {}

  /**
   * Splits the input string with the given regex and filters empty strings.
   *
   * @param input the string to split.
   * @return the array of strings computed by splitting this string
   */
  public static String[] split(final String input, final String regex) {
    if (input == null) {
      return null;
    }
    final String[] arr = input.split(regex);
    final List<String> results = new ArrayList<>(arr.length);
    for (final String a : arr) {
      if (!a.trim().isEmpty()) {
        results.add(a);
      }
    }
    return results.toArray(new String[0]);
  }

  /**
   * Small utility to print diagnostic information about {@link ByteBuffer}
   *
   * @param buffer the buffer to print-out
   * @return diagnostic information about the buffer
   */
  public static String printShortBuffer(final ByteBuffer buffer) {
    if (buffer == null) {
      return "null";
    }

    return MoreObjects.toStringHelper(buffer.getClass())
        .add("position", buffer.position())
        .add("remaining", buffer.remaining())
        .add("limit", buffer.limit())
        .add("capacity", buffer.capacity())
        .add("mark", buffer.mark())
        .add("hash", buffer.hashCode())
        .toString();
  }
}
