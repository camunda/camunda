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

import io.camunda.client.api.response.DeleteResourceResponse;

public interface DeleteResourceCommandStep1
    extends CommandWithOperationReferenceStep<DeleteResourceCommandStep1>,
        CommandWithCommunicationApiStep<DeleteResourceCommandStep1>,
        FinalCommandStep<DeleteResourceResponse> {

  /**
   * When set to true, asynchronously deletes all history records for instances. History records are
   * retained by default (false). Currently supported for process definitions only.
   *
   * @param deleteHistory whether to delete history records
   * @return this builder
   */
  DeleteResourceCommandStep1 deleteHistory(final boolean deleteHistory);
}
