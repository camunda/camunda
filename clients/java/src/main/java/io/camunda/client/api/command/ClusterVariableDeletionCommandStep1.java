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

import io.camunda.client.api.response.DeleteClusterVariableResponse;

/**
 * Represents the first step of deleting a cluster variable. At this step, you must specify the
 * scope of the variable (either global or tenant-scoped).
 *
 * <p>Usage example:
 *
 * <pre>
 *   camundaClient
 *       .newClusterVariableDeleteRequest()
 *       .globalScoped()
 *       .name("myVariable")
 *       .send()
 *       .join();
 * </pre>
 */
public interface ClusterVariableDeletionCommandStep1 {

  /**
   * Specifies that the cluster variable to delete has tenant scope.
   *
   * @param tenantId the ID of the tenant for which the variable is scoped. Must not be null or
   *     empty.
   * @return the next step in the command building process where you can specify the variable name
   */
  ClusterVariableDeletionCommandStep2 tenantScoped(String tenantId);

  /**
   * Specifies that the cluster variable to delete has global scope.
   *
   * @return the next step in the command building process where you can specify the variable name
   */
  ClusterVariableDeletionCommandStep2 globalScoped();

  /**
   * Represents the second step of deleting a cluster variable. At this step, you specify the
   * variable name and send the request to delete it.
   */
  interface ClusterVariableDeletionCommandStep2
      extends FinalCommandStep<DeleteClusterVariableResponse> {

    /**
     * Specifies the name of the cluster variable to delete.
     *
     * <p>The variable will be deleted only if it exists with the specified name and scope. If the
     * variable does not exist, a 404 error will be returned.
     *
     * <pre>
     *   camundaClient
     *       .newClusterVariableDeleteRequest()
     *       .globalScoped()
     *       .name("myVariable")
     *       .send()
     *       .join();
     * </pre>
     *
     * @param name the name of the variable to delete. Must not be null or empty.
     * @return this builder for method chaining
     */
    ClusterVariableDeletionCommandStep2 name(String name);
  }
}
