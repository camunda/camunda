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

import io.camunda.zeebe.client.api.response.ResolveIncidentResponse;

public interface ResolveIncidentCommandStep1 extends FinalCommandStep<ResolveIncidentResponse> {
  // the place for new optional parameters
  /**
   * Sets the tenant ID associated with this command. If the associated incident does not belong to
   * the tenant, then this command will fail.
   *
   * @param tenantId the tenant ID of the incident
   * @return the builder for this command. Call #send() to complete the command and send it to the
   *     broker.
   */
  ResolveIncidentCommandStep1 tenantId(final String tenantId);
}
