/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
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
package io.camunda.process.test.impl.client;

import java.time.Duration;
import java.time.Instant;

/** Interface for manipulating the Camunda cluster clock in process tests. */
public interface CamundaClockClient {

  /**
   * Returns the current time of the cluster.
   *
   * @return the current time
   */
  Instant getCurrentTime();

  /**
   * Increases the cluster time by the given duration.
   *
   * @param timeToAdd the duration to add
   */
  void increaseTime(Duration timeToAdd);

  /**
   * Sets the cluster time to the given instant.
   *
   * @param timeToSet the target time
   */
  void setTime(Instant timeToSet);

  /** Resets the cluster time to the system time. */
  void resetTime();
}
