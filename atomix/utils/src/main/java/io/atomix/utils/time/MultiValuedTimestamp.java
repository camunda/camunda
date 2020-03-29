/*
 * Copyright 2015-present Open Networking Foundation
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
package io.atomix.utils.time;

import static com.google.common.base.MoreObjects.toStringHelper;

import com.google.common.base.Preconditions;
import com.google.common.collect.ComparisonChain;
import java.util.Objects;

/**
 * A logical timestamp that derives its value from two input values. The first value always takes
 * precedence over the second value when comparing timestamps.
 */
public class MultiValuedTimestamp<T extends Comparable<T>, U extends Comparable<U>>
    implements Timestamp {
  private final T value1;
  private final U value2;

  /**
   * Creates a new timestamp based on two values. The first value has higher precedence than the
   * second when comparing timestamps.
   *
   * @param value1 first value
   * @param value2 second value
   */
  public MultiValuedTimestamp(final T value1, final U value2) {
    this.value1 = Preconditions.checkNotNull(value1);
    this.value2 = Preconditions.checkNotNull(value2);
  }

  // Default constructor for serialization
  @SuppressWarnings("unused")
  private MultiValuedTimestamp() {
    this.value1 = null;
    this.value2 = null;
  }

  @Override
  public int compareTo(final Timestamp o) {
    Preconditions.checkArgument(
        o instanceof MultiValuedTimestamp, "Must be MultiValuedTimestamp", o);
    final MultiValuedTimestamp that = (MultiValuedTimestamp) o;

    return ComparisonChain.start()
        .compare(this.value1, that.value1)
        .compare(this.value2, that.value2)
        .result();
  }

  @Override
  public int hashCode() {
    return Objects.hash(value1, value2);
  }

  @Override
  public boolean equals(final Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof MultiValuedTimestamp)) {
      return false;
    }
    final MultiValuedTimestamp that = (MultiValuedTimestamp) obj;
    return Objects.equals(this.value1, that.value1) && Objects.equals(this.value2, that.value2);
  }

  @Override
  public String toString() {
    return toStringHelper(getClass()).add("value1", value1).add("value2", value2).toString();
  }

  /**
   * Returns the first value.
   *
   * @return first value
   */
  public T value1() {
    return value1;
  }

  /**
   * Returns the second value.
   *
   * @return second value
   */
  public U value2() {
    return value2;
  }
}
