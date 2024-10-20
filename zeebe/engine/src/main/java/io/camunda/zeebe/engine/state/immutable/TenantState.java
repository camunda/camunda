/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.immutable;

import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface TenantState {

  /**
   * Retrieves a tenant record by its key.
   *
   * @param tenantKey the key of the tenant to retrieve
   * @return an Optional containing the tenant record if it exists, otherwise an empty Optional
   */
  Optional<TenantRecord> getTenantByKey(final long tenantKey);

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
   * Retrieves all entities associated with a given tenant key, grouped by their entity type.
   *
   * @param tenantKey the key of the tenant whose entities are being retrieved
   * @return a {@link Map} where each key is an {@link EntityType} and the corresponding value is a
   *     {@link List} of entity keys associated with that type
   */
  Map<EntityType, List<Long>> getEntitiesByType(long tenantKey);
}
