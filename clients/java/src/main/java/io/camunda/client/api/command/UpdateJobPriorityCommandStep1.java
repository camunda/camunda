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

import io.camunda.client.api.response.UpdateJobPriorityResponse;

public interface UpdateJobPriorityCommandStep1
    extends CommandWithCommunicationApiStep<UpdateJobPriorityCommandStep1> {

  /**
   * Set the priority of this job.
   *
   * @param priority the priority of this job
   * @return the builder for this command. Call {@link #send()} to complete the command and send it
   *     to the broker.
   */
  UpdateJobPriorityCommandStep2 priority(int priority);

  interface UpdateJobPriorityCommandStep2
      extends CommandWithOperationReferenceStep<UpdateJobPriorityCommandStep2>,
          FinalCommandStep<UpdateJobPriorityResponse> {
    // the place for new optional parameters

    /**
     * Sets the lease token identifying the job's activation, fencing this command against a
     * superseded activation of the same job. Obtain it from {@link
     * io.camunda.client.api.response.ActivatedJob#getLeaseToken() ActivatedJob#getLeaseToken()}.
     *
     * <p>For a leased job, a supplied token is validated to prove the command comes from the worker
     * that holds the current lease; a command carrying a stale token is rejected, fencing the job
     * against a superseded activation (e.g. after the job timed out or failed and was re-activated
     * by another worker). An update without a token always applies, to support operator and bulk
     * updates of leased jobs; this differs from lifecycle commands like complete, fail, and
     * throw-error, which always require a token for leased jobs. A job that was activated without a
     * lease requires no token.
     *
     * <p>When this command is created from an activated job (e.g. {@code
     * newUpdateJobPriorityCommand(activatedJob)}) the job's lease token is carried automatically,
     * so this method is only needed when building the command from a job key.
     *
     * @param leaseToken the opaque lease token the worker received when the job was activated
     * @return the builder for this command. Call {@link #send()} to complete the command and send
     *     it to the broker.
     */
    UpdateJobPriorityCommandStep2 withLeaseToken(String leaseToken);
  }
}
