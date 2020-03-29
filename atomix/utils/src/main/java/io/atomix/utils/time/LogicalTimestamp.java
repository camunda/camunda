/*
 * Copyright 2016-present Open Networking Foundation
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
 * Timestamp based on logical sequence value.
 *
 * <p>LogicalTimestamps are ordered by their sequence values.
 */
public class LogicalTimestamp implements Timestamp {

  private final long value;

  public LogicalTimestamp(final long value) {
    this.value = value;
  }

  /**
   * Returns a new logical timestamp for the given logical time.
   *
   * @param value the logical time for which to create a new logical timestamp
   * @return the logical timestamp
   */
  public static LogicalTimestamp of(final long value) {
    return new LogicalTimestamp(value);
  }

  /**
   * Returns the sequence value.
   *
   * @return sequence value
   */
  public long value() {
    return this.value;
  }

  /**
   * Returns the timestamp as a version.
   *
   * @return the timestamp as a version
   */
  public Version asVersion() {
    return new Version(value);
  }

  @Override
  public int compareTo(final Timestamp o) {
    Preconditions.checkArgument(o instanceof LogicalTimestamp, "Must be LogicalTimestamp", o);
    final LogicalTimestamp that = (LogicalTimestamp) o;

    return ComparisonChain.start().compare(this.value, that.value).result();
  }

  @Override
  public int hashCode() {
    return Long.hashCode(value);
  }

  @Override
  public boolean equals(final Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof LogicalTimestamp)) {
      return false;
    }
    final LogicalTimestamp that = (LogicalTimestamp) obj;
    return Objects.equals(this.value, that.value);
  }

  @Override
  public String toString() {
    return toStringHelper(getClass()).add("value", value).toString();
  }
}
