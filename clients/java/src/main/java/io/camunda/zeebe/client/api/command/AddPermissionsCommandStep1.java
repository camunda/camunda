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
package io.camunda.zeebe.client.api.command;

import io.camunda.zeebe.client.api.response.AddPermissionsResponse;
import io.camunda.zeebe.client.protocol.rest.PermissionTypeEnum;
import io.camunda.zeebe.client.protocol.rest.ResourceTypeEnum;
import java.util.List;

public interface AddPermissionsCommandStep1 {

  /**
   * Sets the resource type for which the permissions should be added.
   *
   * @param resourceType the resource type
   * @return the builder for this command
   */
  AddPermissionsCommandStep2 resourceType(ResourceTypeEnum resourceType);

  interface AddPermissionsCommandStep2 {

    /**
     * Sets the permission type of the permissions that should be added.
     *
     * @param permissionType the permission type
     * @return the builder for this command
     */
    AddPermissionsCommandStep3 permission(PermissionTypeEnum permissionType);
  }

  interface AddPermissionsCommandStep3 {

    /**
     * Adds all resourceIds in the list to the add permissions request.
     *
     * @param resourceIds the list of resource ids
     * @return the builder for this command
     */
    AddPermissionsCommandStep4 resourceIds(List<String> resourceIds);

    /**
     * Adds a resourceId to the add permissions request.
     *
     * @param resourceId the resource id
     * @return the builder for this command
     */
    AddPermissionsCommandStep4 resourceId(String resourceId);
  }

  interface AddPermissionsCommandStep4
      extends AddPermissionsCommandStep2,
          AddPermissionsCommandStep3,
          FinalCommandStep<AddPermissionsResponse> {}
}
