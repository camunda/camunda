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
package io.zeebe.util;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class StringUtil {

  public static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;

  public static byte[] getBytes(final String value) {
    return getBytes(value, DEFAULT_CHARSET);
  }

  public static byte[] getBytes(final String value, final Charset charset) {
    return value.getBytes(charset);
  }

  public static String fromBytes(final byte[] bytes) {
    return fromBytes(bytes, DEFAULT_CHARSET);
  }

  public static String fromBytes(final byte[] bytes, final Charset charset) {
    return new String(bytes, charset);
  }

  public static String formatStackTrace(StackTraceElement[] trace) {
    final StringBuilder sb = new StringBuilder();
    for (StackTraceElement element : trace) {
      sb.append("\t");
      sb.append(element.toString());
      sb.append("\n");
    }
    return sb.toString();
  }

  public static String stringOfLength(int length) {
    final char[] chars = new char[length];
    Arrays.fill(chars, 'a');
    return new String(chars);
  }
}
