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
package io.camunda.zeebe.client.api.command;

import io.camunda.zeebe.client.api.response.UpdateTimeoutJobResponse;
import java.time.Duration;

/**
 * @deprecated since 8.8 for removal in 8.9, replaced by {@link
 *     io.camunda.client.api.command.UpdateTimeoutJobCommandStep1}
 */
@Deprecated
public interface UpdateTimeoutJobCommandStep1
    extends CommandWithCommunicationApiStep<UpdateTimeoutJobCommandStep1> {

  /**
   * Set the timeout of this job.
   *
   * <p>Timeout value in millis is used to calculate a new job deadline. This will happen when the
   * command is processed. The timeout value will be added to the current time then.
   *
   * @param timeout the timeout of this job
   * @return the builder for this command. Call {@link #send()} to complete the command and send it
   *     to the broker.
   */
  UpdateTimeoutJobCommandStep2 timeout(long timeout);

  /**
   * Set the timeout of this job.
   *
   * <p>Timeout value passed as a duration is used to calculate a new job deadline. This will happen
   * when the command is processed. The timeout value will be added to the current time then.
   *
   * @param timeout the time as duration (e.g. "Duration.ofMinutes(5)")
   * @return the builder for this command. Call {@link #send()} to complete the command and send it
   *     to the broker.
   */
  UpdateTimeoutJobCommandStep2 timeout(Duration timeout);

  /**
   * @deprecated since 8.8 for removal in 8.9, replaced by {@link
   *     io.camunda.client.api.command.UpdateTimeoutJobCommandStep1.UpdateTimeoutJobCommandStep2}
   */
  @Deprecated
  interface UpdateTimeoutJobCommandStep2
      extends CommandWithOperationReferenceStep<UpdateTimeoutJobCommandStep2>,
          FinalCommandStep<UpdateTimeoutJobResponse> {
    // the place for new optional parameters
  }
}
