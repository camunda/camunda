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
package io.camunda.client.api.command;

import io.camunda.client.api.response.PinClockResponse;
import java.time.Instant;

public interface ClockPinCommandStep1 extends FinalCommandStep<PinClockResponse> {

  /**
   * Specifies the exact time to which the Zeebe engine's internal clock should be pinned using an
   * epoch timestamp in milliseconds.
   *
   * @param timestamp the epoch time in milliseconds to which the clock should be pinned
   * @return the builder for this command. Call {@link #send()} to complete the command and send it
   *     to the broker.
   */
  ClockPinCommandStep1 time(long timestamp);

  /**
   * Specifies the exact time to which the Zeebe engine's internal clock should be pinned using an
   * {@link java.time.Instant} object.
   *
   * @param instant the {@link java.time.Instant} to which the clock should be pinned
   * @return the builder for this command. Call {@link #send()} to complete the command and send it
   *     to the broker.
   */
  ClockPinCommandStep1 time(Instant instant);
}
