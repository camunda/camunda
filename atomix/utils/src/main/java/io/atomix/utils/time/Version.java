/*
 * Copyright 2017-present Open Networking Foundation
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
import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ComparisonChain;
import java.util.Objects;

/**
 * Logical timestamp for versions.
 *
 * <p>The version is a logical timestamp that represents a point in logical time at which an event
 * occurs. This is used in both pessimistic and optimistic locking protocols to ensure that the
 * state of a shared resource has not changed at the end of a transaction.
 */
public class Version implements Timestamp {
  private final long version;

  public Version(final long version) {
    this.version = version;
  }

  /**
   * Returns the version.
   *
   * @return the version
   */
  public long value() {
    return this.version;
  }

  @Override
  public int compareTo(final Timestamp o) {
    checkArgument(o instanceof Version, "Must be LockVersion", o);
    final Version that = (Version) o;

    return ComparisonChain.start().compare(this.version, that.version).result();
  }

  @Override
  public int hashCode() {
    return Long.hashCode(version);
  }

  @Override
  public boolean equals(final Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof Version)) {
      return false;
    }
    final Version that = (Version) obj;
    return Objects.equals(this.version, that.version);
  }

  @Override
  public String toString() {
    return toStringHelper(getClass()).add("version", version).toString();
  }
}
