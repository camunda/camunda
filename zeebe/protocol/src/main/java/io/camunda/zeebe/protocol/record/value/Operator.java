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
package io.camunda.zeebe.protocol.record.value;

/**
 * Enumerates the supported operators for mapping records. These operators define how claim values
 * should be evaluated during authorization checks.
 */
public enum Operator {
  /**
   * Represents an equality check. This operator is used to determine if the value matches exactly.
   */
  CONTAINS,
  /**
   * Represents a containment check. This operator is used to determine if a value contains the
   * specified substring or element.
   */
  EQUALS;

  /**
   * Parses a string value into its corresponding {@link Operator}. This method is case-insensitive.
   *
   * @param value the string representation of the operator
   * @return the corresponding {@link Operator} if found, or {@code null} otherwise
   */
  public static Operator fromString(final String value) {
    for (final Operator operator : Operator.values()) {
      if (operator.name().equalsIgnoreCase(value)) {
        return operator;
      }
    }
    return null;
  }
}
