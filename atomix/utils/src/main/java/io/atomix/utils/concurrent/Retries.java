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
package io.atomix.utils.concurrent;

/** Retry utilities. */
public final class Retries {

  private Retries() {}

  /**
   * Suspends the current thread for a specified number of millis and nanos.
   *
   * @param ms number of millis
   * @param nanos number of nanos
   */
  public static void delay(final int ms, final int nanos) {
    try {
      Thread.sleep(ms, nanos);
    } catch (final InterruptedException e) {
      throw new RuntimeException("Interrupted", e);
    }
  }
}
