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
package io.zeebe.client.impl.command;

public class ArgumentUtil {

  public static void ensureNotNull(String property, Object value) {
    if (value == null) {
      throw new IllegalArgumentException(property + " must not be null");
    }
  }

  public static void ensureNotEmpty(String property, String value) {
    if (value.isEmpty()) {
      throw new IllegalArgumentException(property + " must not be empty");
    }
  }

  public static void ensureNotNullNorEmpty(String property, String value) {
    ensureNotNull(property, value);
    ensureNotEmpty(property, value);
  }

  public static void ensureGreaterThan(String property, long testValue, long comparisonValue) {
    if (testValue <= comparisonValue) {
      throw new IllegalArgumentException(property + " must be greater than " + comparisonValue);
    }
  }
}
