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
package io.camunda.zeebe.client.impl.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DataSizeUtil {

  public static final int ONE_KB = 1024;
  public static final int ONE_MB = 1024 * ONE_KB;
  private static final Pattern PATTERN = Pattern.compile("^(\\d+)(kb|KB|mb|MB)?$");

  private DataSizeUtil() {}

  public static int parse(String text) {
    if (text == null) {
      throw new IllegalArgumentException("Text must not be null");
    }
    final Matcher matcher = PATTERN.matcher(text.replace(" ", ""));
    if (!matcher.matches()) {
      throw new IllegalArgumentException(
          String.format(
              "Expected %s to be a data size, but does not match pattern %s",
              text, PATTERN.pattern()));
    }

    final String data = matcher.group(1);
    final String unit = matcher.group(2);
    if (null == unit) {
      return Integer.parseInt(data);
    }
    switch (matcher.group(2).toLowerCase()) {
      case "kb":
        return Integer.parseInt(matcher.group(1)) * ONE_KB;
      case "mb":
        return Integer.parseInt(matcher.group(1)) * ONE_MB;
      default:
        throw new IllegalArgumentException(
            String.format("Unexpected data size unit %s, should be one of [kb, mb]", unit));
    }
  }
}
