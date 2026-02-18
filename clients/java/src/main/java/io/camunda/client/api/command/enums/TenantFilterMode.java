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
package io.camunda.client.api.command.enums;

/**
 * Protocol agnostic tenant filter. See {@link
 * io.camunda.zeebe.gateway.protocol.GatewayOuterClass.TenantFilter} and {@link
 * io.camunda.client.protocol.rest.TenantFilterEnum}.
 *
 * <p>Used only during job activation.
 */
public enum TenantFilterMode {
  /**
   * When set, the tenants assigned to the authenticated principal (for example, a user or client)
   * are resolved dynamically when the job is activated. This means you don't need to know in
   * advance which tenants to use; instead, the activated jobs depend on the tenant assignments
   * configured for the authenticated principal in your identity and authorization setup.
   */
  ASSIGNED,
  /**
   * When set, the command uses the tenant IDs supplied with the request (for example, those
   * configured via {@link
   * io.camunda.client.api.command.CommandWithOneOrMoreTenantsStep#tenantIds(java.util.List)} or
   * client defaults), and only jobs associated with those tenants, if any, will be activated.
   */
  PROVIDED;

  /**
   * Converts a string value to a TenantFilter enum, handling case-insensitive input and trimming
   * whitespace.
   *
   * @param value the string representation of the tenant filter
   * @return the corresponding TenantFilter enum value
   * @throws IllegalArgumentException if the value doesn't match any TenantFilter value
   */
  public static TenantFilterMode from(final String value) {
    if (value == null) {
      throw new IllegalArgumentException(
          "Tenant filter value cannot be null. Expected 'ASSIGNED' or 'PROVIDED'");
    }

    final String normalizedValue = value.trim().toUpperCase();
    try {
      return TenantFilterMode.valueOf(normalizedValue);
    } catch (final IllegalArgumentException e) {
      throw new IllegalArgumentException(
          String.format(
              "Invalid tenant filter value: '%s'. Expected 'ASSIGNED' or 'PROVIDED' (case-insensitive)",
              value));
    }
  }
}
