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

/** The result of completing a job that stands in for a called process (alpha). */
public interface CompleteCallActivityJobResultStep1 extends CompleteJobResult {

  /**
   * Starts the process the call activity calls after all, instead of this completion standing in
   * for it.
   *
   * <p>The call activity then behaves as it would have without stubbing: it waits for the called
   * process and completes when that process completes. If the called process cannot be resolved,
   * for example because it is not deployed, an incident is raised on the call activity, and
   * resolving that incident retries the lookup.
   *
   * @return the builder for this command.
   */
  CompleteCallActivityJobResultStep1 runCalledProcess();

  /**
   * Whether to start the called process instead of standing in for it.
   *
   * @param runCalledProcess true to start the called process
   * @return the builder for this command.
   */
  CompleteCallActivityJobResultStep1 runCalledProcess(boolean runCalledProcess);
}
