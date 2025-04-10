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

import io.camunda.client.api.response.CreateBatchOperationResponse;
import io.camunda.client.api.search.filter.ProcessInstanceFilter;
import io.camunda.client.api.search.request.TypedSearchRequest.SearchRequestFilter;
import java.util.function.Consumer;

public interface CreateBatchOperationCommandStep1 {

  /**
   * Defines the type of the batch operation.
   *
   * @return the builder for this command
   */
  CreateBatchOperationCommandStep2<ProcessInstanceFilter> processInstanceCancel();

  interface CreateBatchOperationCommandStep2<E extends SearchRequestFilter> {

    CreateBatchOperationCommandStep3<E> filter(E filter);

    CreateBatchOperationCommandStep3<E> filter(Consumer<E> filter);
  }

  interface CreateBatchOperationCommandStep3<E extends SearchRequestFilter>
      extends FinalCommandStep<CreateBatchOperationResponse> {}
}
