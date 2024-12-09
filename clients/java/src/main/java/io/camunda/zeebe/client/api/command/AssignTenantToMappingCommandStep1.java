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

import io.camunda.zeebe.client.api.response.AssignTenantToMappingResponse;

/** Command to assign a tenant to a mapping. */
public interface AssignTenantToMappingCommandStep1
    extends FinalCommandStep<AssignTenantToMappingResponse> {

  /**
   * Sets the mapping key for the assignment.
   *
   * @param mappingKey the key of the mapping
   * @return the builder for this command. Call {@link #send()} to complete the command and send it
   *     to the broker.
   */
  AssignTenantToMappingCommandStep1 mappingKey(long mappingKey);
}
