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
package io.zeebe.client.api.worker;

import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.response.ActivatedJob;

/** Implementations MUST be thread-safe. */
@FunctionalInterface
public interface JobHandler {

  /**
   * Handles a job. Implements the work to be done whenever a job of a certain type is received.
   *
   * <p>In case the job handler throws an exception the job is failed and the job retries are
   * automatically decremented by one. The failed job will contain the exception stacktrace as error
   * message.
   *
   * <p>If the retries reaches zero an incident will be created, which has to be resolved before the
   * job is available again (see {@link ZeebeClient#newResolveIncidentCommand(long)}
   */
  void handle(JobClient client, ActivatedJob job) throws Exception;
}
