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
package io.camunda.client.api.search.sort;

import io.camunda.client.api.search.request.TypedSortableRequest.SearchRequestSort;

public interface ResourceSort extends SearchRequestSort<ResourceSort> {

  /** Sort by resource key. */
  ResourceSort resourceKey();

  /** Sort by resource name. */
  ResourceSort resourceName();

  /** Sort by resource ID. */
  ResourceSort resourceId();

  /** Sort by version. */
  ResourceSort version();

  /** Sort by version tag. */
  ResourceSort versionTag();

  /** Sort by deployment key. */
  ResourceSort deploymentKey();

  /** Sort by tenant ID. */
  ResourceSort tenantId();
}
