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
  String DEFAULT_TENANT_IDENTIFIER = "[default]";

  /**
   * This identifier is used for entities that are shared among all tenants. For example, a deployed
   * process can be shared among all tenants such that all tenants can create their own instances of
   * the shared process definition. Process instances are always owned by a single tenant.
   */
  String SHARED_TENANT_IDENTIFIER = "[shared]";

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
   * etc.) resulting from this command.
   *
   * <h1>Multi-tenancy</h1>
   *
   * <p>Multiple tenants can share a Zeebe cluster. Entities can be assigned to a specific tenant
   * using an identifier. Only that tenant can access these entities.
   *
   * <p>Any entities created before multi-tenancy has been enabled in the Zeebe cluster, are
   * assigned to the {@link #DEFAULT_TENANT_IDENTIFIER}.
   *
   * <h2>Inferred ownership</h2>
   *
   * It's not always necessary to specify the {@code tenantId} explicitly. In many cases, the owning
   * tenant can be inferred.
   *
   * <p>For example, an instance of a process that is owned by tenant {@code "ACME"} can be created
   * without specifying the {@code tenantId} explicitly. The created process instance is then owned
   * by {@code "ACME"} as well.
   *
   * <p>If no tenant is explicitly specified and the tenant could not be inferred, then the command
   * is rejected.
   *
   * <h2>Shared entities</h2>
   *
   * <p>Some entities (i.e. process and decision definitions) can be shared among all tenants using
   * the {@link #SHARED_TENANT_IDENTIFIER}.
   *
   * <p>When referring to shared entities in your command, the owning tenant may be inferred from
   * the client's access.
   *
   * <p>For example, an instance of a process that is shared among all tenants can be created
   * without specifying the {@code tenantId} explicitly, if the client only has access to one
   * specific tenant. In that case, the tenant owning the created process instance can be inferred
   * from the client's access.
   *
   * @param tenantId the identifier of the tenant to specify for this command, e.g. {@code "ACME"}
   * @return the builder for this command with the tenant specified
   * @since 8.3
   */
  @ExperimentalApi("https://github.com/camunda/zeebe/issues/12653")
  T tenantId(String tenantId);
}
