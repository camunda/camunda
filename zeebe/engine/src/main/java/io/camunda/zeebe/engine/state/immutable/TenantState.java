/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.immutable;

import io.camunda.zeebe.engine.state.tenant.PersistedTenant;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public interface TenantState {

  /**
   * Retrieves the entity type associated with the given tenant key and entity key.
   *
   * @param tenantId the id of the tenant
   * @param entityId the id of the entity
   * @return an {@link Optional} containing the {@link EntityType} if it exists, or an empty {@link
   *     Optional} if not
   */
  Optional<EntityType> getEntityType(final String tenantId, final String entityId);

  /**
   * Retrieves all entities associated with a given tenant id, grouped by their entity type.
   *
   * @param tenantId the id of the tenant whose entities are being retrieved
   * @return a {@link Map} where each key is an {@link EntityType} and the corresponding value is a
   *     {@link List} of entity ids associated with that type
   */
  Map<EntityType, List<String>> getEntitiesByType(String tenantId);

  /**
   * Loops over all tenants and applies the provided callback. It stops looping over the tenants,
   * when the callback function returns false, otherwise it will continue until all tenants are
   * visited.
   */
  void forEachTenant(final Function<String, Boolean> callback);

  /** Retrieves a tenant record by its ID. */
  Optional<PersistedTenant> getTenantById(String tenantId);
}
