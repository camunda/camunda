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

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import io.atomix.utils.misc.ArraySizeHashPrinter;
import io.atomix.utils.misc.TimestampPrinter;
import java.util.function.Function;

/**
 * Versioned value.
 *
 * @param <V> value type.
 */
public class Versioned<V> {
  private final V value;
  private final long version;
  private final long creationTime;

  /**
   * Constructs a new versioned value.
   *
   * @param value value
   * @param version version
   * @param creationTime milliseconds of the creation event from the Java epoch of
   *     1970-01-01T00:00:00Z
   */
  public Versioned(final V value, final long version, final long creationTime) {
    this.value = value;
    this.version = version;
    this.creationTime = creationTime;
  }

  /**
   * Constructs a new versioned value.
   *
   * @param value value
   * @param version version
   */
  public Versioned(final V value, final long version) {
    this(value, version, System.currentTimeMillis());
  }

  /**
   * Returns the value.
   *
   * @return value.
   */
  public V value() {
    return value;
  }

  /**
   * Returns the version.
   *
   * @return version
   */
  public long version() {
    return version;
  }

  /**
   * Returns the system time when this version was created.
   *
   * <p>Care should be taken when relying on creationTime to implement any behavior in a distributed
   * setting. Due to the possibility of clock skew it is likely that even creationTimes of causally
   * related versions can be out or order.
   *
   * @return creation time
   */
  public long creationTime() {
    return creationTime;
  }

  /**
   * Maps this instance into another after transforming its value while retaining the same version
   * and creationTime.
   *
   * @param transformer function for mapping the value
   * @param <U> value type of the returned instance
   * @return mapped instance
   */
  public synchronized <U> Versioned<U> map(final Function<V, U> transformer) {
    return new Versioned<>(value != null ? transformer.apply(value) : null, version, creationTime);
  }

  /**
   * Returns the value of the specified Versioned object if non-null or else returns a default
   * value.
   *
   * @param versioned versioned object
   * @param defaultValue default value to return if versioned object is null
   * @param <U> type of the versioned value
   * @return versioned value or default value if versioned object is null
   */
  public static <U> U valueOrElse(final Versioned<U> versioned, final U defaultValue) {
    return versioned == null ? defaultValue : versioned.value();
  }

  /**
   * Returns the value of the specified Versioned object if non-null or else returns null.
   *
   * @param versioned versioned object
   * @param <U> type of the versioned value
   * @return versioned value or null if versioned object is null
   */
  public static <U> U valueOrNull(final Versioned<U> versioned) {
    return valueOrElse(versioned, null);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(value, version, creationTime);
  }

  @Override
  public boolean equals(final Object other) {
    if (!(other instanceof Versioned)) {
      return false;
    }
    final Versioned<V> that = (Versioned) other;
    return Objects.equal(this.value, that.value)
        && Objects.equal(this.version, that.version)
        && Objects.equal(this.creationTime, that.creationTime);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("value", value instanceof byte[] ? ArraySizeHashPrinter.of((byte[]) value) : value)
        .add("version", version)
        .add("creationTime", new TimestampPrinter(creationTime))
        .toString();
  }
}
