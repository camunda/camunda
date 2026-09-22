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

import io.camunda.client.api.response.ThrowErrorResponse;
import java.io.InputStream;
import java.util.Map;

public interface ThrowErrorCommandStep1
    extends CommandWithCommunicationApiStep<ThrowErrorCommandStep1> {
  /**
   * Set the errorCode for the error.
   *
   * <p>If the errorCode can't be matched to an error catch event in the process, an incident will
   * be created.
   *
   * @param errorCode the errorCode that will be matched against an error catch event
   * @return the builder for this command. Call {@link ThrowErrorCommandStep2#send()} to complete
   *     the command and send it to the broker.
   */
  ThrowErrorCommandStep2 errorCode(String errorCode);

  interface ThrowErrorCommandStep2
      extends JobCallbackFinalCommandStep<ThrowErrorResponse>,
          CommandWithVariables<ThrowErrorCommandStep2> {
    /**
     * Provide an error message describing the reason for the non-technical error. If the error is
     * not caught by an error catch event, this message will be a part of the raised incident.
     *
     * @param errorMsg error message
     * @return the builder for this command. Call {@link #send()} to complete the command and send
     *     it to the broker.
     */
    ThrowErrorCommandStep2 errorMessage(String errorMsg);

    /**
     * Sets the lease token identifying the job's activation, fencing this command against a
     * superseded activation of the same job. Obtain it from {@link
     * io.camunda.client.api.response.ActivatedJob#getJobLeaseToken()
     * ActivatedJob#getJobLeaseToken()}.
     *
     * <p>For a leased job, the matching token must be supplied to prove the command comes from the
     * worker that holds the current lease; a command with no token is rejected. A command carrying
     * a stale token is likewise rejected, fencing the job against a superseded activation (e.g.
     * after the job timed out or failed and was re-activated by another worker). A job that was
     * activated without a lease requires no token.
     *
     * <p>When this command is created from an activated job (e.g. {@code
     * newThrowErrorCommand(activatedJob)}) the job's lease token is carried automatically, so this
     * method is only needed when building the command from a job key.
     *
     * @param jobLeaseToken the opaque lease token the worker received when the job was activated
     * @return the builder for this command. Call {@link #send()} to complete the command and send
     *     it to the broker.
     */
    ThrowErrorCommandStep2 withJobLeaseToken(String jobLeaseToken);

    /**
     * Sets the reservation token of the process instance the job belongs to, proving this command
     * comes from the caller that reserved the instance's jobs (alpha).
     *
     * <p>A job of an instance created with {@link
     * io.camunda.client.api.command.CreateProcessInstanceCommandStep1.CreateProcessInstanceCommandStep3#reserveJobs(String)
     * reserveJobs} is served to no job worker, and every command on it must carry the same token
     * the instance was created with; a command with no token, or with a different one, is rejected.
     * A job of an unreserved instance requires no token.
     *
     * <p>This is a separate value from the lease token: the lease token is minted by the engine per
     * activation, while the reservation token is chosen by the caller and lives for the instance. A
     * reserved job is never activated, so it never has both.
     *
     * <p>This method is only supported over REST. This is an alpha feature and may be subject to
     * change in future releases.
     *
     * @param jobReservationToken the token the instance's jobs were reserved with
     * @return the builder for this command. Call {@link #send()} to complete the command and send
     *     it to the broker.
     */
    ThrowErrorCommandStep2 withJobReservationToken(String jobReservationToken);

    /**
     * Set the variables of this job.
     *
     * @param variables the variables (JSON) as String
     * @return the builder for this command. Call {@link #send()} to complete the command and send
     *     it to the broker.
     */
    @Override
    ThrowErrorCommandStep2 variables(String variables);

    /**
     * Set the variables of this job.
     *
     * @param variables the variables as object
     * @return the builder for this command. Call {@link #send()} to complete the command and send
     *     it to the broker.
     */
    @Override
    ThrowErrorCommandStep2 variables(Object variables);

    /**
     * Set the variables of this job.
     *
     * @param variables the variables (JSON) as stream
     * @return the builder for this command. Call {@link #send()} to complete the command and send
     *     it to the broker.
     */
    @Override
    ThrowErrorCommandStep2 variables(InputStream variables);

    /**
     * Set the variables of this job.
     *
     * @param variables the variables as map
     * @return the builder for this command. Call {@link #send()} to complete the command and send
     *     it to the broker.
     */
    @Override
    ThrowErrorCommandStep2 variables(Map<String, Object> variables);

    /**
     * Set a single variable of this job.
     *
     * @param key the key of the variable as string
     * @param value the value of the variable as object
     * @return the builder for this command. Call {@link #send()} to complete the command and send
     *     it to the broker.
     */
    @Override
    ThrowErrorCommandStep2 variable(String key, Object value);
  }
}
