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

import io.camunda.client.api.response.ClockResponse;
import java.time.Duration;

public interface AddClockCommandStep1 {

  /**
   * Sets the time to add to the Zeebe engine's internal clock. A positive duration moves the clock
   * forward, a negative duration moves it backward.
   *
   * @param offset the time to add to the clock
   * @return the builder for this command. Call {@link AddClockCommandStep2#send()} to complete the
   *     command and send it to the broker.
   */
  AddClockCommandStep2 offset(Duration offset);

  interface AddClockCommandStep2 extends FinalCommandStep<ClockResponse> {
    // the place for new optional parameters
  }
}
