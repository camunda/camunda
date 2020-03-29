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
package io.atomix.primitive.log;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Log consumer. */
public interface LogConsumer {

  /**
   * Adds a new consumer.
   *
   * @param consumer the consumer to add
   * @return a future to be completed once the consumer has been added
   */
  default CompletableFuture<Void> consume(final Consumer<LogRecord> consumer) {
    return consume(1, consumer);
  }

  /**
   * Adds a new consumer.
   *
   * @param index the index from which to begin consuming
   * @param consumer the consumer to add
   * @return a future to be completed once the consumer has been added
   */
  CompletableFuture<Void> consume(long index, Consumer<LogRecord> consumer);
}
