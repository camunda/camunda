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

import io.camunda.zeebe.client.api.ExperimentalApi;

public interface CommandWithTenantStep<T> {

  /**
   * This identifier is used for entities that are created before multi-tenancy is enabled in the
   * Zeebe cluster. After enabling multi-tenancy, these entities can still be interacted by using
   * this identifier explicitly.
   */
  String DEFAULT_TENANT_IDENTIFIER = "<default>";

  /**
   * <strong>Experimental: This method is under development, and as such using it may have no effect
   * on the command builder when called. While unimplemented, it simply returns the command builder
   * instance unchanged. This method already exists for software that is building support for
   * multi-tenancy, and already wants to use this API during its development. As support for
   * multi-tenancy is added to Zeebe, each of the commands that implement this method may start to
   * take effect. Until this warning is removed, anything described below may not yet have taken
   * effect, and the interface and its description are subject to change.</strong>
   *
   * <p>Specifies the tenant that will own any entities (e.g. process definition, process instances,
   * etc.) resulting from this command, or that owns any entities (e.g. jobs) referred to from this
   * command.
   *
   * <h1>Multi-tenancy</h1>
   *
   * <p>Multiple tenants can share a Zeebe cluster. Entities can be assigned to a specific tenant
   * using an identifier. Only that tenant can access these entities.
   *
   * <p>Any entities created before multi-tenancy has been enabled in the Zeebe cluster, are
   * assigned to the {@link #DEFAULT_TENANT_IDENTIFIER}.
   *
   * <p>If no tenant is explicitly specified, then the command is rejected.
   *
   * @param tenantId the identifier of the tenant to specify for this command, e.g. {@code "ACME"}
   * @return the builder for this command with the tenant specified
   * @since 8.3
   */
  @ExperimentalApi("https://github.com/camunda/zeebe/issues/12653")
  T tenantId(String tenantId);
}
