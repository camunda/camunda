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

import io.camunda.client.api.search.enums.OwnerType;
import io.camunda.client.api.search.enums.ResourceType;
import io.camunda.client.api.search.request.TypedFilterableRequest.SearchRequestFilter;
import java.util.List;

public interface AuthorizationFilter extends SearchRequestFilter {

  /**
   * Filter authorizations by the specified owner ID.
   *
   * @param ownerId the ID of the owner
   * @return the updated filter
   */
  AuthorizationFilter ownerId(final String ownerId);

  /**
   * Filter authorizations by the specified owner type.
   *
   * @param ownerType the type of the owner
   * @return the updated filter
   */
  AuthorizationFilter ownerType(final OwnerType ownerType);

  /**
   * Filter authorizations by the specified resource IDs.
   *
   * @param resourceIds the IDs of the resource
   * @return the updated filter
   */
  AuthorizationFilter resourceIds(final String... resourceIds);

  /**
   * Filter authorizations by the specified resource IDs.
   *
   * @param resourceIds the IDs of the resource
   * @return the updated filter
   */
  AuthorizationFilter resourceIds(final List<String> resourceIds);

  /**
   * Filter authorizations by the specified resource type.
   *
   * @param resourceType the type of the resource
   * @return the updated filter
   */
  AuthorizationFilter resourceType(final ResourceType resourceType);
}
