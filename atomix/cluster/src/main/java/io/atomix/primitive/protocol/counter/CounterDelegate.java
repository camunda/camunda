/*
 * Copyright 2018-present Open Networking Foundation
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
package io.atomix.primitive.protocol.counter;

import com.google.common.annotations.Beta;

/** Gossip-based counter service. */
@Beta
public interface CounterDelegate {

  /**
   * Atomically increment by one and return the updated value.
   *
   * @return updated value
   */
  long incrementAndGet();

  /**
   * Atomically decrement by one and return the updated value.
   *
   * @return updated value
   */
  long decrementAndGet();

  /**
   * Atomically increment by one and return the previous value.
   *
   * @return previous value
   */
  long getAndIncrement();

  /**
   * Atomically decrement by one and return the previous value.
   *
   * @return previous value
   */
  long getAndDecrement();

  /**
   * Atomically adds the given value to the current value.
   *
   * @param delta the value to add
   * @return previous value
   */
  long getAndAdd(long delta);

  /**
   * Atomically adds the given value to the current value.
   *
   * @param delta the value to add
   * @return updated value
   */
  long addAndGet(long delta);

  /**
   * Returns the counter value.
   *
   * @return the counter value
   */
  long get();

  /** Closes the counter. */
  void close();
}
