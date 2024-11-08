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

import io.camunda.zeebe.client.api.response.RemovePermissionsResponse;
import io.camunda.zeebe.client.protocol.rest.PermissionTypeEnum;
import io.camunda.zeebe.client.protocol.rest.ResourceTypeEnum;
import java.util.List;

public interface RemovePermissionsCommandStep1 {
  /**
   * Sets the resource type for which the permissions should be removed.
   *
   * @param resourceType the resource type
   * @return the builder for this command
   */
  RemovePermissionsCommandStep2 resourceType(ResourceTypeEnum resourceType);

  interface RemovePermissionsCommandStep2 {

    /**
     * Sets the permission type of the permissions that should be removed.
     *
     * @param permissionType the permission type
     * @return the builder for this command
     */
    RemovePermissionsCommandStep3 permission(PermissionTypeEnum permissionType);
  }

  interface RemovePermissionsCommandStep3 {

    /**
     * Adds all resourceIds in the list to the remove permissions request.
     *
     * @param resourceIds the list of resource ids
     * @return the builder for this command
     */
    RemovePermissionsCommandStep4 resourceIds(List<String> resourceIds);

    /**
     * Adds a resourceId to the remove permissions request.
     *
     * @param resourceId the resource id
     * @return the builder for this command
     */
    RemovePermissionsCommandStep4 resourceId(String resourceId);
  }

  interface RemovePermissionsCommandStep4
      extends RemovePermissionsCommandStep2,
          RemovePermissionsCommandStep3,
          FinalCommandStep<RemovePermissionsResponse> {}
}
