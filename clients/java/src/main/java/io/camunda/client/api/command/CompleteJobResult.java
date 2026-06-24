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

import io.camunda.client.api.command.enums.JobResultType;

public interface CompleteJobResult {

  /**
   * Get the type of the job result. Depending on the type, different fields will be set on the
   * request. These fields are used to perform follow-up actions on the job upon completion. Eg:
   * changing the assignee of a user task, or activating certain elements of an ad-hoc sub process.
   *
   * @return the type of the job result
   */
  JobResultType getType();
}
