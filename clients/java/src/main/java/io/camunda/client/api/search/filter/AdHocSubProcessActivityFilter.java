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
package io.camunda.client.api.search.filter;

import io.camunda.client.api.search.request.TypedFilterableRequest.SearchRequestFilter;

public interface AdHocSubProcessActivityFilter extends SearchRequestFilter {

  /**
   * Filters ad-hoc sub-process activities by process definition key.
   *
   * @param processDefinitionKey the process definition key of the ad-hoc sub-process
   * @return the updated filter
   */
  AdHocSubProcessActivityFilter processDefinitionKey(final long processDefinitionKey);

  /**
   * Filters element instances by ad-hoc sub-process id.
   *
   * @param adHocSubProcessId the id of the ad-hoc sub-process
   * @return the updated filter
   */
  AdHocSubProcessActivityFilter adHocSubProcessId(final String adHocSubProcessId);
}
