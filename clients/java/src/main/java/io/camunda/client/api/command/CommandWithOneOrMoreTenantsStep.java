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

import io.camunda.client.api.command.enums.TenantFilter;
import java.util.List;

public interface CommandWithOneOrMoreTenantsStep<T> extends CommandWithTenantStep<T> {

  /**
   * {@inheritDoc}
   *
   * <h1>One or more tenants</h1>
   *
   * <p>This method can be called multiple times to specify multiple tenants.
   *
   * <p>This can be useful when requesting jobs for multiple tenants at once. Each of the activated
   * jobs will be owned by the tenant that owns the corresponding process instance.
   *
   * @param tenantId the identifier of the tenant to specify for this command, e.g. {@code "ACME"}
   * @return the builder for this command with the tenant specified
   * @since 8.3
   */
  @Override
  T tenantId(String tenantId);

  /**
   * Specifies the tenants that may own any entities (e.g. process definition, process instances,
   * etc.) resulting from this command.
   *
   * <h1>One or more tenants</h1>
   *
   * <p>This can be useful when requesting jobs for multiple tenants at once. Each of the activated
   * jobs will be owned by the tenant that owns the corresponding process instance.
   *
   * @param tenantIds the identifiers of the tenants to specify for this command, e.g. {@code
   *     ["ACME", "OTHER"]}
   * @return the builder for this command with the tenants specified
   * @see #tenantId(String)
   * @since 8.3
   */
  T tenantIds(List<String> tenantIds);

  /**
   * Shorthand method for {@link #tenantIds(List)}.
   *
   * @param tenantIds the identifiers of the tenants to specify for this command, e.g. {@code
   *     ["ACME", "OTHER"]}
   * @return the builder for this command with the tenants specified
   * @see #tenantIds(List)
   * @since 8.3
   */
  T tenantIds(String... tenantIds);

  /**
   * Sets the tenant filter strategy for this command. See {@link TenantFilter} for possible values.
   *
   * <p>When set to {@link TenantFilter#ASSIGNED}, the tenants assigned to the authenticated
   * principal are resolved dynamically and any tenant IDs provided via {@link #tenantId(String)} or
   * {@link #tenantIds(List)} are ignored.
   *
   * <p>When set to {@link TenantFilter#PROVIDED}, the tenant IDs supplied with this command are
   * used.
   *
   * @param tenantFilter the tenant filter strategy
   * @return the builder for this command with the tenant filter specified
   */
  T tenantFilter(TenantFilter tenantFilter);
}
