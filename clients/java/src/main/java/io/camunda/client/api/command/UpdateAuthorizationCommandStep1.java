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

import io.camunda.client.api.response.UpdateAuthorizationResponse;
import io.camunda.client.api.search.enums.OwnerType;
import io.camunda.client.api.search.enums.PermissionType;
import io.camunda.client.api.search.enums.ResourceType;

public interface UpdateAuthorizationCommandStep1 {

  /**
   * Sets the ownerId of the permissions for the authorization.
   *
   * @param ownerId the ID of the owner of the permissions
   * @return the builder for this command
   */
  UpdateAuthorizationCommandStep2 ownerId(String ownerId);

  interface UpdateAuthorizationCommandStep2 {

    /**
     * Sets the ownerType of the permissions for the authorization.
     *
     * @param ownerType the type of the owner of the permissions
     * @return the builder for this command
     */
    UpdateAuthorizationCommandStep3 ownerType(OwnerType ownerType);
  }

  interface UpdateAuthorizationCommandStep3 {

    /**
     * Sets the resource ID for the authorization.
     *
     * @param resourceId the ID of the resource
     * @return the builder for this command
     */
    UpdateAuthorizationCommandStep4 resourceId(String resourceId);
  }

  interface UpdateAuthorizationCommandStep4 {

    /**
     * Sets the resource type for the authorization.
     *
     * @param resourceType the type of the resource
     * @return the builder for this command
     */
    UpdateAuthorizationCommandStep5 resourceType(ResourceType resourceType);
  }

  interface UpdateAuthorizationCommandStep5 {

    /**
     * List the permission types for the authorization.
     *
     * @param permissionTypes the permission types
     * @return the builder for this command
     */
    UpdateAuthorizationCommandStep6 permissionTypes(PermissionType... permissionTypes);
  }

  interface UpdateAuthorizationCommandStep6
      extends UpdateAuthorizationCommandStep2,
          UpdateAuthorizationCommandStep3,
          UpdateAuthorizationCommandStep4,
          UpdateAuthorizationCommandStep5,
          FinalCommandStep<UpdateAuthorizationResponse> {}
}
