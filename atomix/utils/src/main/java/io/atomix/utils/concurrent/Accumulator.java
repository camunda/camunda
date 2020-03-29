/*
 * Copyright 2015-present Open Networking Foundation
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
package io.atomix.utils.concurrent;

import java.util.List;

/**
 * Abstraction of an accumulator capable of collecting items and at some point in time triggers
 * processing of all previously accumulated items.
 *
 * @param <T> item type
 */
public interface Accumulator<T> {

  /**
   * Adds an item to the current batch. This operation may, or may not trigger processing of the
   * current batch of items.
   *
   * @param item item to be added to the current batch
   */
  void add(T item);

  /**
   * Processes the specified list of accumulated items.
   *
   * @param items list of accumulated items
   */
  void processItems(List<T> items);

  /**
   * Indicates whether the accumulator is ready to process items.
   *
   * @return true if ready to process
   */
  boolean isReady();
}
