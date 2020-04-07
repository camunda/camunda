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

/**
 * Epoch.
 *
 * <p>An epoch is a specific type of {@link LogicalTimestamp} that represents a long term section of
 * logical time.
 */
public class Epoch extends LogicalTimestamp {

  /**
   * Creates a new epoch timestamp.
   *
   * @param value the epoch value
   */
  public Epoch(final long value) {
    super(value);
  }

  /**
   * Returns a new logical timestamp for the given logical time.
   *
   * @param value the logical time for which to create a new logical timestamp
   * @return the logical timestamp
   */
  public static Epoch of(final long value) {
    return new Epoch(value);
  }
}
