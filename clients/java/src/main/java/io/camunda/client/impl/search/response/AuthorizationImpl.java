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

import io.camunda.client.api.search.response.Authorization;
import io.camunda.client.protocol.rest.OwnerTypeEnum;
import io.camunda.client.protocol.rest.PermissionTypeEnum;
import io.camunda.client.protocol.rest.ResourceTypeEnum;
import java.util.List;

public class AuthorizationImpl implements Authorization {

  private final String authorizationKey;
  private final String ownerId;
  private final String resourceId;
  private final OwnerTypeEnum ownerType;
  private final ResourceTypeEnum resourceType;
  private final List<PermissionTypeEnum> permissionTypes;

  public AuthorizationImpl(
      final String authorizationKey,
      final String ownerId,
      final String resourceId,
      final OwnerTypeEnum ownerType,
      final ResourceTypeEnum resourceType,
      final List<PermissionTypeEnum> permissionTypes) {
    this.authorizationKey = authorizationKey;
    this.ownerId = ownerId;
    this.resourceId = resourceId;
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
  public OwnerTypeEnum getOwnerType() {
    return ownerType;
  }

  @Override
  public ResourceTypeEnum getResourceType() {
    return resourceType;
  }

  @Override
  public String getResourceId() {
    return resourceId;
  }

  @Override
  public List<PermissionTypeEnum> getPermissionTypes() {
    return permissionTypes;
  }
}
