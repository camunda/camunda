/*
 * Copyright 2016-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
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
package io.atomix.utils.misc;

import static com.google.common.base.MoreObjects.toStringHelper;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Function;

/**
 * Utility class for checking matching values.
 *
 * @param <T> type of value
 */
public final class Match<T> {

  public static final Match ANY = new Match<>();
  public static final Match NULL = new Match<>(null, false);
  public static final Match NOT_NULL = new Match<>(null, true);

  private final boolean matchAny;
  private final T value;
  private final boolean negation;

  private Match() {
    matchAny = true;
    negation = false;
    value = null;
  }

  private Match(final T value, final boolean negation) {
    matchAny = false;
    this.value = value;
    this.negation = negation;
  }

  /**
   * Returns a Match that matches any value including null.
   *
   * @param <T> match type
   * @return new instance
   */
  public static <T> Match<T> any() {
    return ANY;
  }

  /**
   * Returns a Match that matches null values.
   *
   * @param <T> match type
   * @return new instance
   */
  public static <T> Match<T> ifNull() {
    return NULL;
  }

  /**
   * Returns a Match that matches all non-null values.
   *
   * @param <T> match type
   * @return new instance
   */
  public static <T> Match<T> ifNotNull() {
    return NOT_NULL;
  }

  /**
   * Returns a Match that only matches the specified value.
   *
   * @param value value to match
   * @param <T> match type
   * @return new instance
   */
  public static <T> Match<T> ifValue(final T value) {
    return new Match<>(value, false);
  }

  /**
   * Returns a Match that matches any value except the specified value.
   *
   * @param value value to not match
   * @param <T> match type
   * @return new instance
   */
  public static <T> Match<T> ifNotValue(final T value) {
    return new Match<>(value, true);
  }

  /**
   * Maps this instance to a Match of another type.
   *
   * @param mapper transformation function
   * @param <V> new match type
   * @return new instance
   */
  public <V> Match<V> map(final Function<T, V> mapper) {
    if (matchAny) {
      return any();
    } else if (value == null) {
      return negation ? ifNotNull() : ifNull();
    } else {
      return negation ? ifNotValue(mapper.apply(value)) : ifValue(mapper.apply(value));
    }
  }

  /**
   * Checks if this instance matches specified value.
   *
   * @param other other value
   * @return true if matches; false otherwise
   */
  public boolean matches(final T other) {
    if (matchAny) {
      return true;
    } else if (other == null) {
      return negation ? value != null : value == null;
    } else {
      if (value instanceof byte[]) {
        final boolean equal = Arrays.equals((byte[]) value, (byte[]) other);
        return negation ? !equal : equal;
      }
      return negation ? !Objects.equals(value, other) : Objects.equals(value, other);
    }
  }

  @Override
  public int hashCode() {
    return Objects.hash(matchAny, value, negation);
  }

  @Override
  public boolean equals(final Object other) {
    if (!(other instanceof Match)) {
      return false;
    }
    final Match<T> that = (Match<T>) other;
    return this.matchAny == that.matchAny
        && Objects.equals(this.value, that.value)
        && this.negation == that.negation;
  }

  @Override
  public String toString() {
    return toStringHelper(this)
        .add("matchAny", matchAny)
        .add("negation", negation)
        .add("value", value)
        .toString();
  }
}
