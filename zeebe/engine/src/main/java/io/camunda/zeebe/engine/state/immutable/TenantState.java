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
   * Retrieves a tenant record by its key.
   *
   * @param tenantKey the key of the tenant to retrieve
   * @return an Optional containing the tenant record if it exists, otherwise an empty Optional
   */
  Optional<PersistedTenant> getTenantByKey(final long tenantKey);

  /**
   * Retrieves the tenant key associated with the given tenant ID.
   *
   * @param tenantId the unique identifier of the tenant to look up
   * @return an {@link Optional} containing the tenant key if the tenant exists, or an empty {@link
   *     Optional} if not
   */
  Optional<Long> getTenantKeyById(final String tenantId);

  /**
   * Retrieves the entity type associated with the given tenant key and entity key.
   *
   * @param tenantKey the key of the tenant
   * @param entityKey the key of the entity
   * @return an {@link Optional} containing the {@link EntityType} if it exists, or an empty {@link
   *     Optional} if not
   */
  Optional<EntityType> getEntityType(final long tenantKey, final long entityKey);

  /**
   * Checks if the specified entity is assigned to the given tenant.
   *
   * @param entityKey the key of the entity to check
   * @param tenantKey the key of the tenant
   * @return true if the entity is assigned to the tenant, false otherwise
   */
  boolean isEntityAssignedToTenant(final long entityKey, final long tenantKey);

  /**
   * Retrieves all entities associated with a given tenant key, grouped by their entity type.
   *
   * @param tenantKey the key of the tenant whose entities are being retrieved
   * @return a {@link Map} where each key is an {@link EntityType} and the corresponding value is a
   *     {@link List} of entity keys associated with that type
   */
  Map<EntityType, List<Long>> getEntitiesByType(long tenantKey);

  /**
   * Loops over all tenants and applies the provided callback. It stops looping over the tenants,
   * when the callback function returns false, otherwise it will continue until all tenants are
   * visited.
   */
  void forEachTenant(final Function<String, Boolean> callback);

  /** Retrieves a tenant record by its ID. */
  Optional<PersistedTenant> getTenantById(String tenantId);
}
