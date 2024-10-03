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

public interface Form {

  /**
   * @return the form ID, as parsed during deployment; together with the versions forms a unique
   *     identifier for a specific form
   */
  String getFormId();

  /**
   * @return the assigned form version
   */
  long getVersion();

  /**
   * @return the assigned form key, which acts as a unique identifier for this form
   */
  long getFormKey();

  /**
   * @return the schema of the form
   */
  Object getSchema();

  /**
   * @return the tenant identifier that owns this form
   */
  String getTenantId();
}
