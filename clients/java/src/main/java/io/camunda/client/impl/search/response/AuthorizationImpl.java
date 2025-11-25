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
package io.camunda.client.impl.search.response;

import io.camunda.client.api.search.enums.OwnerType;
import io.camunda.client.api.search.enums.PermissionType;
import io.camunda.client.api.search.enums.ResourceType;
import io.camunda.client.api.search.response.Authorization;
import java.util.List;

public class AuthorizationImpl implements Authorization {

  private final String authorizationKey;
  private final String ownerId;
  private final String resourceId;
  private final String resourcePropertyName;
  private final OwnerType ownerType;
  private final ResourceType resourceType;
  private final List<PermissionType> permissionTypes;

  public AuthorizationImpl(
      final String authorizationKey,
      final String ownerId,
      final String resourceId,
      final String resourcePropertyName,
      final OwnerType ownerType,
      final ResourceType resourceType,
      final List<PermissionType> permissionTypes) {
    this.authorizationKey = authorizationKey;
    this.ownerId = ownerId;
    this.resourceId = resourceId;
    this.resourcePropertyName = resourcePropertyName;
    this.ownerType = ownerType;
    this.resourceType = resourceType;
    this.permissionTypes = permissionTypes;
  }

  @Override
  public String getAuthorizationKey() {
    return authorizationKey;
  }

  @Override
  public String getOwnerId() {
    return ownerId;
  }

  @Override
  public OwnerType getOwnerType() {
    return ownerType;
  }

  @Override
  public ResourceType getResourceType() {
    return resourceType;
  }

  @Override
  public String getResourceId() {
    return resourceId;
  }

  @Override
  public String getResourcePropertyName() {
    return resourcePropertyName;
  }

  @Override
  public List<PermissionType> getPermissionTypes() {
    return permissionTypes;
  }
}
