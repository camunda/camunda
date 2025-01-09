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
package io.camunda.zeebe.client.impl.command;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Predicate;

public final class ArgumentUtil {

  public static void ensureNotNull(final String property, final Object value) {
    if (value == null) {
      throw new IllegalArgumentException(property + " must not be null");
    }
  }

  public static void ensureNotEmpty(final String property, final String value) {
    ensureNotEmpty(property, value, String::isEmpty);
  }

  public static void ensureNotNullNorEmpty(final String property, final String value) {
    ensureNotNull(property, value);
    ensureNotEmpty(property, value);
  }

  public static void ensureGreaterThan(
      final String property, final long testValue, final long comparisonValue) {
    if (testValue <= comparisonValue) {
      throw new IllegalArgumentException(property + " must be greater than " + comparisonValue);
    }
  }

  public static void ensureNotNegative(final String property, final long testValue) {
    if (testValue < 0) {
      throw new IllegalArgumentException(String.format("%s must be not negative", property));
    }
  }

  public static void ensureNotNegative(final String property, final Duration testValue) {
    ensureNotNegative(property, testValue.toMillis());
  }

  public static void ensureNotZero(final String property, final Duration testValue) {
    if (testValue.isZero()) {
      throw new IllegalArgumentException(String.format("%s must be not zero", property));
    }
  }

  public static void ensurePositive(final String property, final Duration testValue) {
    ensureNotNegative(property, testValue);
    ensureNotZero(property, testValue);
  }

  public static void ensureNotBefore(
      final String property, final Instant testValue, final Instant otherInstant) {
    if (testValue.isBefore(otherInstant)) {
      throw new IllegalArgumentException(
          String.format("%s must be equal to or after %s", property, otherInstant));
    }
  }

  public static void ensureNotEmpty(final String property, final List<?> value) {
    ensureNotEmpty(property, value, List::isEmpty);
  }

  public static void ensureNotNullOrEmpty(final String property, final List<?> value) {
    ensureNotNull(property, value);
    ensureNotEmpty(property, value);
  }

  private static <T> void ensureNotEmpty(
      final String property, final T value, final Predicate<T> isEmptyPredicate) {
    if (isEmptyPredicate.test(value)) {
      throw new IllegalArgumentException(property + " must not be empty");
    }
  }
}
