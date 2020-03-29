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
package io.atomix.utils;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Objects;

/** Abstract identifier backed by another value, e.g. string, int. */
public class AbstractIdentifier<T extends Comparable<T>> implements Identifier<T> {

  protected final T identifier; // backing identifier value

  /** Constructor for serialization. */
  protected AbstractIdentifier() {
    this.identifier = null;
  }

  /**
   * Constructs an identifier backed by the specified value.
   *
   * @param value the backing value
   */
  protected AbstractIdentifier(final T value) {
    this.identifier = checkNotNull(value, "Identifier cannot be null.");
  }

  /**
   * Returns the backing identifier value.
   *
   * @return identifier
   */
  public T id() {
    return identifier;
  }

  /**
   * Returns the hashcode of the identifier.
   *
   * @return hashcode
   */
  @Override
  public int hashCode() {
    return identifier.hashCode();
  }

  /**
   * Compares two device key identifiers for equality.
   *
   * @param obj to compare against
   * @return true if the objects are equal, false otherwise.
   */
  @Override
  public boolean equals(final Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj instanceof AbstractIdentifier) {
      final AbstractIdentifier that = (AbstractIdentifier) obj;
      return this.getClass() == that.getClass() && Objects.equals(this.identifier, that.identifier);
    }
    return false;
  }

  /**
   * Returns a string representation of a DeviceKeyId.
   *
   * @return string
   */
  public String toString() {
    return identifier.toString();
  }
}
