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

import io.camunda.zeebe.client.api.response.UpdateRetriesJobResponse;

/**
 * @deprecated since 8.8 for removal in 8.9, replaced by {@link
 *     io.camunda.client.api.command.UpdateRetriesJobCommandStep1}
 */
@Deprecated
public interface UpdateRetriesJobCommandStep1
    extends CommandWithCommunicationApiStep<UpdateRetriesJobCommandStep1> {
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
  UpdateRetriesJobCommandStep2 retries(int retries);

  /**
   * @deprecated since 8.8 for removal in 8.9, replaced by {@link
   *     io.camunda.client.api.command.UpdateRetriesJobCommandStep1.UpdateRetriesJobCommandStep2}
   */
  @Deprecated
  interface UpdateRetriesJobCommandStep2
      extends CommandWithOperationReferenceStep<UpdateRetriesJobCommandStep2>,
          FinalCommandStep<UpdateRetriesJobResponse> {
    // the place for new optional parameters
  }
}
