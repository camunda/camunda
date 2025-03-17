/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.mutable;

import io.camunda.zeebe.engine.state.immutable.TenantState;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;

public interface MutableTenantState extends TenantState {
  /**
   * Creates a tenant in the state.
   *
   * @param tenantRecord the tenant record to add
   */
  void createTenant(final TenantRecord tenantRecord);

  /**
   * Updates the tenant record associated with the given tenant key.
   *
   * @param updatedTenantRecord the updated tenant record with new values
   */
  void updateTenant(final TenantRecord updatedTenantRecord);

  /**
   * Adds an entity to the specified tenant.
   *
   * @param tenantRecord the tenant record containing the tenant key and the entity to add
   */
  void addEntity(final TenantRecord tenantRecord);

  /**
   * Removes a specific entity from the given tenant.
   *
   * @param tenantRecord the tenant record containing the tenant id and the entity to add
   */
  void removeEntity(final TenantRecord tenantRecord);

  /**
   * Deletes a tenant and all associated data from the state.
   *
   * @param tenantRecord the tenant record representing the tenant to delete
   */
  void delete(final TenantRecord tenantRecord);
}
