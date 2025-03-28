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

import io.camunda.client.api.response.UpdateJobResponse;
import io.camunda.client.api.search.JobChangeset;
import java.time.Duration;

public interface UpdateJobCommandStep1 {

  /**
   * Update the retries and/or the timeout of this job.
   *
   * <p>If the given retries are greater than zero then this job will be picked up again by a job
   * subscription and a related incident will be marked as resolved.
   *
   * <p>Timeout value passed as a duration is used to calculate a new job deadline. This will happen
   * when the command is processed. The timeout value will be added to the current time then.
   *
   * @param jobChangeset the changeset of this job, if a field is not provided (equals to null) it
   *     will be not updated
   * @return the builder for this command. Call {@link #send()} to complete the command and send it
   *     to the broker.
   */
  UpdateJobCommandStep2 update(JobChangeset jobChangeset);

  /**
   * Update the retries and/or the timeout of this job.
   *
   * <p>If the given retries are greater than zero then this job will be picked up again by a job
   * subscription and a related incident will be marked as resolved.
   *
   * <p>Timeout value passed as a duration is used to calculate a new job deadline. This will happen
   * when the command is processed. The timeout value will be added to the current time then.
   *
   * @param retries retries the retries of this job, if null it won't be updated
   * @param timeout the time the timeout of this job, if null it won't be updated
   * @return the builder for this command. Call {@link #send()} to complete the command and send it
   *     to the broker.
   */
  UpdateJobCommandStep2 update(Integer retries, Long timeout);

  /**
   * Set the retries of this job.
   *
   * <p>If the given retries are greater than zero then this job will be picked up again by a job
   * subscription and a related incident will be marked as resolved.
   *
   * @param retries the retries of this job
   * @return the builder for this command. Call {@link #send()} to complete the command and send it
   *     to the broker.
   */
  UpdateJobCommandStep2 updateRetries(int retries);

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
  UpdateJobCommandStep2 updateTimeout(long timeout);

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
  UpdateJobCommandStep2 updateTimeout(Duration timeout);

  interface UpdateJobCommandStep2 extends FinalCommandStep<UpdateJobResponse> {
    // the place for new optional parameters
  }
}
