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
package io.atomix.utils;

import java.util.concurrent.CompletableFuture;

/**
 * Interface for types that can be asynchronously started and stopped.
 *
 * @param <T> managed type
 */
public interface Managed<T> {

  /**
   * Starts the managed object.
   *
   * @return A completable future to be completed once the object has been started.
   */
  CompletableFuture<T> start();

  /**
   * Returns a boolean value indicating whether the managed object is running.
   *
   * @return Indicates whether the managed object is running.
   */
  boolean isRunning();

  /**
   * Stops the managed object.
   *
   * @return A completable future to be completed once the object has been stopped.
   */
  CompletableFuture<Void> stop();
}
