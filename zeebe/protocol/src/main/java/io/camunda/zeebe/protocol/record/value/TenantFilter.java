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
package io.camunda.zeebe.protocol.record.value;

/** Enumerates the tenant filtering strategies for job activation */
public enum TenantFilter {
  /**
   * Indicates that the tenant IDs provided in the request should be used to filter jobs. This is
   * the default behavior where jobs are filtered based on explicitly provided tenant identifiers.
   */
  PROVIDED,

  /**
   * Indicates that jobs should be filtered based on tenants assigned to the requesting identity.
   * When this filter is used, the system will automatically determine which tenants the requester
   * has access to and filter jobs accordingly.
   */
  ASSIGNED
}
