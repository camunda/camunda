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

import io.camunda.client.api.search.request.TypedSearchRequest.SearchRequestFilter;

public interface AdHocSubprocessActivityFilter extends SearchRequestFilter {

  /**
   * Filters ad-hoc subprocess activities by process definition key.
   *
   * @param processDefinitionKey the process definition key of the ad-hoc subprocess
   * @return the updated filter
   */
  AdHocSubprocessActivityFilter processDefinitionKey(final long processDefinitionKey);

  /**
   * Filters flow node instances by ad-hoc subprocess id.
   *
   * @param adHocSubprocessId the id of the ad-hoc subprocess
   * @return the updated filter
   */
  AdHocSubprocessActivityFilter adHocSubprocessId(final String adHocSubprocessId);

  /**
   * Returns prepared REST API filter object.
   *
   * @return the API filter object
   */
  io.camunda.client.protocol.rest.AdHocSubprocessActivityFilter getRequestFilter();
}
