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
package io.camunda.zeebe.client.api.search.response;

public interface ProcessDefinition {

  /**
   * @return the key of the process definition
   */
  long getProcessDefinitionKey();

  /**
   * @return the name of the process definition
   */
  String getName();

  /**
   * @return the resource name of the process definition
   */
  String getResourceName();

  /**
   * @return the version of the process definition
   */
  int getVersion();

  /**
   * @return the version tag of the process definition
   */
  String getVersionTag();

  /**
   * @return the id of the process definition
   */
  String getProcessDefinitionId();

  /**
   * @return the tenant id of the process definition
   */
  String getTenantId();
}
