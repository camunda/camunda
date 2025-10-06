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

import io.camunda.client.api.search.filter.builder.StringProperty;
import io.camunda.client.api.search.request.TypedFilterableRequest.SearchRequestFilter;
import java.util.function.Consumer;

public interface ProcessDefinitionFilter extends SearchRequestFilter {

  /**
   * Filters process definitions to only include the latest version of each process definition.
   *
   * @return the updated filter
   */
  ProcessDefinitionFilter isLatestVersion(boolean latestVersion);

  /**
   * Filters process definitions by the specified process definition key.
   *
   * @param processDefinitionKey the key of the process definition
   * @return the updated filter
   */
  ProcessDefinitionFilter processDefinitionKey(final long processDefinitionKey);

  /**
   * Filters process definitions by the specified name.
   *
   * @param name the name of the process definition
   * @return the updated filter
   */
  ProcessDefinitionFilter name(final String name);

  /**
   * Filters process definitions by the specified name using {@link StringProperty} consumer.
   *
   * @param fn the name {@link StringProperty} consumer of the process definition
   * @return the updated filter
   */
  ProcessDefinitionFilter name(final Consumer<StringProperty> fn);

  /**
   * Filters process definitions by the specified resource name.
   *
   * @param resourceName the resource name of the process definition
   * @return the updated filter
   */
  ProcessDefinitionFilter resourceName(final String resourceName);

  /**
   * Filters process definitions by the specified version.
   *
   * @param version the version of the process definition
   * @return the updated filter
   */
  ProcessDefinitionFilter version(final int version);

  /**
   * Filters process definitions by the specified version tag.
   *
   * @param versionTag the version tag of the process definition
   * @return the updated filter
   */
  ProcessDefinitionFilter versionTag(final String versionTag);

  /**
   * Filters process definitions by the specified process definition id.
   *
   * @param processDefinitionId the id of the process definition
   * @return the updated filter
   */
  ProcessDefinitionFilter processDefinitionId(final String processDefinitionId);

  /**
   * Filters process definitions by the specified processDefinitionId using {@link StringProperty}
   * consumer.
   *
   * @param fn the processDefinitionId {@link StringProperty} consumer of the process definition
   * @return the updated filter
   */
  ProcessDefinitionFilter processDefinitionId(final Consumer<StringProperty> fn);

  /**
   * Filters process definitions by the specified tenant id.
   *
   * @param tenantId the tenant id of the process definition.
   * @return the updated filter
   */
  ProcessDefinitionFilter tenantId(final String tenantId);

  /**
   * Filters process definitions by having or not a form to start the process
   *
   * @param hasStartForm boolean to indicate how to filter
   * @return the updated filter
   */
  ProcessDefinitionFilter hasStartForm(final boolean hasStartForm);
}
