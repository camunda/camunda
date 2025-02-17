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
package io.camunda.client.api.response;

public interface Resource {

  /**
   * @return the resource ID, as parsed during deployment; together with the versions forms a unique
   *     identifier for a specific resource
   */
  String getResourceId();

  /**
   * @return the assigned resource key, which acts as a unique identifier for this resource
   */
  long getResourceKey();

  /**
   * @return the assigned resource version
   */
  int getVersion();

  /**
   * @return the name of the resource, as parsed during deployment
   */
  String getResourceName();

  /**
   * @return the tenant identifier that owns this resource
   */
  String getTenantId();
}
